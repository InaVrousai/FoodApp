package backend;


import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.util.ArrayList;

import static backend.Worker.*;
import static java.lang.Math.max;



public class WorkerAction implements Runnable {
    ObjectOutputStream objectOutputStream;
    ObjectInputStream objectInputStream;
    Socket socket ;

    public WorkerAction(Socket socket) throws IOException {
        this.socket = socket;
        // Initialize output first to avoid deadlock
//        this.objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
//        this.objectOutputStream.flush();
//        this.objectInputStream = new ObjectInputStream(socket.getInputStream());
    }

    @Override
    public void run() {

        try(ObjectInputStream objectInputStream = new ObjectInputStream(socket.getInputStream());) {
            this.objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
            this.objectOutputStream.flush();
            Object received = objectInputStream.readObject();
            if (!(received instanceof CustomMessage)) {
                objectOutputStream.writeObject(new CustomMessage("error", new JSONObject().put("message", "Invalid message type"),null,null));
                return;
            }

            CustomMessage message = (CustomMessage) received;

            System.out.println("Message received: "+ message.getAction() +" with payload "+message.getJsonString());

            CustomMessage responseMessage = handleAction(message);

            objectOutputStream.writeObject(responseMessage);


        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error handling customer: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if(objectInputStream!=null)
                    objectInputStream.close();
                if(objectOutputStream!=null)
                    objectOutputStream.close();
            } catch (IOException e) {
                System.err.println("Error closing streams: " + e.getMessage());
            }
        }
    }


    private CustomMessage handleAction(CustomMessage message) throws Exception {

        switch (message.getAction()) {
            // === MANAGER COMMANDS ==
            case "AddStore":
            {
                Store store = message.getStore();
                System.out.println("Store added: " + store.getStoreName());
                store.calculatePriceRange(); //calculate price range
                // Store in memory
                storesList.add(store);
                synchronized (storeMapLock) {
                    Worker.storeMap.put(store.getId(), store);
                }

                return new CustomMessage("ACK", new JSONObject(), null, null);
            }

            case "AddProduct": {

                int storeId = message.getParameters().getInt("restaurantId");
                Store store;
                //syncro to avoid race conditions
                synchronized (storeMapLock) {
                    store = Worker.storeMap.get(storeId);   // finds store
                }
                if (store == null) {
                    return new CustomMessage("ERROR",
                                new JSONObject().put("message", "Store ID " + storeId + " not found"),
                                null, null
                        );
                }
                //adds the product to the store
                store.addProduct(message.getProduct());

                //recalculates price range
                store.calculatePriceRange();
                System.out.println("Add product was successful");
                return new CustomMessage("ACK", new JSONObject(), null, null);
            }

            case "RemoveProduct": {
                int storeId = message.getParameters().getInt("restaurantId");
                Store store;
                //syncro to avoid race conditions
                synchronized (storeMapLock) {
                    store = Worker.storeMap.get(storeId);   // finds store
                }
                if (store == null) {
                    return new CustomMessage("ERROR",
                            new JSONObject().put("message", "Store ID " + storeId + " not found"),
                            null, null);
                }
                String productName = message.getParameters().getString("Product");
                ArrayList<Product> products = store.getProductsList();
                //"removes" product from a store
                store.removeProduct(productName);
                store.calculatePriceRange();
                System.out.println("Remove was successful");
                return new CustomMessage("ACK", new JSONObject(), null, null);
            }
            case "TotalSales": {

                int storeId = message.getParameters().getInt("restaurantId");
                Store store;

                //syncro to avoid race conditions
                synchronized (storeMapLock) {
                    store = Worker.storeMap.get(storeId);   // finds store
                }
                if (store == null) {
                    return new CustomMessage("ERROR",
                            new JSONObject().put("message", "Store ID " + storeId + " not found"),
                            null, null
                    );
                }
                JSONObject json = new JSONObject();
                for (Product p : store.getProductsList()) {
                    json.put(p.getProductName(), p.getTotalSales());
                }
                System.out.println("Returning total sales");
                return new CustomMessage("ACK", json, null, null);
            }
            case "IncreaseProductAmount": {
                //gets store id from customMessage
                int storeId = message.getParameters().getInt("restaurantId");
                Store store;
                //syncro to avoid race conditions
                synchronized (storeMapLock) {
                    store = Worker.storeMap.get(storeId);   // finds store
                }
                if (store == null) {
                    return new CustomMessage("ERROR",
                            new JSONObject().put("message", "Store ID " + storeId + " not found"),
                            null, null
                    );
                }
                synchronized (store) {
                    //checks if the store is in use
                    while (store.storeInUse) {
                        try {
                            store.wait(); //if store is in use wait
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            System.err.println("Thread Interrupted");
                        }
                    }
                    //Store goes in use
                    store.storeInUse = true;

                    //Product amount
                    int amount = message.getParameters().getInt("Amount");
                    //finds the product we want to increase the amount
                    Product product = store.findProduct(message.getParameters().getString("Product"));
                    //increases the amount
                    if (product == null) {
                        return new CustomMessage("ERROR"
                                ,new JSONObject().put("message", "The product name doesn't exists: "+ message.getParameters().getString("Product"))
                                ,null,null);
                    }

                    //sets product in use in case the product ammount was zero so it was not in use
                    product.setProductInUse(true);
                    product.setAvailableAmount(product.getAvailableAmount() + amount);

                    System.out.println("Product amount increased: " + product.getProductName() + " to " + product.getAvailableAmount());

                    store.storeInUse = false; // Release store usage
                    store.notifyAll(); // wakes up other threads that are waiting for the store
                }
                System.out.println("Increase was successful");
                return new CustomMessage("ACK", new JSONObject(), null, null);
            }

            case "DecreaseProductAmount": {
                //gets store id from customMessage
                int storeId = message.getParameters().getInt("restaurantId");
                Store store;
                //syncro to avoid race conditions
                synchronized (storeMapLock) {
                    store = Worker.storeMap.get(storeId);   // finds store
                }
                if (store == null) {
                    return new CustomMessage("ERROR",
                            new JSONObject().put("message", "Store ID " + storeId + " not found"),
                            null, null
                    );
                }
                synchronized (store) {
                    //checks if the store is in use
                    while (store.storeInUse) {
                        try {
                            store.wait(); //if store is in use wait
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            System.err.println("Thread Interrupted");
                        }
                    }
                    //Store goes in use
                    store.storeInUse = true;

                    //Product amount
                    int amount1 = message.getParameters().getInt("Amount");
                    //finds the product we want to decrease the amount
                    Product product1 = store.findProduct(message.getParameters().getString("Product"));
                    //if the amount we want to decrease is bigger than the curent amount we zero the available amount
                    product1.setAvailableAmount(max(product1.getAvailableAmount() - amount1, 0));
                    //if the product amount drops to zero the product goes out of use
                    if (product1.getAvailableAmount() == 0)
                        product1.setProductInUse(false);

                    store.storeInUse = false; // Release store usage
                    store.notifyAll(); // wakes up other threads that are waiting for the store
                }
                System.out.println("Decrease was successful");
                    return new CustomMessage("ACK", new JSONObject(), null, null);
            }

            case "TotalSalesProductType": {
                //the jason object that stores the data that reducer needs
                JSONObject mapJson = new JSONObject(); //action variable is used to know if we find the products we are looking for
                String productType = message.getParameters().getString("ProductType");

                //a jsonArray that stores intermediate data that is used by the reducer
                JSONArray storesAnswers = new JSONArray();
                //locking list to avoid race conditions
                synchronized (storeListLock) {
                    for (Store store : storesList) {
                        int totalProductSales = 0;
                        //for every product in store
                        for (Product p : store.getProductsList()) {
                            if (p.getProductType().equals(productType))
                                //adds  total sales
                                totalProductSales += p.getTotalSales();
                        }
                        //creates a new json for each store
                        JSONObject storeAnswer = new JSONObject();
                        storeAnswer.put("TotalSales", totalProductSales);
                        storeAnswer.put("StoreName", store.getStoreName());
                        storesAnswers.put(storeAnswer);//inserts store total sales info into the json array
                    }
                    //inserts the data into the mapJson
                    mapJson.put("MapID", message.getParameters().getInt("MapID"));
                    mapJson.put("IntermediateData", storesAnswers);
                }
                sendToReducer(new CustomMessage(message.getAction(), mapJson, null, null)); //sends message to the reducer
                return new CustomMessage("ACK",new JSONObject(),null,null);

            }

            case "TotalSalesStoreCategory": {
                //the jason object that stores the data that reducer needs
                JSONObject mapJson = new JSONObject();
                //a jsonArray that stores intermediate data that is used by the reducer
                JSONArray storesAnswers = new JSONArray();
                int totalSales = 0;
                String storeFoodCategory = message.getParameters().getString("StoreFoodCategory");
                //locking store list to avoid race conditions
                synchronized (storeListLock) {
                    for (Store store : storesList) {
                        if (store.getFoodCategory().equals(storeFoodCategory)) {
                            totalSales = store.getTotalSales();

                            JSONObject storeAnswer = new JSONObject();
                            storeAnswer.put("TotalSales", totalSales);
                            storeAnswer.put("StoreName", store.getStoreName());
                            storesAnswers.put(storeAnswer);//inserts store total sales info into the json array
                        }

                    }
                    //inserts the data into the mapJson
                    mapJson.put("MapID", message.getParameters().getInt("MapID"));
                    mapJson.put("IntermediateData", storesAnswers);
                }
                sendToReducer(new CustomMessage(message.getAction(), mapJson, null, null)); //sends message to the reducer
                return new CustomMessage("ACK",new JSONObject(),null,null);

            }
                //=====Client Commands=====
            case "Search":
                return applyFilters(message);

            case "Buy":
                return  buy(message);

            case "Rate":
                return rate(message);

            default:
                return new CustomMessage("NACK", new JSONObject(),null,null);
        }

    }

    //Uses custom message that contains a json array with each product and the amount
    private  CustomMessage buy(CustomMessage message) throws Exception {

        int storeId = message.getParameters().getInt("restaurantId");
        Store store;
        //syncro to avoid race conditions
        synchronized (storeMapLock) {
            store = Worker.storeMap.get(storeId);   // finds store
        }
        if (store == null) {
            return new CustomMessage("ERROR",
                    new JSONObject().put("message", "Store ID " + storeId + " not found"),
                    null, null
            );
        }
        synchronized (store) {
            //checks if the store is in use
            while (store.storeInUse) {
                try {
                    store.wait(); //if store is in use wait
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("Thread Interrupted");
                }
            }
            //Store goes in use
            store.storeInUse = true;

            //Reads a jason array from the json object ,that stores every product and the preferable amount the client wants
            JSONArray productsArray = message.getParameters().getJSONArray("Order");
            for (int i = 0; i < productsArray.length(); i++) {
                JSONObject productObject = productsArray.getJSONObject(i);

                //reads the Product name and amount from the json array cell
                String productName = productObject.getString("Product");
                Product product = store.findProduct(productName); //finds the product object based on the product name

                if (product == null) {
                    return new CustomMessage("ERROR"
                            ,new JSONObject().put("message", "The product name doesn't exists: "+ productName)
                            ,null,null);
                }
                int amount = productObject.getInt("Amount");
                if (amount <= 0 || amount > product.getAvailableAmount()) {
                    return new CustomMessage("ERROR"
                            ,new JSONObject().put("message", "The product available amount is: "+ + product.getAvailableAmount())
                            ,null,null);
                }

                //Setting new available amount
                product.setAvailableAmount(product.getAvailableAmount() - amount);
                //if the customer buys all the product stock it gets out of use
                if (product.getAvailableAmount() == 0)
                    product.setProductInUse(false);
                //updates total sales of the product
                product.addTotalSales(amount);
                store.addTotalSales(amount);
            }

            store.storeInUse = false; // Release store usage
            store.notifyAll(); // wakes up other threads that are waiting for the store
        }
        System.out.println("Buy was successful");
        return new CustomMessage("ACK",null,null,null);
    }

        //Uses custom message that contains store and the rating L
        private CustomMessage rate (CustomMessage message){

            int storeId = message.getParameters().getInt("restaurantId");
            Store store;
            //syncro to avoid race conditions
            synchronized (storeMapLock) {
                store = Worker.storeMap.get(storeId);   // finds store
            }
            if (store == null) {
                return new CustomMessage("ERROR",
                        new JSONObject().put("message", "Store ID " + storeId + " not found"),
                        null, null
                );
            }
            synchronized (store) {
                //checks if the store is in use
                while (store.storeInUse) {
                    try {
                        store.wait(); //if store is in use wait
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        System.err.println("Thread Interrupted");
                    }
                }
                //Store goes in use
                store.storeInUse = true;
                //client rating
                double rating = message.getParameters().getDouble("Stars");

                //updating number of votes
                int newNumberOfVotes = store.getNumberOfVotes() + 1;
                store.setNumberOfVotes(newNumberOfVotes);
                //current stars
                double currentStars = store.getStars();
                //calculating new rating
                double newRating = ((currentStars * (newNumberOfVotes - 1)) + rating) / newNumberOfVotes;

                //updating rating
                store.setStars(newRating);


                store.storeInUse = false; // Release store usage
                store.notifyAll(); // wakes up other threads that are waiting for the store
            }
            System.out.println("Rate was successful");
            return new CustomMessage("ACK",null,null,null);
        }

        //we check if the store can be returned based on the filters applied
        private CustomMessage applyFilters (CustomMessage message){

            double latitude = message.getParameters().getDouble("latitude");
            double longitude = message.getParameters().getDouble("longitude");
            JSONArray jArray = message.getParameters().getJSONArray("categories");

            // Convert JSONArray to ArrayList
            ArrayList<String> categories = new ArrayList<>();
            for (int i = 0; i < jArray.length(); i++) {
                categories.add(jArray.getString(i));
            }
            ArrayList<Store> tempStoreList = new ArrayList<>();
            double minStars = message.getParameters().getDouble("minStars");
            String priceRange = message.getParameters().getString("priceRange");
            //locking store list to avoid race conditions
            synchronized (storeListLock) {
                for (Store store : storesList) {
                    System.out.println(store.getStoreName());
                    //if the store doesn't much one filter returns null
                    if (true){//DistanceCalculator.calculateDistance(store.getLatitude(), store.getLongitude(), latitude, longitude) <= 10.0) {
                        if (store.getPriceRange().equals(priceRange) || priceRange.equals("-")) {
                            if (store.getStars() >= minStars || minStars == 0) {
                                if (categories.contains(store.getFoodCategory()) || categories.isEmpty()) {
                                    //Returns message with the json
                                    tempStoreList.add(store);//adds store to the storeList that the reducer will receive
                                }
                            }
                        }
                    }
                }
            }
            JSONObject preReduce = new JSONObject();
            preReduce.put("IntermediateData" ,storesWithProductsToJson(tempStoreList));
            preReduce.put("MapID",message.getParameters().getInt("MapID"));
            sendToReducer(new CustomMessage("Search", preReduce, null, null)); //sends message to the reducer
            return new CustomMessage("ACK",null,null,null);
        }

    //Puts stores and their products in a json Array
    public JSONArray storesWithProductsToJson(ArrayList<Store> stores) {
        JSONArray storesJArray = new JSONArray();// stores all store jsons
        //locking store list to avoid race conditions
        synchronized (storeListLock) {
        for (Store store : stores) {
            //stores the name the id and the  products jsonArray
            JSONObject storeJson = new JSONObject();
            storeJson.put("StoreName", store.getStoreName());

            JSONArray productsArray = new JSONArray();
            for (Product product : store.getProductsList()) {
                //if product is in use show it to the client
                if(product.isProductInUse()) {
                    JSONObject productJson = new JSONObject();
                    productJson.put("ProductName", product.getProductName());
                    //productJson.put("productType", product.getProductType());
                    productJson.put("AvailableAmount", product.getAvailableAmount());
                    productJson.put("Price", product.getPrice());

                    productsArray.put(productJson);
                }
            }

            storeJson.put("Products", productsArray); // add product list to store object
            storesJArray.put(storeJson);           // add store to master array
        }
        }
        return storesJArray;
    }
    private void sendToReducer(CustomMessage mapMessage) {
        try (Socket reducerSocket = new Socket("localhost", 6000);
             ObjectOutputStream out = new ObjectOutputStream(reducerSocket.getOutputStream())) {
            out.writeObject(mapMessage);
            out.flush();
            System.out.println("Sent map message to reducer: " + mapMessage.getAction() +
                    " (MapID=" + mapMessage.getParameters().getInt("MapID") + ")");
        } catch (IOException e) {
            System.err.println("Failed to send to reducer: " + e.getMessage());
        }
    }

}
