package main.java.backend;


import java.io.Serializable;
import java.util.ArrayList;

public class Store implements Serializable {
    private String storeName;
    private double latitude;
    private double longitude;
    private String foodCategory;
    private double stars;
    private int numberOfVotes;
    private String storeLogoPath;
    private ArrayList<Product> productsList;
    private String priceRange;
    private int totalSales = 0;
    public boolean storeInUse = false; // used in synchronisation
    private static int id;

    public Store(String storeName, double latitude, double longitude,String foodCategory ,double stars, int numberOfVotes, String storeLogoPath, ArrayList<Product> productsList, int id) {
        this.storeName = storeName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.foodCategory = foodCategory;
        this.stars = stars;
        this.numberOfVotes = numberOfVotes;
        this.storeLogoPath = storeLogoPath;
        this.productsList = productsList;
        //this.id = id;
    }


    public int getTotalSales(){ return totalSales;}

    public void addTotalSales(int amount){ this.totalSales += amount ;}

    public Product findProduct(String product) {
        for (Product p : productsList) {
            if (p.getProductName().equals(product))
                return p ;
        }
        return null;
    }

    //finds the product that exists in the store and sets it out of use
    public void removeProduct(String product) {
        for (Product p : productsList) {
            if (p.getProductName().equals(product))
                p.setProductInUse(false);
        }
    }

    public void addProduct(Product product) throws Exception {
        if (productsList.contains(product))
            throw new Exception("Product already exists in the store!!");
        productsList.add(product); //adds a product in the store
    }


    public  void setId(int id){
        this.id = id;
    }
    public int getId(){
        return id;
    }

    public String getStoreName() {
        return storeName;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public String getFoodCategory() {
        return foodCategory;
    }

    public double getStars() {
        return stars;
    }

    public int getNumberOfVotes() {
        return numberOfVotes;
    }

    public String getStoreLogoPath() {
        return storeLogoPath;
    }

    public ArrayList<Product> getProductsList() {
        return productsList;
    }

    public String getPriceRange() {
        return priceRange;
    }
    public void setPriceRange(String range) { this.priceRange = range; }


}