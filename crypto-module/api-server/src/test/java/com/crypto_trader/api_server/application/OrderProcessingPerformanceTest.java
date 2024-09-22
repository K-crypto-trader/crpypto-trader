package com.crypto_trader.api_server.application;

import com.crypto_trader.api_server.domain.OrderSide;
import com.crypto_trader.api_server.domain.Ticker;
import com.crypto_trader.api_server.domain.entities.CryptoAsset;
import com.crypto_trader.api_server.domain.entities.Order;
import com.crypto_trader.api_server.domain.entities.OrderState;
import com.crypto_trader.api_server.domain.entities.UserEntity;
import com.crypto_trader.api_server.infra.OrderRepository;
import com.crypto_trader.api_server.infra.UserEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
public class OrderProcessingPerformanceTest {

    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private UserEntityRepository userEntityRepository;
    @Autowired
    private OrderService orderService;
    private UserEntity mockUser;
    private List<Order> mockOrders;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        // 가짜 UserEntity 생성
        mockUser = createMockUser();
        mockOrders = createMockOrders();
    }

    /**
     * 가짜 UserEntity 생성 함수
     */
    private UserEntity createMockUser() {
        // 가짜 CryptoAsset 생성
        CryptoAsset btcAsset = new CryptoAsset("BTC", 10000.0, 500.0);

        // 가짜 UserEntity 생성
        UserEntity user = new UserEntity("test_user");
        user.getAssets().add(btcAsset); // CryptoAsset 추가

        userEntityRepository.save(user);
        return user;
    }

    private List<Order> createMockOrders() {

        List<Order> mockOrders = new LinkedList<>();
        // 10,000개의 가짜 주문 생성
        for (int i = 0; i < 10_000; i++) {
            Order order = Order.builder()
                    .market("BTC")
                    .side(OrderSide.BID)
                    .volume(1)
                    .price(50000 + i)
                    .build();
            order.setUser(mockUser); // UserEntity와 연결
            order.setState(OrderState.CREATED);
            orderRepository.save(order);
            mockOrders.add(order);
        }

        return mockOrders;
    }

    @Test
    public void testBeforeProcessTicker() throws InterruptedException {

        List<Order> findRandomOrder = new ArrayList<>();

        // 가짜 Ticker 생성
        Ticker ticker = Ticker.builder()
                .market("BTC")
                .tradePrice(1000) // 거래 가격 1000
                .build();

        // 랜덤 조회 쓰레드 생성
        Thread randomQueryThread = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                // 랜덤으로 10개 주문 조회
                List<Long> randomOrderIds = getRandomOrderIds(mockOrders, 10);
                randomOrderIds.forEach(orderId -> {
                    Order queriedOrder = orderRepository.findById(orderId)
                            .orElseThrow(() -> new RuntimeException("Order not found"));
                    findRandomOrder.add(queriedOrder);
                });
            }
        });

        Thread processingThread = new Thread(() -> {
            orderService.beforeProcessTicker(ticker);
        });

        long startTime = System.currentTimeMillis();
        // beforeProcessTicker 호출
        processingThread.start();
        randomQueryThread.start();
        processingThread.join();
        randomQueryThread.join();
        // 처리 시간 계산
        long duration = System.currentTimeMillis() - startTime;  // 밀리초 단위
        System.out.println("걸린 시간: "+duration);

        List<Order> btc = orderRepository.findByMarket("BTC");
        btc.forEach(order -> {
            assertThat(order.getState()).isEqualTo(OrderState.COMPLETED);
        });

        assertThat(findRandomOrder.size()).isEqualTo(1000);
    }

    @Test
    public void testEnhancedProcessTicker() throws InterruptedException {

        List<Order> findRandomOrder = new ArrayList<>();

        Thread processingThread = new Thread(() -> {
            mockOrders.forEach(order -> {
                orderService.processOrderWithLock(order);  // 쓰기 Lock 사용
            });
        });

        // 랜덤 조회 쓰레드 생성
        Thread randomQueryThread = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                // 랜덤으로 10개 주문 조회
                List<Long> randomOrderIds = getRandomOrderIds(mockOrders, 10);
                randomOrderIds.forEach(orderId -> {
                    Order queriedOrder = orderRepository.findById(orderId)
                            .orElseThrow(() -> new RuntimeException("Order not found"));
                    findRandomOrder.add(queriedOrder);
                });
            }
        });

        long startTime = System.currentTimeMillis();
        // 처리 시간 계산
        processingThread.start();
        randomQueryThread.start();
        processingThread.join();
        randomQueryThread.join();
        long duration = System.currentTimeMillis() - startTime;  // 밀리초 단위
        System.out.println("걸린 시간: "+duration);

        List<Order> btc = orderRepository.findByMarket("BTC");
        btc.forEach(order -> {
            assertThat(order.getState()).isEqualTo(OrderState.COMPLETED);
        });

        assertThat(findRandomOrder.size()).isEqualTo(1000);
    }

    // 랜덤으로 Order 리스트에서 n개의 Order ID를 선택하는 메서드
    private List<Long> getRandomOrderIds(List<Order> orders, int n) {
        Random random = new Random();
        return random.ints(0, orders.size()) // 범위 내에서 랜덤 인덱스 생성
                .distinct() // 중복 제거
                .limit(n) // n개 선택
                .mapToObj(i -> orders.get(i).getId()) // Order의 ID만 추출
                .collect(Collectors.toList());
    }
}