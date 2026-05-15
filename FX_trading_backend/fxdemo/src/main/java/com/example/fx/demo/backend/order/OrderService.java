package com.example.fx.demo.backend.order;

import com.example.fx.demo.backend.market.Rate;
import com.example.fx.demo.backend.market.RateService;
import com.example.fx.demo.backend.order.dto.CreateOrderRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final RateService rateService;

    public OrderService(OrderRepository orderRepository, RateService rateService) {
        this.orderRepository = orderRepository;
        this.rateService = rateService;
    }

    public List<Order> findByAccountId(Long accountId) {
        return orderRepository.findByAccountId(accountId);
    }

    @Transactional
    public Order create(CreateOrderRequest request) {
        Order order = new Order(
                request.accountId(),
                request.currencyPair(),
                request.side(),
                request.type(),
                request.quantity(),
                request.price()
        );

        // 成行注文はデモ用レートですぐ約定した扱いにする。
        if (request.type().name().equals("MARKET")) {
            Rate rate = rateService.getCurrentRate(request.currencyPair());
            BigDecimal executedPrice = request.side().name().equals("BUY") ? rate.ask() : rate.bid();
            order.markExecuted(executedPrice);
        }

        return orderRepository.save(order);
    }
}
