package backend;

import java.util.ArrayList;
import java.util.Scanner;
import org.json.JSONObject;
import org.json.JSONArray;

public class Manager {

    public static void main(String[] args) {
        JasonParser j = new JasonParser();
        ManagerRequest managerRequest = new ManagerRequest();
        Scanner in = new Scanner(System.in);
        Store storeObj;
        String store;
        String product;
        int productAmount;
        CustomMessage message;
        CustomMessage serverResponse;
        ArrayList<Object> list;
        String path;
        JSONObject json;
        boolean exit = false;
        String foodCategory;

        while (true) {
            System.out.println("\n========== Manager Menu ==========");
            System.out.println("What do you want to do?");
            System.out.println("1. Add Store");
            System.out.println("2. Add Product to a Store");
            System.out.println("3. Remove Product from a Store");
            System.out.println("4. View Total Sales of Products");
            System.out.println("5. Increase Product Amount in a Store");
            System.out.println("6. Decrease Product Amount in a Store");
            System.out.println("7. View Total Sales of a Specific Store Type");
            System.out.println("8. View Total Sales of a Specific Product Type");
            System.out.println("9. Exit");
            System.out.print("Enter your choice: ");
            int choice = in.nextInt();
            in.nextLine();

            switch (choice) {
                case 1: {
                    System.out.print("Please insert the JSON file path: ");
                    path = in.nextLine();
                    storeObj = j.jsonReader(path);

                    // Create message with store information
                    message = new CustomMessage("AddStore", null, storeObj, null);

                    // Send store data to the master
                    serverResponse = managerRequest.sendRequest(message);

                    // Handle response from master
                    if (serverResponse.getAction().equals("ACK")) {
                        System.out.println("The store was added successfully.");
                    } else {
                        System.out.println("Error: Store was not added. " + serverResponse.getJsonString());
                    }
                    break;
                }

                case 2: {
                    System.out.print("Please insert the name of the store: ");
                    store = in.nextLine();

                    System.out.print("Enter product name: ");
                    String productName = in.nextLine();

                    System.out.print("Enter product type: ");
                    String productType = in.nextLine();

                    System.out.print("Enter available product amount: ");
                    productAmount = in.nextInt();

                    while (productAmount < 0) {
                        System.out.println("Product amount cannot be negative!");
                        System.out.print("Enter a valid product amount: ");
                        productAmount = in.nextInt();
                    }

                    System.out.print("Enter product price: ");
                    double productPrice = in.nextDouble();
                    while (productPrice < 0) {
                        System.out.println("Product price cannot be negative!");
                        System.out.print("Enter a valid product price: ");
                        productPrice = in.nextDouble();
                    }

                    // Create new product
                    Product productN = new Product(productName, productType, productAmount, productPrice);
                    JSONObject jsonStore = new JSONObject();
                    jsonStore.put("Store", store);  // Store name

                    // Create message with product to add
                    message = new CustomMessage("AddProduct", jsonStore, null, productN);

                    // Send request to the master
                    serverResponse = managerRequest.sendRequest(message);

                    // Handle response
                    if (serverResponse.getAction().equals("ACK")) {
                        System.out.println("The product was added successfully.");
                    } else {
                        System.out.println("Error: Product not added. " + serverResponse.getJsonString());
                    }
                    break;
                }

                case 3: {
                    System.out.println("Enter the name of the store: ");
                    store = in.nextLine();

                    System.out.println("Enter the name of the product to remove: ");
                    product = in.nextLine();

                    // Fill JSON object
                    json = new JSONObject();
                    json.put("Store", store);
                    json.put("Product", product);

                    // Create message to remove product
                    message = new CustomMessage("RemoveProduct", json, null, null);

                    // Send request
                    serverResponse = managerRequest.sendRequest(message);

                    // Handle response
                    if (serverResponse.getAction().equals("ACK")) {
                        System.out.println("The product was removed successfully.");
                    } else {
                        System.out.println("Error: Product not removed. " + serverResponse.getJsonString());
                    }
                    break;
                }

                case 4: {
                    System.out.println("Enter the name of the store: ");
                    store = in.nextLine();
                    json = new JSONObject();
                    json.put("Store", store);

                    message = new CustomMessage("TotalSales", json, null, null);
                    serverResponse = managerRequest.sendRequest(message);

                    if (serverResponse.getAction().equals("ACK")) {
                        JSONObject salesData = serverResponse.getParameters();
                        for (String productName : salesData.keySet()) {
                            int totalSales = salesData.getInt(productName);
                            System.out.println("Product: " + productName + ", Total Sales: " + totalSales);
                        }
                    } else {
                        System.out.println("Error retrieving total sales: " + serverResponse.getJsonString());
                    }
                    break;
                }

                case 5: {
                    System.out.println("Enter the name of the store: ");
                    store = in.nextLine();

                    System.out.println("Enter the name of the product: ");
                    product = in.nextLine();

                    System.out.println("Enter the amount to increase: ");
                    productAmount = in.nextInt();

                    while (productAmount < 0) {
                        System.out.println("Amount cannot be negative!");
                        System.out.print("Enter a valid amount: ");
                        productAmount = in.nextInt();
                    }

                    json = new JSONObject();
                    json.put("Store", store);
                    json.put("Product", product);
                    json.put("Amount", productAmount);

                    message = new CustomMessage("IncreaseProductAmount", json, null, null);
                    serverResponse = managerRequest.sendRequest(message);

                    if (serverResponse.getAction().equals("ACK")) {
                        System.out.println("Product amount increased successfully.");
                    } else {
                        System.out.println("Error: Could not increase product amount. " + serverResponse.getJsonString());
                    }
                    break;
                }

                case 6: {
                    System.out.println("Enter the name of the store: ");
                    store = in.nextLine();

                    System.out.println("Enter the name of the product: ");
                    product = in.nextLine();

                    System.out.println("Enter the amount to decrease: ");
                    productAmount = in.nextInt();

                    while (productAmount < 0) {
                        System.out.println("Amount cannot be negative!");
                        System.out.print("Enter a valid amount: ");
                        productAmount = in.nextInt();
                    }

                    json = new JSONObject();
                    json.put("Store", store);
                    json.put("Product", product);
                    json.put("Amount", productAmount);

                    message = new CustomMessage("DecreaseProductAmount", json, null, null);
                    serverResponse = managerRequest.sendRequest(message);

                    if (serverResponse.getAction().equals("ACK")) {
                        System.out.println("Product amount decreased successfully.");
                    } else {
                        System.out.println("Error: Could not decrease product amount. " + serverResponse.getJsonString());
                    }
                    break;
                }

                case 7: {
                    System.out.println("Enter the food category of the store: ");
                    foodCategory = in.nextLine();

                    json = new JSONObject();
                    json.put("StoreFoodCategory", foodCategory);

                    message = new CustomMessage("TotalSalesStoreCategory", json, null, null);
                    serverResponse = managerRequest.sendRequest(message);

                    if (serverResponse.getAction().equals("ReducedTotalSales")) {
                        JSONObject params = serverResponse.getParameters();
                        if (params.has("Stores")) {
                            int grandTotalSales = 0;
                            JSONArray storesArray = params.getJSONArray("Stores");

                            for (int i = 0; i < storesArray.length(); i++) {
                                JSONObject storeJ = storesArray.getJSONObject(i);
                                String storeName = storeJ.getString("StoreName");
                                int totalSales = storeJ.getInt("TotalSales");
                                grandTotalSales += totalSales;
                                System.out.println("Store: " + storeName + ", Total Sales: " + totalSales);
                            }
                            System.out.println("Total Sales: " + grandTotalSales);
                        } else {
                            System.out.println("No stores found with that food category.");
                        }
                    } else {
                        System.out.println("Error retrieving total sales for this store category.");
                    }
                    break;
                }

                case 8: {
                    System.out.println("Enter the product type: ");
                    product = in.nextLine();

                    json = new JSONObject();
                    json.put("ProductType", product);

                    message = new CustomMessage("TotalSalesProductType", json, null, null);
                    serverResponse = managerRequest.sendRequest(message);

                    if (serverResponse.getAction().equals("ReducedTotalSales")) {
                        JSONObject params = serverResponse.getParameters();
                        if (params.has("Stores")) {
                            int grandTotalSales = 0;
                            JSONArray storesArray = params.getJSONArray("Stores");

                            for (int i = 0; i < storesArray.length(); i++) {
                                JSONObject storeJ = storesArray.getJSONObject(i);
                                String storeName = storeJ.getString("StoreName");
                                int totalSales = storeJ.getInt("TotalSales");
                                grandTotalSales += totalSales;
                                System.out.println("Store: " + storeName + ", Total Sales: " + totalSales);
                            }
                            System.out.println("Total Sales: " + grandTotalSales);
                        } else {
                            System.out.println("No stores found with that product type.");
                        }
                    } else {
                        System.out.println("Error retrieving total sales for this product type.");
                    }
                    break;
                }

                case 9:
                    exit = true;
                    break;

                default:
                    System.out.println("Invalid choice. Please try again.");
            }

            if (exit)
                break;
        }
    }
}
