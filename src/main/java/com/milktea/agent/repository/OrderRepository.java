package com.milktea.agent.repository;

import com.milktea.agent.model.Order;
import com.milktea.agent.model.OrderStatus;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
public class OrderRepository {

    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final AtomicLong idCounter = new AtomicLong(10000);

    public String generateOrderId() {
        return "MT" + idCounter.incrementAndGet();
    }

    public Order save(Order order) {
        orders.put(order.getOrderId(), order);
        return order;
    }

    public Optional<Order> findById(String orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }

    public List<Order> findByPhone(String phone) {
        return orders.values().stream()
                .filter(o -> phone.equals(o.getPhone()))
                .sorted(Comparator.comparing(Order::getCreateTime).reversed())
                .collect(Collectors.toList());
    }

    public List<Order> findByCustomerName(String customerName) {
        return orders.values().stream()
                .filter(o -> customerName.equals(o.getCustomerName()))
                .sorted(Comparator.comparing(Order::getCreateTime).reversed())
                .collect(Collectors.toList());
    }

    public List<Order> findByStatus(OrderStatus status) {
        return orders.values().stream()
                .filter(o -> status.equals(o.getStatus()))
                .sorted(Comparator.comparing(Order::getCreateTime).reversed())
                .collect(Collectors.toList());
    }

    public List<Order> findAll() {
        return orders.values().stream()
                .sorted(Comparator.comparing(Order::getCreateTime).reversed())
                .collect(Collectors.toList());
    }

    public boolean deleteById(String orderId) {
        return orders.remove(orderId) != null;
    }
}
