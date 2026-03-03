package com.milktea.agent.react;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.milktea.agent.model.OrderItem;
import com.milktea.agent.service.OrderService;
import com.milktea.agent.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Executes tool calls from the ReAct agent by dispatching to the appropriate service methods.
 */
@Component
public class ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);

    private final OrderService orderService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ToolExecutor(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Returns descriptions of all available tools for the system prompt.
     */
    public String getToolDescriptions() {
        return """
                1. createOrder - 创建奶茶订单
                   当客户确认要下单时调用。
                   参数(JSON):
                   {
                     "customerName": "客户姓名(必填)",
                     "phone": "客户手机号(必填)",
                     "items": [
                       {
                         "productName": "饮品名称(必填)",
                         "size": "杯型: 小杯/中杯/大杯(必填)",
                         "sweetness": "甜度: 无糖/少糖/半糖/正常糖/多糖(必填)",
                         "ice": "冰度: 去冰/少冰/正常冰/多冰/热饮(必填)",
                         "topping": "加料: 珍珠/椰果/仙草/布丁/芋圆(可选)",
                         "quantity": 数量(必填),
                         "unitPrice": 单价(必填)
                       }
                     ],
                     "remark": "备注(可选)"
                   }

                2. cancelOrder - 取消或退奶茶订单
                   当客户要取消或退订单时调用。
                   参数(JSON):
                   {
                     "orderId": "要取消的订单号(必填)",
                     "reason": "取消/退单原因(必填)"
                   }

                3. queryOrder - 查询奶茶订单
                   当客户要查询订单状态时调用，可通过订单号、手机号或姓名查询。
                   参数(JSON):
                   {
                     "orderId": "订单号(可选)",
                     "phone": "客户手机号(可选)",
                     "customerName": "客户姓名(可选)"
                   }
                   至少提供一个查询条件。""";
    }

    /**
     * Execute a tool by name with the given JSON input.
     */
    public String execute(String toolName, String jsonInput) {
        try {
            return switch (toolName.trim().toLowerCase()) {
                case "createorder" -> executeCreateOrder(jsonInput);
                case "cancelorder" -> executeCancelOrder(jsonInput);
                case "queryorder" -> executeQueryOrder(jsonInput);
                default -> "未知工具: " + toolName + "。可用工具: createOrder, cancelOrder, queryOrder";
            };
        } catch (Exception e) {
            log.error("Tool execution failed: tool={}, input={}", toolName, jsonInput, e);
            return "工具执行出错: " + e.getMessage();
        }
    }

    private String executeCreateOrder(String jsonInput) throws Exception {
        JsonNode root = objectMapper.readTree(jsonInput);

        String customerName = root.get("customerName").asText();
        String phone = root.get("phone").asText();
        String remark = root.has("remark") && !root.get("remark").isNull()
                ? root.get("remark").asText() : null;

        JsonNode itemsNode = root.get("items");
        List<OrderItem> items = new ArrayList<>();
        for (JsonNode itemNode : itemsNode) {
            OrderItem item = new OrderItem();
            item.setProductName(itemNode.get("productName").asText());
            item.setSize(itemNode.get("size").asText());
            item.setSweetness(itemNode.get("sweetness").asText());
            item.setIce(itemNode.get("ice").asText());
            if (itemNode.has("topping") && !itemNode.get("topping").isNull()) {
                item.setTopping(itemNode.get("topping").asText());
            }
            item.setQuantity(itemNode.get("quantity").asInt());
            item.setUnitPrice(itemNode.get("unitPrice").asDouble());
            items.add(item);
        }

        Order order = orderService.createOrder(customerName, phone, items, remark);
        return String.format("下单成功！订单号: %s，总价: %.2f元。", order.getOrderId(), order.getTotalPrice());
    }

    private String executeCancelOrder(String jsonInput) throws Exception {
        JsonNode root = objectMapper.readTree(jsonInput);
        String orderId = root.get("orderId").asText();
        String reason = root.get("reason").asText();
        return orderService.cancelOrder(orderId, reason);
    }

    private String executeQueryOrder(String jsonInput) throws Exception {
        JsonNode root = objectMapper.readTree(jsonInput);

        // Query by order ID
        if (root.has("orderId") && !root.get("orderId").isNull() && !root.get("orderId").asText().isBlank()) {
            String orderId = root.get("orderId").asText();
            Optional<Order> order = orderService.getOrder(orderId);
            return order.map(Order::toString).orElse("未找到订单号为 " + orderId + " 的订单");
        }
        // Query by phone
        if (root.has("phone") && !root.get("phone").isNull() && !root.get("phone").asText().isBlank()) {
            String phone = root.get("phone").asText();
            List<Order> orders = orderService.getOrdersByPhone(phone);
            if (orders.isEmpty()) return "未找到手机号 " + phone + " 的相关订单";
            return orders.stream().map(Order::toString).collect(Collectors.joining("\n---\n"));
        }
        // Query by name
        if (root.has("customerName") && !root.get("customerName").isNull() && !root.get("customerName").asText().isBlank()) {
            String customerName = root.get("customerName").asText();
            List<Order> orders = orderService.getOrdersByCustomerName(customerName);
            if (orders.isEmpty()) return "未找到客户 " + customerName + " 的相关订单";
            return orders.stream().map(Order::toString).collect(Collectors.joining("\n---\n"));
        }

        return "请提供订单号、手机号或客户姓名来查询订单";
    }
}
