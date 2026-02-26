package com.milktea.agent.model;

public class OrderItem {

    private String productName;
    private String size;       // 小杯/中杯/大杯
    private String sweetness;  // 无糖/少糖/半糖/正常糖/多糖
    private String ice;        // 去冰/少冰/正常冰/多冰
    private String topping;    // 加料: 珍珠/椰果/仙草/布丁
    private int quantity;
    private double unitPrice;

    public OrderItem() {}

    public OrderItem(String productName, String size, String sweetness, String ice, String topping, int quantity, double unitPrice) {
        this.productName = productName;
        this.size = size;
        this.sweetness = sweetness;
        this.ice = ice;
        this.topping = topping;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    // Getters and Setters
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }

    public String getSweetness() { return sweetness; }
    public void setSweetness(String sweetness) { this.sweetness = sweetness; }

    public String getIce() { return ice; }
    public void setIce(String ice) { this.ice = ice; }

    public String getTopping() { return topping; }
    public void setTopping(String topping) { this.topping = topping; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(double unitPrice) { this.unitPrice = unitPrice; }

    @Override
    public String toString() {
        return String.format("%s(%s/%s/%s%s) x%d @%.2f元",
                productName, size, sweetness, ice,
                topping != null && !topping.isEmpty() ? "/加" + topping : "",
                quantity, unitPrice);
    }
}
