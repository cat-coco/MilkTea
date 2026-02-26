package com.milktea.agent.service;

import com.milktea.agent.model.Order;
import com.milktea.agent.model.OrderItem;
import com.milktea.agent.model.OrderStatus;
import com.milktea.agent.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public Order createOrder(String customerName, String phone, List<OrderItem> items, String remark) {
        Order order = new Order();
        order.setOrderId(orderRepository.generateOrderId());
        order.setCustomerName(customerName);
        order.setPhone(phone);
        order.setItems(items);
        order.setRemark(remark);
        double total = items.stream()
                .mapToDouble(item -> item.getUnitPrice() * item.getQuantity())
                .sum();
        order.setTotalPrice(total);
        order.setStatus(OrderStatus.PENDING);
        return orderRepository.save(order);
    }

    public Optional<Order> getOrder(String orderId) {
        return orderRepository.findById(orderId);
    }

    public List<Order> getOrdersByPhone(String phone) {
        return orderRepository.findByPhone(phone);
    }

    public List<Order> getOrdersByCustomerName(String customerName) {
        return orderRepository.findByCustomerName(customerName);
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public String cancelOrder(String orderId, String reason) {
        Optional<Order> opt = orderRepository.findById(orderId);
        if (opt.isEmpty()) {
            return "订单不存在，订单号: " + orderId;
        }
        Order order = opt.get();
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.REFUNDED) {
            return "订单已经是" + order.getStatus().getDescription() + "状态，无需重复操作";
        }
        if (order.getStatus() == OrderStatus.COMPLETED) {
            order.setStatus(OrderStatus.REFUNDED);
            order.setUpdateTime(LocalDateTime.now());
            order.setRemark(order.getRemark() != null ? order.getRemark() + " | 退单原因: " + reason : "退单原因: " + reason);
            orderRepository.save(order);
            return String.format("订单 %s 已退单成功，退款金额: %.2f元，退单原因: %s", orderId, order.getTotalPrice(), reason);
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdateTime(LocalDateTime.now());
        order.setRemark(order.getRemark() != null ? order.getRemark() + " | 取消原因: " + reason : "取消原因: " + reason);
        orderRepository.save(order);
        return String.format("订单 %s 已取消成功，取消原因: %s", orderId, reason);
    }
}
