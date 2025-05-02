package backend;

import org.json.JSONObject;

public class Product {
    private final String productName;
    private final String productType;
    private int availableAmount;
    private final double price;
    private boolean productInUse = true;
    private int totalSales =0;

    public Product(String productName, String productType, int availableAmount, double price){
        this.productName = productName;
        this.productType = productType;
        this.availableAmount = availableAmount;
        this.price = price;
    }

    public void addTotalSales(int sales){ this.totalSales += sales ;}

    public void setProductInUse(boolean inUse){
        this.productInUse = inUse;
    }

    public  void setAvailableAmount(int amount){ this.availableAmount = amount ; }

    public String getProductName() {
        return productName;
    }

    public String getProductType() {
        return productType;
    }

    public int getAvailableAmount() {
        return availableAmount;
    }

    public double getPrice() {
        return price;
    }

    public int getTotalSales() { return totalSales;}

    public boolean isProductInUse() {
        return productInUse;
    }

}