package com.crypto_trader.api_server.infra;

import com.crypto_trader.api_server.domain.Ticker;
import com.crypto_trader.api_server.domain.events.TickerChangeEvent;
import jakarta.annotation.PostConstruct;
import jakarta.ws.rs.core.Application;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.crypto_trader.api_server.global.constant.Constants.TICKER;

@Slf4j
@Repository
public class TickerRepository {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ApplicationEventPublisher publisher;
    private final SimpleMarketRepository simpleMarketRepository;

    private final Map<String, Ticker> tickers = new ConcurrentHashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        initTickers();
    }

    public TickerRepository(ReactiveRedisTemplate<String, String> redisTemplate,
                            ApplicationEventPublisher publisher,
                            SimpleMarketRepository simpleMarketRepository) {
        this.redisTemplate = redisTemplate;
        this.publisher = publisher;
        this.simpleMarketRepository = simpleMarketRepository;
    }

    public void save(Ticker ticker) {
        tickers.put(ticker.getMarket(), ticker);
        publisher.publishEvent(new TickerChangeEvent(this, ticker));
    }

    public Ticker findTickerByMarket(String marketCode) {
        return tickers.get(marketCode);
    }

    public List<Ticker> findAllTickers() {
        return new ArrayList<>(tickers.values());
    }

    public Flux<? extends ReactiveSubscription.Message<String, String>> getChannel() {
        return redisTemplate
                .listenToChannel(TICKER);
    }

    private void initTickers() {
        simpleMarketRepository.marketCodesUpdates()
                .doOnNext(marketCodes -> {
                    for (String marketCode : marketCodes) {
                        Ticker ticker = new Ticker(marketCode, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
                        tickers.put(marketCode, ticker);
                    }
                })
                .subscribe();
    }
}
