package main.java.backend;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;

public class JasonParser {

    public  Store jsonReader(String filePath) {

        Store store = null;

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            StringBuilder jsonContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonContent.append(line);
            }

            // Parse the JSON string into a JSONObject
            JSONObject storeJSON = new JSONObject(jsonContent.toString());

            // Extract store details
            // Extract basic store details
            String storeName = storeJSON.getString("StoreName");
            double latitude = storeJSON.getDouble("Latitude");
            double longitude = storeJSON.getDouble("Longitude");
            String foodCategory = storeJSON.getString("FoodCategory");
            int stars = storeJSON.getInt("Stars");
            int numberOfVotes = storeJSON.getInt("NoOfVotes");
            String logoPath = storeJSON.optString("StoreLogo", "");

            // Extract products
            ArrayList<Product> productList = new ArrayList<>();
            JSONArray productsArray = storeJSON.getJSONArray("Products");
            for (int i = 0; i < productsArray.length(); i++) {
                JSONObject productJSON = productsArray.getJSONObject(i);
                Product product = new Product(
                        productJSON.getString("ProductName"),
                        productJSON.getString("ProductType"),
                        productJSON.getInt("Available Amount"),
                        productJSON.getDouble("Price")
                );
                productList.add(product);
            }

            int id = storeJSON.has("Id") ? storeJSON.getInt("Id") : storeName.hashCode();
            store = new Store(storeName, latitude, longitude, foodCategory, stars, numberOfVotes, logoPath, productList, id);
        } catch (Exception e) {
            System.err.println("Error reading JSON: " + e.getMessage());
        }

        return store;
    }


}

