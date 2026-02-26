package com.milktea.agent.model;

import java.time.LocalDateTime;
import java.util.List;

public class Order {

    private String orderId;
    private String customerName;
    private String phone;
    private List<OrderItem> items;
    private double totalPrice;
    private OrderStatus status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String remark;

    public Order() {
        this.createTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
        this.status = OrderStatus.PENDING;
    }

    // Getters and Setters
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }

    public double getTotalPrice() { return totalPrice; }
    public void setTotalPrice(double totalPrice) { this.totalPrice = totalPrice; }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    @Override
    public String toString() {
        return String.format("订单号: %s, 客户: %s, 电话: %s, 商品: %s, 总价: %.2f元, 状态: %s, 下单时间: %s, 备注: %s",
                orderId, customerName, phone, items, totalPrice, status.getDescription(), createTime, remark);
    }
}
