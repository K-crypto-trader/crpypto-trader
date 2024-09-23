package com.crypto_trader.api_server.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.crypto_trader.api_server.global.constant.Constants.MARKET;

@Component
public class CryptoRepository {

    private final SimpleMarketRepository marketRepository;
    private final CryptoJpaRepository cryptoJpaRepository;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public CryptoRepository(SimpleMarketRepository marketRepository,
                            CryptoJpaRepository cryptoJpaRepository,
                            ReactiveRedisTemplate<String, String> redisTemplate,
                            ObjectMapper objectMapper) {
        this.marketRepository = marketRepository;
        this.cryptoJpaRepository = cryptoJpaRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public List<String> updateMarkets() {
        return null;
    }

    public List<String> getMarkets() {
        return null;
    }
}
