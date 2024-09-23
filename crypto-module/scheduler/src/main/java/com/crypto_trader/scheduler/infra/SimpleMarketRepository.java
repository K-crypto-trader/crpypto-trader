package com.crypto_trader.scheduler.infra;

import com.crypto_trader.scheduler.domain.Market;
import com.crypto_trader.scheduler.domain.event.MarketsUpdateEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

import static com.crypto_trader.scheduler.global.constant.RedisConst.MARKET;

@Slf4j
@Repository
public class SimpleMarketRepository {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ApplicationEventPublisher publisher;
    private final ObjectMapper objectMapper;

    private final Map<String, Market> markets = new HashMap<>();

    @Autowired
    public SimpleMarketRepository(ReactiveRedisTemplate<String, String> redisTemplate,
                                  ApplicationEventPublisher publisher,
                                  ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
    }

    public synchronized void saveMarkets(List<Market> newMarketList) {
        // market 변경 신호 to redis
        Map<String, Market> newMarkets = newMarketList.stream()
                .collect(Collectors.toMap(Market::getMarket, market -> market));

        boolean isModified = !markets.keySet().equals(newMarkets.keySet()) || !markets.equals(newMarkets) || markets.isEmpty();

        markets.keySet().retainAll(newMarkets.keySet());
        markets.putAll(newMarkets);

        if (isModified) {
            log.debug("Renewal Market");
            try {
                redisTemplate.opsForValue()
                        .set(MARKET, objectMapper.writeValueAsString(markets.keySet()))
                        .block();
                publisher.publishEvent(new MarketsUpdateEvent(this));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public List<Market> getAllMarkets() {
        return new ArrayList<>(markets.values());
    }

    public List<String> getAllMarketCodes() {
        return new ArrayList<>(markets.keySet());
    }
}
