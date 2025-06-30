package backend;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class ClientDummy {

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        boolean exit = false;

        ClientRequest clientRequest = new ClientRequest();
        System.out.print("Enter your latitude: ");
        double latitude = in.nextDouble();
        System.out.print("Enter your longitude: ");
        double longitude = in.nextDouble();

        // First, show stores that are near the client
        CustomMessage response = clientRequest.search(latitude, longitude, null, 0, "-");
        printReducedSearchResults(response); // Display stores near the client

        while (!exit) {
            System.out.println("\n========== Client Menu ==========");
            System.out.println("Welcome! What would you like to do?");
            System.out.println("1. Filter stores");
            System.out.println("2. Select store to buy from");
            System.out.println("3. Rate Store");

            int choice = in.nextInt();
            in.nextLine();

            switch (choice) {
                case 1: {
                    System.out.print("Enter categories (comma-separated): ");
                    String input = in.nextLine().trim(); // Trim the input
                    List<String> categories = input.isEmpty() ? new ArrayList<>() : Arrays.asList(input.split(","));

                    System.out.print("Enter minimum stars (0-5, 0 for any): ");
                    int minStars = in.nextInt();
                    in.nextLine();
                    System.out.print("Enter price range ($, $$, $$$ or - for any): ");
                    String priceRange = in.nextLine();

                    response = clientRequest.search(latitude, longitude, categories, minStars, priceRange);
                    printReducedSearchResults(response);
                    break;
                }
                case 2: {

                    // Call search again to get updated product quantities
                    response = clientRequest.search(latitude, longitude, null, 0, "-");

                    System.out.print("Enter store name to buy from: ");
                    String storeName = in.nextLine().trim();

                    // Get the chosen store's product list from the last search result
                    JSONArray storesArray = null;
                    if (response.getParameters() != null && response.getParameters().has("Stores")) {
                        storesArray = response.getParameters().getJSONArray("Stores");
                    }

                    if (storesArray == null) {
                        System.out.println("No stores loaded. Please search first.");
                        break;
                    }

                    JSONObject selectedStore = null;
                    for (int i = 0; i < storesArray.length(); i++) {
                        JSONObject storeJson = storesArray.getJSONObject(i);
                        if (storeJson.getString("StoreName").equalsIgnoreCase(storeName)) {
                            selectedStore = storeJson;
                            break;
                        }
                    }

                    if (selectedStore == null) {
                        System.out.println("Store not found.");
                        break;
                    }

                    // Show available products
                    JSONArray productsArray = selectedStore.getJSONArray("Products");
                    System.out.println("Products available in " + storeName + ":");
                    for (int i = 0; i < productsArray.length(); i++) {
                        JSONObject product = productsArray.getJSONObject(i);
                        System.out.printf("- %s | Price: %.2f | Available: %d%n",
                                product.getString("ProductName"),
                                product.getDouble("Price"),
                                product.getInt("AvailableAmount"));
                    }

                    JSONArray orderArray = new JSONArray();
                    boolean addMore = true;

                    // While customer wants to add more products
                    while (addMore) {
                        System.out.print("Enter product name to buy: ");
                        String productAnswer = in.nextLine().trim();

                        JSONObject foundProduct = null;
                        for (int i = 0; i < productsArray.length(); i++) {
                            JSONObject p = productsArray.getJSONObject(i);
                            if (p.getString("ProductName").equalsIgnoreCase(productAnswer)) {
                                foundProduct = p;
                                break;
                            }
                        }

                        if (foundProduct == null) {
                            System.out.println("Product not found.");
                            continue;
                        }

                        System.out.print("Enter quantity to buy: ");
                        int qty = in.nextInt();
                        in.nextLine();

                        int available = foundProduct.getInt("AvailableAmount");
                        if (qty <= 0 || qty > available) {
                            System.out.println("Invalid amount. Must be between 1 and " + available);
                            continue;
                        }

                        JSONObject productJson = new JSONObject();
                        productJson.put("Product", productAnswer);
                        productJson.put("Amount", qty);
                        orderArray.put(productJson);

                        System.out.print("Do you want to add more products? (yes/no): ");
                        String more = in.nextLine().trim().toLowerCase();
                        addMore = more.equals("yes");
                    }

                    // Send buy request
                    CustomMessage responseB = clientRequest.buy(storeName, orderArray, null);
                    if (responseB.getAction().equals("ERROR")) {
                        System.out.println(responseB.getJsonString());
                        System.out.println("Start the process again.");
                    } else {
                        System.out.println("Products bought successfully.");
                    }
                    break;
                }
                case 3: {
                    System.out.print("Enter store name to rate: ");
                    String storeName = in.nextLine();
                    System.out.print("How many stars do you rate the store (1-5): ");
                    double stars = in.nextDouble();

                    CustomMessage response1 = clientRequest.rate(storeName, stars, null);
                    if (response1.getAction().equals("ERROR")) {
                        System.out.println(response1.getJsonString());
                    } else {
                        System.out.println("Store rated successfully!");
                    }
                    break;
                }
                case 0:
                    System.out.println("Exiting...");
                    exit = true;
                    break;

                default:
                    exit = true;
                    System.out.println("There is no option: " + choice);
                    break;
            }
        }
    }

    private static void printReducedSearchResults(CustomMessage response) {
        JSONObject params = response.getParameters();

        if (params == null || !params.has("Stores")) {
            System.out.println("No search results found.");
            return;
        }

        JSONArray storeArray = params.getJSONArray("Stores");

        if (storeArray.isEmpty()) {
            System.out.println("You live too far from civilization, no stores found.");
            return;
        }

        System.out.println("Stores near you:");
        // Display stores near the client
        for (int i = 0; i < storeArray.length(); i++) {
            JSONObject storeJson = storeArray.getJSONObject(i);
            String storeName = storeJson.getString("StoreName");
            System.out.println("- " + storeName);
        }
    }
}
