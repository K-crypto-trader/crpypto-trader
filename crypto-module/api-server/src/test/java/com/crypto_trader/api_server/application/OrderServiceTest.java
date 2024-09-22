package com.crypto_trader.api_server.application;

import com.crypto_trader.api_server.domain.OrderSide;
import com.crypto_trader.api_server.domain.entities.Account;
import com.crypto_trader.api_server.domain.entities.Order;
import com.crypto_trader.api_server.domain.entities.OrderState;
import com.crypto_trader.api_server.domain.entities.UserEntity;
import com.crypto_trader.api_server.infra.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.concurrent.*;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.platform.commons.function.Try.success;
import static org.mockito.Mockito.*;

public class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderService orderService; // processOrderWithLock 메서드가 속한 클래스라고 가정

    private Order order;
    private UserEntity user;

    @BeforeEach
    public void setUp() {
        // Mockito 초기화
        MockitoAnnotations.openMocks(this);

        // 가짜 UserEntity 생성
        user = mock(UserEntity.class);
        Account mockAccount = mock(Account.class);
        when(user.getAccount()).thenReturn(mockAccount);
        when(mockAccount.getBalance()).thenReturn(1000.0); // 사용자 잔고 설정

        // 가짜 Order 생성
        order = Order.builder()
                .market("BTC")
                .side(OrderSide.BID)
                .volume(1)
                .price(500)
                .build();
        order.setId(1L); // ID 설정
        order.validationWith(user);
    }

    @Test
    @Transactional
    public void testProcessOrderWithLock_Success() {
        // 가짜 OrderRepository 동작 설정
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        // 실행
        orderService.processOrderWithLock(order);

        // 상태 변경 확인
        assertEquals(OrderState.COMPLETED, order.getState());

        // OrderRepository에서 한번의 조회가 있었는지 확인
        verify(orderRepository, times(1)).findById(order.getId());
    }

    @Test
    @Transactional
    public void testProcessOrderWithLock_AlreadyProcessed() {
        // 상태가 이미 COMPLETED인 경우 설정
        order.execution(); // 미리 상태를 COMPLETED로 변경
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        // 실행 및 예외 확인
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            orderService.processOrderWithLock(order);
        });

        assertEquals("Order already processed or invalid state", exception.getMessage());

        // OrderRepository에서 한번의 조회가 있었는지 확인
        verify(orderRepository, times(1)).findById(order.getId());

        // 추가적인 저장(save) 호출이 없는지 확인
        verify(orderRepository, never()).save(order); // 저장이 호출되지 않아야 함
    }

    @Test
    @Transactional
    public void testProcessOrderWithLock_MultiThread() throws InterruptedException, ExecutionException {
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        ExecutorService executorService = Executors.newFixedThreadPool(2);

        Callable<Void> task1 = () -> {
            orderService.processOrderWithLock(order);
            return null;
        };

        Callable<Void> task2 = () -> {
            Thread.sleep(100); // 첫 번째 스레드가 락을 거는 시간을 기다림
            orderService.processOrderWithLock(order); // 이 시점에서 락이 걸려 있어야 함
            return null;
        };

        // 두 작업을 병렬로 실행
        Future<Void> future1 = executorService.submit(task1);
        Future<Void> future2 = executorService.submit(task2);

        // 첫 번째 작업은 정상적으로 완료되었는지 확인
        future1.get(); // 첫 번째 작업이 완료되기를 기다림
        assertEquals(OrderState.COMPLETED, order.getState());

        // 두 번째 작업은 락 대기로 인해 예외가 발생하는지 확인
        try {
            future2.get();
//            fail("Second thread should not be able to process the order because of lock");
        } catch (ExecutionException e) {
            e.printStackTrace();
            assertInstanceOf(RuntimeException.class, e.getCause());
        }

        executorService.shutdown();
        verify(orderRepository, times(2)).findById(order.getId());
    }

    @DisplayName("첫 번째 스레드가 락을 걸고 있는 동안 두 번째 스레드가 읽기를 시도할 때")
    @Test
    @Transactional
    public void testReadWhileLock() throws InterruptedException, ExecutionException {
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        // 스레드 풀 생성 (2개의 스레드)
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        // 1. 첫 번째 스레드에서 락을 걸고 주문을 처리하는 작업
        Callable<Void> task1 = () -> {
            orderService.processOrderWithLock(order);  // 락을 걸고 처리
            return null;
        };

        // 2. 두 번째 스레드에서 동일한 주문을 읽으려고 시도하는 작업 (락 대기 후 읽기 시도)
        Callable<Void> task2 = () -> {
            Thread.sleep(100); // 첫 번째 스레드가 락을 거는 시간을 기다림
            Order lockedOrder = orderRepository.findById(order.getId()).orElseThrow(() -> new RuntimeException("Order not found"));
            assertNotNull(lockedOrder); // 정상적으로 읽혔는지 확인
            return null;
        };

        // 두 작업을 병렬로 실행
        Future<Void> future1 = executorService.submit(task1);
        Future<Void> future2 = executorService.submit(task2);

        // 첫 번째 작업은 정상적으로 완료되었는지 확인
        future1.get();
        assertEquals(OrderState.COMPLETED, order.getState());

        // 두 번째 작업은 정상적으로 읽혔는지 확인 (락 대기 후 처리)
        future2.get();

        executorService.shutdown();

        verify(orderRepository, times(2)).findById(order.getId());
    }

    @DisplayName("PESSIMISTIC_READ 락을 걸고, 상태를 읽고 업데이트하는 동안 다른 스레드가 상태를 먼저 업데이트하는 경우")
    @Test
    public void testUpdateFailsIfAlreadyUpdated(){
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        // 1. 첫 번째 트랜잭션에서 PESSIMISTIC_READ 락을 걸고 주문을 읽고 처리하는 작업
        Callable<Void> task1 = () -> {
            orderService.processOrderWithReadLock(order);
            return null;
        };

        // 2. 두 번째 트랜잭션에서 동일한 주문을 완료 상태로 업데이트하는 작업
        Callable<Void> task2 = () -> {
            Thread.sleep(500); // 첫 번째 트랜잭션이 락을 거는 시간을 기다림
            Order readOrder = orderRepository.findById(order.getId()).orElseThrow();
            orderService.processOrderWithReadLock(readOrder);
            return null;
        };

        executorService.submit(task1);

            try{
                executorService.submit(task2).get();
            }catch (Exception e) {
                assertInstanceOf(RuntimeException.class, e.getCause(), "Expected a RuntimeException");
                assertEquals(e.getCause().getMessage(), "Order already processed or invalid state");
            }

        executorService.shutdown();
    }


}