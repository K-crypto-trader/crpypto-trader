package com.crypto_trader.scheduler.application;

import com.crypto_trader.scheduler.domain.Market;
import com.crypto_trader.scheduler.infra.SimpleMarketRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.yaml.snakeyaml.error.Mark;

import java.util.List;

@Slf4j
@Service
public class MarketService {

    private final RestTemplate restTemplate;
    private final SimpleMarketRepository simpleMarketRepository;

    // static field
    public static final String MARKET_URL = "https://api.upbit.com/v1/market/all";

    @Autowired
    public MarketService(RestTemplate restTemplate, SimpleMarketRepository simpleMarketRepository) {
        this.restTemplate = restTemplate;
        this.simpleMarketRepository = simpleMarketRepository;
    }

    public void renewalMarkets() {
        ResponseEntity<List<Market>> responseEntity = restTemplate.exchange(
                MARKET_URL,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {
                }
        );

        List<Market> newMarketList = responseEntity.getBody();
        assert newMarketList != null;

        log.debug("Renewal Market Start {}", newMarketList.size());

        simpleMarketRepository.saveMarkets(newMarketList);
    }

    public List<String> getAllMarketCodes() {
        return simpleMarketRepository.getAllMarketCodes();
    }
}
