package main.java.backend;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.util.ArrayList;


import static java.lang.Math.min;


public class WorkerAction implements Runnable {
    ObjectOutputStream objectOutputStream;
    ObjectInputStream objectInputStream;
    private Store store = null;

    public WorkerAction(Socket socket) throws IOException {
        // Initialize output first to avoid deadlock
        this.objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
        this.objectOutputStream.flush();
        this.objectInputStream = new ObjectInputStream(socket.getInputStream());
    }

    @Override
    public void run() {

        try {
            Object received = objectInputStream.readObject();
            if (!(received instanceof CustomMessage)) {
                objectOutputStream.writeObject(new CustomMessage("error", new JSONObject().put("message", "Invalid message type"), null, null));
                return;
            }

            CustomMessage message = (CustomMessage) received;
            System.out.println("Message received:" + message.getAction());

            CustomMessage responseMessage = handleAction(message);

            objectOutputStream.writeObject(responseMessage);


        } catch (Exception e) {
            System.err.println("Error handling customer: " + e.getMessage());
        } finally {
            try {
                objectInputStream.close();
                objectOutputStream.close();
            } catch (IOException e) {
                System.err.println("Error closing streams: " + e.getMessage());
            }
        }
    }


    private CustomMessage handleAction(CustomMessage message) throws Exception {
        switch (message.getAction()) {

            case "AddStore": {
                // 1) Extract Store object
                Store store = message.getStore();


                store.setPriceRange(calculatePriceRange(store.getProductsList()));

                // 4) Store in memory
                Worker.stores.add(store);
                Worker.storeMap.put(store.getId(), store);


                // 5) Acknowledge
                return new CustomMessage("ACK", new JSONObject(), null, null);
            }
            case "AddProduct": {
                int storeId = message.getParameters().getInt("restaurantId");
                Store store = Worker.storeMap.get(storeId);   // O(1) lookup
                if (store == null) {
                    return new CustomMessage("ERROR",
                            new JSONObject().put("message", "Store ID " + storeId + " not found"),
                            null, null
                    );
                }

                // now add the product
                store.addProduct(message.getProduct());
                store.setPriceRange(calculatePriceRange(store.getProductsList()));
                return new CustomMessage("ACK", new JSONObject(), null, null);
            }
            case "RemoveProduct": {
                int storeId = message.getParameters().getInt("restaurantId");
                Store store = Worker.storeMap.get(storeId);
                if (store == null) {
                    return new CustomMessage("ERROR",
                            new JSONObject().put("message", "Store ID " + storeId + " not found"),
                            null, null);
                }
                String productName = message.getParameters().getString("Product");
                ArrayList<Product> products = store.getProductsList();
                boolean removed = products.removeIf(p -> p.getProductName().equals(productName));

                if (!removed) {
                    return new CustomMessage("ERROR",
                            new JSONObject().put("message", "Product " + productName + " not found in store ID " + storeId),
                            null, null);
                }
                store.setPriceRange(calculatePriceRange(products));
                return new CustomMessage("ACK", new JSONObject(), null, null);
            }
            case "IncreaseProductAmount": {
                int storeId   = message.getParameters().getInt("restaurantId");
                String name   = message.getParameters().getString("Product").trim();
                int delta     = message.getParameters().getInt("Amount");
                Store store   = Worker.storeMap.get(storeId);

                System.out.printf("[DEBUG] IncAmt store=%d product=\"%s\" delta=%d%n", storeId, name, delta);
                System.out.println("[DEBUG] Before update, products:");
                for (Product p : store.getProductsList()) {
                    System.out.printf("    • \"%s\": avail=%d%n",
                            p.getProductName(), p.getAvailableAmount());
                }

                boolean found = false;
                for (Product p : store.getProductsList()) {
                    if (p.getProductName().trim().equalsIgnoreCase(name)) {
                        p.setAvailableAmount(p.getAvailableAmount() + delta);
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    System.err.println("[DEBUG] Product not found: " + name);
                    return new CustomMessage("ERROR",
                            new JSONObject().put("message", "Product " + name + " not found"), null, null);
                }

                System.out.println("[DEBUG] After update, new avail:");
                for (Product p : store.getProductsList()) {
                    if (p.getProductName().trim().equalsIgnoreCase(name)) {
                        System.out.printf("    • \"%s\": avail=%d%n",
                                p.getProductName(), p.getAvailableAmount());
                    }
                }

                store.setPriceRange(calculatePriceRange(store.getProductsList()));
                return new CustomMessage("ACK", new JSONObject(), null, null);
            }
            default:
                // Unknown action
                return new CustomMessage(
                        "NACK",
                        new JSONObject().put(
                                "message",
                                "Unknown action: " + message.getAction()
                        ),
                        null,
                        null
                );
        }

    }
    private String calculatePriceRange(ArrayList<Product> productsList){
        double sum =0;
        for(Product product : productsList){
            sum += product.getPrice();
        }
        double averagePrice = sum / productsList.size();
        if( averagePrice <= 5 ){
            return "$";
        }else if (averagePrice <=15){
            return "$$";
        }else{
            return "$$$";

        }
    }

}
