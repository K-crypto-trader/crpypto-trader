package com.crypto_trader.scheduler.application;

import com.crypto_trader.scheduler.domain.Ticker;
import com.crypto_trader.scheduler.domain.entity.Candle;
import com.crypto_trader.scheduler.infra.CandleMongoRepository;
import com.crypto_trader.scheduler.infra.SimpleCandleRepository;
import com.crypto_trader.scheduler.infra.SimpleMarketRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.crypto_trader.scheduler.domain.CandleUnit.ONEMINUTE;
import static com.crypto_trader.scheduler.global.constant.RedisConst.*;
import static com.crypto_trader.scheduler.proto.DataModel.*;

@Slf4j
@Service
public class CandleService {

    private final SimpleCandleRepository candleRepository;
    private final SimpleMarketRepository simpleMarketRepository;
    private final CandleMongoRepository candleMongoRepository;
    private final MarketService marketService;

    private final ObjectMapper objectMapper;
    private final ReactiveRedisTemplate<String, String> stringRedisTemplate;
    private final ReactiveRedisTemplate<String, byte[]> byteArrayRedisTemplate;
    private final TickerService tickerService;

    public CandleService(SimpleCandleRepository candleRepository,
                         SimpleMarketRepository simpleMarketRepository,
                         CandleMongoRepository candleMongoRepository,
                         MarketService marketService,
                         ObjectMapper objectMapper,
                         ReactiveRedisTemplate<String, String> stringRedisTemplate,
                         ReactiveRedisTemplate<String, byte[]> byteArrayRedisTemplate, TickerService tickerService) {
        this.candleRepository = candleRepository;
        this.simpleMarketRepository = simpleMarketRepository;
        this.candleMongoRepository = candleMongoRepository;
        this.marketService = marketService;
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.byteArrayRedisTemplate = byteArrayRedisTemplate;
        this.tickerService = tickerService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        marketService.renewalMarkets();

        // cleanRedisDB가 완료된 후에만 initRedisCandleFromMongo 실행
        cleanRedisDB()
                .then(Mono.fromRunnable(this::initRedisCandleFromMongo))  // DB 클리어 후에 Mongo 데이터를 Redis에 초기화
                .subscribe(success -> log.debug("Redis initialized with Mongo data."),
                        error -> log.debug("Error initializing Redis: {}", error.getMessage()));

        // Redis Ticker 구독 설정
        stringRedisTemplate
                .listenToChannel(REDIS_TICKER)
                .subscribe((value) -> {
                    String message = value.getMessage();
                    try {
                        Ticker ticker = objectMapper.readValue(message, Ticker.class);
                        candleRepository.update(ticker.getMarket(), ticker.getTradePrice(), ticker.getAccTradeVolume());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    // MongoDB에서 모든 데이터 가져오기
    private void initRedisCandleFromMongo() {
        log.debug("Starting initRedisCandleFromMongo...");

        List<String> markets = marketService.getAllMarketCodes().subList(0, 3);
        List<Candle> candles = new ArrayList<>();

        for (String market : markets) {
            log.debug("Fetching candles for market: {}", market);
            List<Candle> candlesByMarket = candleMongoRepository.findCandlesByMarket(market);
            candles.addAll(candlesByMarket);
        }

        // 각 캔들 데이터를 Redis에 저장
        candles.forEach(candle -> {
            String key = ONEMINUTE + ":minute_candle:" + candle.getMarket();
            try {
                PCandle pCandle = new PCandle.Builder()
                        .setOpen(candle.getOpen())
                        .setClose(candle.getClose())
                        .setHigh(candle.getHigh())
                        .setLow(candle.getLow())
                        .setVolume(candle.getVolume())
                        .setTime(candle.getTime().toString())
                        .build();

                byteArrayRedisTemplate.opsForList()
                        .rightPush(key, pCandle.toByteArray())  // Redis 리스트에 저장
                        .doOnSuccess(result -> log.debug("Successfully pushed to Redis: {}", key))
                        .subscribe();
            } catch (Exception e) {
                log.debug("Error serializing candle data: {}", e.getMessage());
            }
        });

        tickerService.fetchStart();
    }

    // Redis 데이터베이스에서 "market" 키를 제외한 모든 데이터를 삭제
    private Mono<Void> cleanRedisDB() {
        return stringRedisTemplate.keys("*")
                .filter(key -> !key.equals(MARKET))
                .flatMap(stringRedisTemplate::delete)
                .then(Mono.just("Redis cache cleared except 'market' key on startup."))
                .doOnSuccess(log::debug)
                .then();
    }

}
