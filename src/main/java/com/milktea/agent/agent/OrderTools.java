package com.milktea.agent.agent;

import com.milktea.agent.model.Order;
import com.milktea.agent.model.OrderItem;
import com.milktea.agent.service.OrderService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Order-related tools for the ReactAgent, using @Tool annotation
 * for automatic discovery by spring-ai-alibaba-agent-framework.
 */
@Service
public class OrderTools {

    private final OrderService orderService;

    public OrderTools(OrderService orderService) {
        this.orderService = orderService;
    }

    @Tool(name = "createOrder", description = "创建奶茶订单-当客户确认要下单时调用。必须先收集客户姓名和手机号，否则无法创建订单。")
    public String createOrder(
            @ToolParam(description = "客户姓名，必填，不能为空") String customerName,
            @ToolParam(description = "客户手机号，必填，不能为空") String phone,
            @ToolParam(description = "饮品名称") String productName,
            @ToolParam(description = "杯型：小杯/中杯/大杯") String size,
            @ToolParam(description = "甜度：无糖/少糖/半糖/正常糖/多糖") String sweetness,
            @ToolParam(description = "冰度：去冰/少冰/正常冰/多冰/热饮") String ice,
            @ToolParam(description = "加料：珍珠/椰果/仙草/布丁/芋圆，可为空") String topping,
            @ToolParam(description = "数量") int quantity,
            @ToolParam(description = "单价（元）") double unitPrice,
            @ToolParam(description = "备注，可为空") String remark) {

        // Validate required fields
        if (customerName == null || customerName.isBlank()) {
            return "下单失败：缺少客户姓名，请先询问客户姓名后再下单。";
        }
        if (phone == null || phone.isBlank()) {
            return "下单失败：缺少客户手机号，请先询问客户手机号后再下单。";
        }
        if (productName == null || productName.isBlank()) {
            return "下单失败：缺少饮品名称，请先确认客户要点什么饮品。";
        }

        OrderItem item = new OrderItem(productName, size, sweetness, ice, topping, quantity, unitPrice);
        Order order = orderService.createOrder(customerName, phone, List.of(item), remark);
        return String.format("下单成功！订单号: %s，总价: %.2f元。请记好您的订单号哦~",
                order.getOrderId(), order.getTotalPrice());
    }

    @Tool(name = "cancelOrder", description = "取消或退奶茶订单-当客户要取消或退订单时调用")
    public String cancelOrder(
            @ToolParam(description = "要取消的订单号") String orderId,
            @ToolParam(description = "取消/退单原因") String reason) {
        return orderService.cancelOrder(orderId, reason);
    }

    @Tool(name = "queryOrder", description = "查询奶茶订单-当客户要查询订单状态时调用，可通过订单号、手机号或姓名查询")
    public String queryOrder(
            @ToolParam(description = "订单号(可选)") String orderId,
            @ToolParam(description = "客户手机号(可选)") String phone,
            @ToolParam(description = "客户姓名(可选)") String customerName) {

        if (orderId != null && !orderId.isBlank()) {
            Optional<Order> order = orderService.getOrder(orderId);
            return order.map(Order::toString).orElse("未找到订单号为 " + orderId + " 的订单");
        }
        if (phone != null && !phone.isBlank()) {
            List<Order> orders = orderService.getOrdersByPhone(phone);
            if (orders.isEmpty()) return "未找到手机号 " + phone + " 的相关订单";
            return orders.stream().map(Order::toString).collect(Collectors.joining("\n---\n"));
        }
        if (customerName != null && !customerName.isBlank()) {
            List<Order> orders = orderService.getOrdersByCustomerName(customerName);
            if (orders.isEmpty()) return "未找到客户 " + customerName + " 的相关订单";
            return orders.stream().map(Order::toString).collect(Collectors.joining("\n---\n"));
        }
        return "请提供订单号、手机号或客户姓名来查询订单";
    }
}
