package com.crypto_trader.api_server.application;

import com.crypto_trader.api_server.application.dto.CryptoDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CryptoService {

    private final SimpleMarketService simpleMarketService;

    public CryptoService(SimpleMarketService simpleMarketService) {
        this.simpleMarketService = simpleMarketService;
    }

    @Transactional
    public void updateCrypto() {
        List<CryptoDto> cryptoDtos = simpleMarketService.getAllCryptos();

    }
}
