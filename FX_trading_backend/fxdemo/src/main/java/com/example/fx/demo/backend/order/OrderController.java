package com.example.fx.demo.backend.order;

import com.example.fx.demo.backend.order.dto.CreateOrderRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/account/{accountId}")
    public List<Order> findByAccountId(@PathVariable Long accountId) {
        return orderService.findByAccountId(accountId);
    }

    @PostMapping
    public Order create(@RequestBody CreateOrderRequest request) {
        return orderService.create(request);
    }
}
