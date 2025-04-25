package main.java.backend;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class ClientRequest {

    public Object sendRequest(CustomMessage message) {
        try (Socket socket = new Socket("localhost", 5000);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            out.writeObject(message);
            out.flush();
            return in.readObject();

        } catch (Exception e) {
            System.err.println("Error sending client request: " + e.getMessage());
            return null;
        }
    }

    public Object search(double latitude, double longitude, List<String> categories, int minStars, String priceRange) {
        JSONObject filterJson = new JSONObject();
        filterJson.put("latitude", latitude);
        filterJson.put("longitude", longitude);
        filterJson.put("categories", new JSONArray(categories));
        filterJson.put("minStars", minStars);
        filterJson.put("priceRange", priceRange);

        CustomMessage message = new CustomMessage("search", filterJson, null, null);
        return sendRequest(message);
    }

    public Object buy(String storeName, List<String> productNames, String customerName) {
        JSONObject buyJson = new JSONObject();
        buyJson.put("storeName", storeName);
        buyJson.put("products", new JSONArray(productNames));
        buyJson.put("customerName", customerName);

        ArrayList<Object> list = new ArrayList<>();
        list.add("buy");
        list.add(buyJson);

        CustomMessage message = new CustomMessage("buy", buyJson, null, null);
        return sendRequest(message);
    }

    public Object rate(String storeName, int stars, String customerName) {
        JSONObject rateJson = new JSONObject();
        rateJson.put("storeName", storeName);
        rateJson.put("stars", stars);
        rateJson.put("customerName", customerName);

        CustomMessage message = new CustomMessage("rate", rateJson, null, null);
        return sendRequest(message);
    }
}
