package com.crypto_trader.api_server.domain.entities;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Entity(name = "Users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String username;
    @Embedded
    private Account account;

    // 보유한 크립토
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CryptoAsset> assets = new ArrayList<>();

    public UserEntity() {}

    public UserEntity(String username) {
        this.username = username;
        this.account = initAccount();
    }

    public CryptoAsset findCryptoAssetByMarket(String market) {
        return assets.stream()
                .filter(c -> c.getMarket().equals(market))
                .findFirst()
                .orElse(null);
    }

    // private

    private Account initAccount() {
        return new Account("유저의 계좌번호");
    }
}
