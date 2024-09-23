package com.crypto_trader.api_server.application;

import com.crypto_trader.api_server.application.dto.OrderCancelRequestDto;
import com.crypto_trader.api_server.auth.PrincipalUser;
import com.crypto_trader.api_server.domain.OrderSide;
import com.crypto_trader.api_server.domain.Ticker;
import com.crypto_trader.api_server.domain.entities.CryptoAsset;
import com.crypto_trader.api_server.domain.entities.Order;
import com.crypto_trader.api_server.domain.entities.OrderState;
import com.crypto_trader.api_server.domain.entities.UserEntity;
import com.crypto_trader.api_server.application.dto.OrderCreateRequestDto;
import com.crypto_trader.api_server.application.dto.OrderResponseDto;
import com.crypto_trader.api_server.domain.events.TickerProcessingEvent;
import com.crypto_trader.api_server.global.utils.ListUtils;
import com.crypto_trader.api_server.infra.OrderRepository;
import jakarta.annotation.PreDestroy;
import jakarta.persistence.LockModeType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ListUtils listUtils;

    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    private final int ORDER_BATCH_SIZE = 1000;

    public OrderService(OrderRepository orderRepository,
                        ListUtils listUtils) {
        this.orderRepository = orderRepository;
        this.listUtils = listUtils;
    }

    @PreDestroy
    public void shutdownExecutor() {
        executorService.shutdown();
    }

    @Transactional
    public OrderResponseDto createOrder(PrincipalUser principalUser, OrderCreateRequestDto orderCreateRequestDto) {
        UserEntity user = principalUser.getUser();

        Order order = orderCreateRequestDto.toEntity();
        order.validationWith(user);

        user.getAccount().lock(order.totalPrice());
        orderRepository.save(order);

        return OrderResponseDto.toDto(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponseDto> getAllOrders(PrincipalUser principalUser) {
        UserEntity user = principalUser.getUser();

        return orderRepository.findByUserId(user.getId())
                .stream()
                .map(OrderResponseDto::toDto)
                .toList();
    }

    @Transactional
    public OrderResponseDto cancelOrder(PrincipalUser principalUser, OrderCancelRequestDto dto) {
        UserEntity user = principalUser.getUser();

        Order order = orderRepository.findByIdWithLock(dto.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        if (!Objects.equals(order.getUser().getId(), user.getId()))
            throw new IllegalStateException("User id does not match");

        if (order.getState() != OrderState.CREATED)
            throw new IllegalStateException("Order can't be cancelled");

        order.cancel(dto.getMarket());

        // remove ??
        // orderRepository.delete(order);

        return new OrderResponseDto();
    }

    @Transactional(readOnly = true)
    public List<Order> getOrderToProcess(String market, double tradePrice) {
        return orderRepository.findByMarket(market).stream()
                .filter(order -> {
                    double price = order.getPrice().doubleValue();
                    return order.getState() == OrderState.CREATED &&
                            ((order.getSide() == OrderSide.BID) ? tradePrice <= price : tradePrice >= price);
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void processOrdersInParallel(String market, double tradePrice) throws Exception {
        List<Order> ordersToProcess = getOrderToProcess(market, tradePrice);
        List<List<Order>> partitionedOrders = listUtils.partitionList(ordersToProcess, ORDER_BATCH_SIZE);

        List<Future<Void>> futures = new ArrayList<>();
        for (List<Order> orderBatch : partitionedOrders) {
            futures.add(executorService.submit(() -> {
                processOrderBatchWithLock(orderBatch);
                return null;
            }));
        }

        for (Future<Void> future : futures) {
            future.get();
        }

        if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
            log.warn("Some tasks didn't finish within 60 seconds");
        }
    }

    // 비관적 락을 걸고 주문 처리하는 메서드
    @Transactional
    public void processOrderBatchWithLock(List<Order> orderBatch) {
        List<Long> orderIds = orderBatch.stream().map(Order::getId).collect(Collectors.toList());
        List<Order> lockedOrders = orderRepository.findOrdersWithLock(orderIds);

        for (Order order : lockedOrders) {
            order.execution();
            orderRepository.saveAndFlush(order);
        }
    }

    /*
     * 성능 TEST 용 함수
     * */
    @Transactional
    public void beforeProcessTicker(Ticker ticker) {
        String market = ticker.getMarket();
        double tradePrice = ticker.getTradePrice();

        orderRepository.findByMarket(market).stream()
                .filter(order -> {
                    double price = order.getPrice().doubleValue();
                    return order.getState() == OrderState.CREATED &&
                            (order.getSide() == OrderSide.BID) ? tradePrice <= price : tradePrice >= price;
                })
                .forEach(Order::execution);
    }
}
