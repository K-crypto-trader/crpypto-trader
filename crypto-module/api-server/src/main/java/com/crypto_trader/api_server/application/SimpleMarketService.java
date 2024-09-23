package com.crypto_trader.api_server.application;

import com.crypto_trader.api_server.application.dto.CryptoDto;
import com.crypto_trader.api_server.infra.SimpleMarketRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SimpleMarketService {

    private final SimpleMarketRepository marketRepository;

    public SimpleMarketService(SimpleMarketRepository marketRepository) {
        this.marketRepository = marketRepository;
    }

    public List<CryptoDto> getAllCryptos() {
        List<String> marketCodes = marketRepository.marketCodesUpdates()
                .blockFirst();

        if (marketCodes == null || marketCodes.isEmpty()) {
            return new ArrayList<>();
        }

        return marketCodes
                .stream()
                .map(CryptoDto::new)
                .toList();
    }
}
