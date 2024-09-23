package com.crypto_trader.api_server.infra;

import com.crypto_trader.api_server.domain.entities.Crypto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;

public interface CryptoJpaRepository extends JpaRepository<Crypto, Long> {

}
