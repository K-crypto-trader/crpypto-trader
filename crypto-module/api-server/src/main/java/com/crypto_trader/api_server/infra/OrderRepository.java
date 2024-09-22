package com.crypto_trader.api_server.infra;

import com.crypto_trader.api_server.domain.entities.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserId(Long id);

    @Query("SELECT o FROM Orders o " +
            "JOIN FETCH o.user u " +
            "LEFT JOIN FETCH u.assets a " + // left가 있어야 user의 assets가 없어도 user가 조회 됨
            "WHERE o.market = :market")
    List<Order> findByMarket(@Param("market") String market);
}
