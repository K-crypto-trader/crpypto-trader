package com.crypto_trader.api_server.application;

import com.crypto_trader.api_server.domain.OrderSide;
import com.crypto_trader.api_server.domain.Ticker;
import com.crypto_trader.api_server.domain.entities.Order;
import com.crypto_trader.api_server.domain.entities.OrderState;
import com.crypto_trader.api_server.domain.events.TickerProcessingEvent;
import com.crypto_trader.api_server.infra.OrderRepository;
import com.crypto_trader.api_server.infra.SimpleMarketRepository;
import com.crypto_trader.api_server.infra.TickerRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.LockModeType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class OrderExecutionService {

    private final OrderService orderService;
    private final TickerRepository tickerRepository;
    private final SimpleMarketRepository marketRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    private final Map<String, Sinks.Many<Ticker>> sinkMap = new HashMap<>();
    private final Map<String, Disposable> subscriptionMap = new HashMap<>();

    @Autowired
    public OrderExecutionService(OrderService orderService,
                                 TickerRepository tickerRepository,
                                 SimpleMarketRepository marketRepository,
                                 ObjectMapper objectMapper,
                                 ApplicationEventPublisher eventPublisher) {
        this.orderService = orderService;
        this.tickerRepository = tickerRepository;
        this.marketRepository = marketRepository;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    @PostConstruct
    public void init() {
        // 시장 코드 업데이트에 따른 구독 설정
        marketRepository.marketCodesUpdates().subscribe(this::updateMarketSubscriptions);

        // Ticker 채널 구독 설정
        tickerRepository.getChannel().subscribe(value -> handleTickerMessage(value.getMessage()));
    }

    /**
     * 시장 코드 업데이트에 따라 구독과 Sink를 재설정
     */
    private void updateMarketSubscriptions(List<String> codes) {
        clearSubscriptionsAndSinks();

        for (String code : codes) {
            Sinks.Many<Ticker> sink = Sinks.many().unicast().onBackpressureBuffer();
            sinkMap.put(code, sink);

            Disposable subscription = sink.asFlux()
                    .sampleFirst(Duration.ofSeconds(1))  // 1초에 한 번 처리
                    .subscribe(ticker -> eventPublisher.publishEvent(new TickerProcessingEvent(this, ticker)));

            subscriptionMap.put(code, subscription);
        }
    }

    /**
     * 기존의 구독과 Sink를 정리
     */
    private void clearSubscriptionsAndSinks() {
        subscriptionMap.values().forEach(Disposable::dispose);
        subscriptionMap.clear();
        sinkMap.clear();
    }

    /**
     * Redis에서 받은 Ticker 메시지를 처리하여 적절한 Sink에 발행
     */
    private void handleTickerMessage(String message) {
        try {
            Ticker ticker = objectMapper.readValue(message, Ticker.class);
            tickerRepository.save(ticker);

            Sinks.Many<Ticker> sink = sinkMap.get(ticker.getMarket());
            if (sink != null) {
                sink.tryEmitNext(ticker).orThrow();
            } else {
                throw new RuntimeException("Sink not found for market: " + ticker.getMarket());
            }

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to process Ticker message", e);
        }
    }

    /**
     * TickerProcessingEvent 를 처리하여 주문 실행
     */
    @EventListener
    public void processTicker(TickerProcessingEvent event) {
        Ticker ticker = event.getTicker();
        try{
            orderService.processOrdersInParallel(ticker.getMarket(), ticker.getTradePrice());
        }catch (Exception e) {
            throw new RuntimeException("Exception occurred while processing orders", e.getCause());
        }
    }
}