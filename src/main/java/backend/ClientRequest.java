package backend;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class ClientRequest {

    public CustomMessage sendRequest(CustomMessage message) {
        try (Socket socket = new Socket("localhost", 5000);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            out.writeObject(message);
            out.flush();
            return (CustomMessage) in.readObject();

        } catch (Exception e) {
            System.err.println("Error sending client request: " + e.getMessage());
            return null;
        }
    }

    public CustomMessage search(double latitude, double longitude, List<String> categories, int minStars, String priceRange) {
        JSONObject filterJson = new JSONObject();
        filterJson.put("latitude", latitude);
        filterJson.put("longitude", longitude);
        filterJson.put("categories", new JSONArray(categories));
        filterJson.put("minStars", minStars);
        filterJson.put("priceRange", priceRange);


        CustomMessage message = new CustomMessage("Search", filterJson,null,null);
        return sendRequest(message);
    }
    //the order must consist of a json array that every cell has a Product Amount
    public CustomMessage buy(String storeName, JSONArray order, String customerName) {
        JSONObject buyJson = new JSONObject();
        buyJson.put("Store", storeName);
        buyJson.put("Order", order);
        //buyJson.put("customerName", customerName);

        CustomMessage message = new CustomMessage("Buy", buyJson,null,null);
        return sendRequest(message);
    }

    public CustomMessage rate(String storeName, double stars, String customerName) {
        JSONObject rateJson = new JSONObject();
        rateJson.put("Store", storeName);
        rateJson.put("Stars", stars);
        //rateJson.put("customerName", customerName);

        CustomMessage message = new CustomMessage("Rate", rateJson,null,null);
        return sendRequest(message);
    }
}