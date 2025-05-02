package backend;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileReader;
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
            store = new Store(
                    storeJSON.getString("StoreName"),
                    storeJSON.getDouble("Latitude"),
                    storeJSON.getDouble("Longitude"),
                    storeJSON.getString("FoodCategory"),
                    storeJSON.getInt("Stars"),
                    storeJSON.getInt("NoOfVotes"),
                    storeJSON.getString("StoreLogo"),
                    new ArrayList<Product>()
            );

            // Extract products
            JSONArray productsArray = storeJSON.getJSONArray("Products");
            for (int i = 0; i < productsArray.length(); i++) {
                JSONObject productJSON = productsArray.getJSONObject(i);
                Product product = new Product(
                        productJSON.getString("ProductName"),
                        productJSON.getString("ProductType"),
                        productJSON.getInt("Available Amount"),
                        productJSON.getDouble("Price")
                );
                store.addProduct(product); // adds product in the list
            }
        } catch (Exception e) {
            System.err.println("Error reading JSON: " + e.getMessage());
        }

        return store;
    }

}
