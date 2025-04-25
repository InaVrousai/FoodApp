package main.java.backend;

import java.util.List;

public class Purchase {
    private String storeName;
    private List<String> productNames;
    private String customerName;

    public Purchase(String storeName, List<String> productNames, String customerName) {
        this.storeName = storeName;
        this.productNames = productNames;
        this.customerName = customerName;
    }

    public String getStoreName() {
        return storeName;
    }

    public List<String> getProductNames() {
        return productNames;
    }

    public String getCustomerName() {
        return customerName;
    }
}
