package com.crypto_trader.api_server.domain.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;

@Getter
@Entity
public class Crypto {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String market;
    private String krName;
    private String enName;
    private String cryptoImgUrl;
}
