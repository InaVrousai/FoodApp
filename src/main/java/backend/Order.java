package backend;

import java.util.List;

public class Order {
    private int orderId;
    private String customerName;
    private String restaurantName;
    private int restaurantId;
    private List<String> items;
    private double totalPrice;

    public Order(int orderId, String customerName, int restaurantId, List<String> items, double totalPrice) {
        this.orderId = orderId;
        this.customerName = customerName;
        this.restaurantName = restaurantName;
        this.restaurantId = restaurantId;
        this.items = items;
        this.totalPrice = totalPrice;
    }

    public int getOrderId() { return orderId; }
    public String getCustomerName() { return customerName; }
    public String getRestaurantName() { return restaurantName; }
    public int getRestaurantId() { return restaurantId; }
    public List<String> getItems() { return items; }
    public double getTotalPrice() { return totalPrice; }
}
