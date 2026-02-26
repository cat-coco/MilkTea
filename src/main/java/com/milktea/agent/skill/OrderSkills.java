package com.milktea.agent.skill;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.milktea.agent.model.Order;
import com.milktea.agent.model.OrderItem;
import com.milktea.agent.service.OrderService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Configuration
public class OrderSkills {

    private final OrderService orderService;

    public OrderSkills(OrderService orderService) {
        this.orderService = orderService;
    }

    // ===== Create Order Skill =====

    public record CreateOrderRequest(
            @JsonProperty(required = true) @JsonPropertyDescription("客户姓名") String customerName,
            @JsonProperty(required = true) @JsonPropertyDescription("客户手机号") String phone,
            @JsonProperty(required = true) @JsonPropertyDescription("订单商品列表") List<OrderItemRequest> items,
            @JsonPropertyDescription("订单备注") String remark
    ) {}

    public record OrderItemRequest(
            @JsonProperty(required = true) @JsonPropertyDescription("饮品名称，如：经典珍珠奶茶、抹茶拿铁、杨枝甘露等") String productName,
            @JsonProperty(required = true) @JsonPropertyDescription("杯型：小杯/中杯/大杯") String size,
            @JsonProperty(required = true) @JsonPropertyDescription("甜度：无糖/少糖/半糖/正常糖/多糖") String sweetness,
            @JsonProperty(required = true) @JsonPropertyDescription("冰度：去冰/少冰/正常冰/多冰/热饮") String ice,
            @JsonPropertyDescription("加料：珍珠/椰果/仙草/布丁/芋圆，可为空") String topping,
            @JsonProperty(required = true) @JsonPropertyDescription("数量") int quantity,
            @JsonProperty(required = true) @JsonPropertyDescription("单价（元）") double unitPrice
    ) {}

    @Bean
    @Description("创建奶茶订单-当客户确认要下单时调用此函数")
    public Function<CreateOrderRequest, String> createOrder() {
        return request -> {
            List<OrderItem> items = request.items().stream().map(r -> {
                OrderItem item = new OrderItem();
                item.setProductName(r.productName());
                item.setSize(r.size());
                item.setSweetness(r.sweetness());
                item.setIce(r.ice());
                item.setTopping(r.topping());
                item.setQuantity(r.quantity());
                item.setUnitPrice(r.unitPrice());
                return item;
            }).collect(Collectors.toList());

            Order order = orderService.createOrder(
                    request.customerName(), request.phone(), items, request.remark());
            return String.format("下单成功！订单号: %s，总价: %.2f元。请记好您的订单号哦~",
                    order.getOrderId(), order.getTotalPrice());
        };
    }

    // ===== Cancel Order Skill =====

    public record CancelOrderRequest(
            @JsonProperty(required = true) @JsonPropertyDescription("要取消/退单的订单号") String orderId,
            @JsonProperty(required = true) @JsonPropertyDescription("取消/退单原因") String reason
    ) {}

    @Bean
    @Description("取消或退奶茶订单-当客户要取消或退订单时调用此函数")
    public Function<CancelOrderRequest, String> cancelOrder() {
        return request -> orderService.cancelOrder(request.orderId(), request.reason());
    }

    // ===== Query Order Skill =====

    public record QueryOrderRequest(
            @JsonPropertyDescription("订单号") String orderId,
            @JsonPropertyDescription("客户手机号") String phone,
            @JsonPropertyDescription("客户姓名") String customerName
    ) {}

    @Bean
    @Description("查询奶茶订单-当客户要查询订单状态时调用此函数，可通过订单号、手机号或姓名查询")
    public Function<QueryOrderRequest, String> queryOrder() {
        return request -> {
            // Query by order ID
            if (request.orderId() != null && !request.orderId().isBlank()) {
                Optional<Order> order = orderService.getOrder(request.orderId());
                return order.map(Order::toString).orElse("未找到订单号为 " + request.orderId() + " 的订单");
            }
            // Query by phone
            if (request.phone() != null && !request.phone().isBlank()) {
                List<Order> orders = orderService.getOrdersByPhone(request.phone());
                if (orders.isEmpty()) return "未找到手机号 " + request.phone() + " 的相关订单";
                return orders.stream().map(Order::toString).collect(Collectors.joining("\n---\n"));
            }
            // Query by name
            if (request.customerName() != null && !request.customerName().isBlank()) {
                List<Order> orders = orderService.getOrdersByCustomerName(request.customerName());
                if (orders.isEmpty()) return "未找到客户 " + request.customerName() + " 的相关订单";
                return orders.stream().map(Order::toString).collect(Collectors.joining("\n---\n"));
            }
            return "请提供订单号、手机号或客户姓名来查询订单";
        };
    }
}
