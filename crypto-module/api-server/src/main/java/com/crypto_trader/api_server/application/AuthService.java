package com.crypto_trader.api_server.application;

import com.crypto_trader.api_server.auth.JwtUtil;
import com.crypto_trader.api_server.application.dto.UserInfoDto;
import com.crypto_trader.api_server.domain.entities.CryptoAsset;
import com.crypto_trader.api_server.domain.entities.UserEntity;
import com.crypto_trader.api_server.infra.UserEntityRepository;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.TransactionScoped;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class AuthService {

    private final UserEntityRepository userEntityRepository;
    private final JwtUtil jwtUtil;

    public AuthService(UserEntityRepository userEntityRepository, JwtUtil jwtUtil) {
        this.userEntityRepository = userEntityRepository;
        this.jwtUtil = jwtUtil;
    }

    @PostConstruct
    public void init() {
        UserEntity user = new UserEntity("user1");
        // TODO: init anonymous user
        // temp user
        CryptoAsset btc = new CryptoAsset("KRW-BTC", 10, 70000000);
        CryptoAsset xrp = new CryptoAsset("KRW-XRP", 1000, 6000);

        btc.setUser(user);
        xrp.setUser(user);

        userEntityRepository.save(user);
    }

    @Transactional
    public String login(String username) {
        userEntityRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(username));

        return jwtUtil.generateToken(username);
    }

    @Transactional
    public void signup(String username) {
        userEntityRepository.findByUsername(username)
                .ifPresent(u -> {
                    throw new RuntimeException("username already exists");
                });

        UserEntity user = new UserEntity(username);
        userEntityRepository.save(user);
    }

    @Transactional
    public UserInfoDto findByUsername(String username) {
        UserEntity userEntity = userEntityRepository.findByUsername(username)
                .orElseThrow();
        return UserInfoDto.from(userEntity);
    }
}
