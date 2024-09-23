package com.crypto_trader.api_server.presentation;

import com.crypto_trader.api_server.application.CryptoService;
import com.crypto_trader.api_server.application.dto.CryptoDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class CryptoController {

    private final CryptoService cryptoService;

    public CryptoController(CryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    @PutMapping("/api/crypto-entity")
    public void getAllCryptos() {
        cryptoService.updateCrypto();
    }
}
