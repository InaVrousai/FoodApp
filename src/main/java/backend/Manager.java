package main.java.backend;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Scanner;
import org.json.JSONObject;
import org.json.JSONArray;

public class Manager {


    public static void main(String[] args) {
        JasonParser j = new JasonParser();
        ManagerRequest managerRequest = new ManagerRequest();
        Scanner in = new Scanner(System.in);
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

        while(true){
            System.out.println("========== Manager Menu ==========");
            System.out.println("What do you want to do?");
            System.out.println("1. Add Store");
            System.out.println("2. Add Product to a Store");
            System.out.println("3. Remove Product from a Store");
            System.out.println("4. View Total Sales of products");
            System.out.println("5. Remove Product amount from a Store");
            System.out.println("6. Remove Product amount from a Store");
            System.out.println("7. View Total Sales of a specific store type");
            System.out.println("8. View Total Sales of a specific product type");
            System.out.println("9. Exit");
            System.out.print("Enter your choice: ");
            int choice = in.nextInt();
            in.nextLine();

            switch(choice){
                case 1:

                    System.out.println("Please insert the json path: ");
                    path = in.nextLine();
                    Store tempStore =  j.jsonReader(path);

                    //adds the message type and Store in the message
                    message = new CustomMessage("AddStore", null,tempStore,null);

                    //Sends the store to the Master
                    serverResponse = managerRequest.sendRequest(message);
                    if (serverResponse == null) {
                        System.err.println("No response from server—check that MasterServer is running.");
                    } else if ("ACK".equals(serverResponse.getAction())) {
                        System.out.println("The store was added successfully");
                    } else {
                        System.out.println("Error store not added ");
                    }
                    //handles master response

                    if(serverResponse.getAction().equals("ACK")) {
                        System.out.println("The store was added successfully");
                    }else{
                        System.out.println("Error store not added ");
                    }
                    break;

                case 2:

                    System.out.println("Please insert the name of the store:");
                    store = in.nextLine();
                    //in.nextLine();

                    System.out.println("Give product name");
                    String productName = in.nextLine();
                    //in.nextLine();
                    System.out.println("Give product type");
                    String productType = in.nextLine();
                    //in.nextLine();
                    System.out.println("Give product available amount");
                    productAmount = in.nextInt();
                    in.nextLine();
                    while(productAmount < 0) {
                        System.out.println("Product available amount cannot be negative!!!");
                        System.out.println("Give new roduct available amount.");
                        productAmount = in.nextInt();
                        in.nextLine();
                    }

                    double productPrice;
                    while (true) {
                        System.out.println("Give product price");
                        String priceToken = in.nextLine();
                        try {
                            productPrice = Double.parseDouble(priceToken);
                            if (productPrice < 0) {
                                System.out.println("Price cannot be negative!");
                            } else {
                                break;  // good value
                            }
                        } catch (NumberFormatException e) {
                            System.out.println("Invalid decimal. Please enter a number like 7.00");
                        }
                    }

                    //creates the new product
                    Product productN = new Product(productName,productType,productAmount,productPrice);
                    //adds the message type and product in the message
                   // message = new CustomMessage("AddProduct", null,null,productN);

                    JSONObject payload = new JSONObject();
                    payload.put("StoreName", store);
                    // send the store name + product object\
                    message = new CustomMessage("AddProduct", payload, null, productN);

                    //Sends a request to the Master
                    serverResponse = managerRequest.sendRequest(message);
                    //handles master response
                    if(serverResponse.getAction().equals("ACK")) {
                        System.out.println("The product was added successfully");
                    }else{
                        System.out.println("Error product not added ");
                    }
                    break;
                case 3:

                    System.out.println("Please insert the name of the store: ");
                    store = in.nextLine();
                    in.nextLine();
                    System.out.println ("And the name of the product: ");
                    product = in.nextLine();
                    in.nextLine();

                    //filling up json
                    json = new JSONObject();
                    json.put("Store", store);
                    json.put("Product", product);


                    //adds the message type and product in the list
                    message = new CustomMessage("RemoveProduct", json,null,null);

                    //Sends a request to the Master
                    serverResponse = managerRequest.sendRequest(message);
                    //handles master response
                    if(serverResponse.getAction().equals("ACK")) {
                        System.out.println("The product was removed successfully");
                    }else{
                        System.out.println("Error product not removed ");
                    }
                    break;

                case 4:

                    //adds the message type and product in the list
                    message = new CustomMessage("TotalSales", null,null,null);
                    //Sends a request to the Master
                    serverResponse = managerRequest.sendRequest(message);
                    if(serverResponse.getAction().equals("ACK")) {
                        //handles master response
                        JSONArray array = serverResponse.getParameters().getJSONArray("TotalSales");
                        for(int i=0;i<array.length();i++)
                            System.out.println(array.getString(i));//ασ υποθεσουμε οτι καθε κελι εχει string στην μορφη pizza:100 klp
                    }else{
                        System.out.println("Error with total sales");
                    }
                    break;


                case 5:

                    System.out.println("Please insert the name of the store: ");
                    store = in.nextLine();
                    in.nextLine();
                    System.out.println("Please insert the name of the product: ");
                    product = in.nextLine();
                    in.nextLine();
                    System.out.println ("Please insert the amount of the product: ");
                    productAmount = in.nextInt();
                    in.nextLine();

                    while(productAmount < 0) {
                        System.out.println("You can not increase product available amount with a negative number !!!");
                        System.out.println("Give new product available amount.");
                        productAmount = in.nextInt();
                        in.nextLine();
                    }

                    //filling up json
                    json = new JSONObject();
                    json.put("Store", store);
                    json.put("Product", product);
                    json.put("Amount",productAmount);


                    //adds the message type and product in the list
                    message = new CustomMessage("IncreaseProductAmount", json,null,null);

                    //Sends a request to the Master
                    serverResponse = managerRequest.sendRequest(message);
                    //handles master response
                    if(serverResponse.getAction().equals("ACK")) {
                        System.out.println("The product was increased successfully");
                    }else{
                        System.out.println("Error product was not increased");
                    }
                    break;

                case 6:

                    System.out.println("Please insert the name of the store: ");
                    store = in.nextLine();
                    in.nextLine();
                    System.out.println("Please insert the name of the product: ");
                    product = in.nextLine();
                    in.nextLine();
                    System.out.println ("Please insert the amount of the product: ");
                    productAmount = in.nextInt();
                    in.nextLine();

                    while(productAmount < 0) {
                        System.out.println("You cant decrease product available with a negative number!!!");
                        System.out.println("Give new product available amount.");
                        productAmount = in.nextInt();
                        in.nextLine();
                    }

                    //filling up json
                    json = new JSONObject();
                    json.put("Store", store);
                    json.put("Product", product);
                    json.put("Amount",productAmount);


                    //adds the message type and product in the list
                    message = new CustomMessage("DecreaseProductAmount", json,null,null);
                    //Sends a request to the Master
                    serverResponse = managerRequest.sendRequest(message);
                    //handles master response
                    if(serverResponse.getAction().equals("ACK")) {
                        System.out.println("The product was decreased successfully");
                    }else{
                        System.out.println("Error product was not decreased");
                    }
                    break;

                case 7:

                    System.out.println("Please insert the food category of the store: ");
                    foodCategory = in.nextLine();
                    in.nextLine();
                    //filling up json
                    json = new JSONObject();
                    json.put("StoreFoodCategory", foodCategory);


                    //adds the message type and product in the list
                    message = new CustomMessage("TotalSalesStoreCategory", json,null,null);

                    //Sends a request to the Master
                    serverResponse = managerRequest.sendRequest(message);
                    //handles master response
                    if(serverResponse.getAction().equals("ACK")) {
                        Iterator<String> keys = serverResponse.getParameters().keys();
                        while (keys.hasNext()) {
                            String key = keys.next();
                            System.out.println(key + ": " + serverResponse.getParameters().get(key));
                        }

                    }else{
                        System.out.println("Error");
                    }

                    break;

                case 8:

                    System.out.println("Please insert the product type: ");
                    product = in.nextLine();
                    in.nextLine();

                    //filling up json
                    json = new JSONObject();
                    json.put("ProductType", product);

                    //adds the message type and product in the list
                    message = new CustomMessage("TotalSalesProductType", json,null,null);

                    //Sends a request to the Master
                    serverResponse = managerRequest.sendRequest(message);
                    //handles master response
                    if(serverResponse.getAction().equals("ACK")) {
                        Iterator<String> keys = serverResponse.getParameters().keys();
                        while (keys.hasNext()) {
                            String key = keys.next();
                            System.out.println(key + ": " + serverResponse.getParameters().get(key));
                        }

                    }else{
                        System.out.println("Error");
                    }
                    break;

                case 9:
                    exit = true;
                    break;
                default:
                    System.out.println("Invalid choice.Please try again");
            }
            if (exit)
                break;
        }


    }
}