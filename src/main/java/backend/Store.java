package backend;


import java.io.Serializable;
import java.util.ArrayList;

public class Store implements Serializable {
    private static int id; //used in mapping
    private final String storeName;
    private double latitude;
    private double longitude;
    private final String foodCategory;
    private double stars;
    private int numberOfVotes;
    private final String storeLogoPath;
    private ArrayList<Product> productsList;
    private String priceRange;
    private int totalSales = 0;
    public boolean storeInUse = false; // used in synchronisation

    public Store(String storeName, double latitude, double longitude,String foodCategory ,double stars, int numberOfVotes, String storeLogoPath, ArrayList<Product> productsList) {
        this.storeName = storeName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.foodCategory = foodCategory;
        this.stars = stars;
        this.numberOfVotes = numberOfVotes;
        this.storeLogoPath = storeLogoPath;
        this.productsList = productsList;

    }

    public int getId(){ return id;}

    public  void setId(int id){
        Store.id = id;
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

    public void calculatePriceRange(){
        double sum =0;
        for(Product product : productsList){
            sum += product.getPrice();
        }
        double averagePrice = sum / productsList.size();
        if( averagePrice <= 5 ){
            setPriceRange("$");
        }else if (averagePrice <=15){
            setPriceRange("$$");
        }else{
            setPriceRange("$$$");

        }
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public void setStars(double stars) {
        this.stars = stars;
    }

    public void setPriceRange(String priceRange) {
        this.priceRange = priceRange;
    }

    public void setNumberOfVotes(int numberOfVotes) {
        this.numberOfVotes = numberOfVotes;
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

    public ArrayList<Product> getProductsList() {
        return productsList;
    }

    public String getPriceRange() {
        return priceRange;
    }


}
