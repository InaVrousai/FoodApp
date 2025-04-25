package main.java.backend;
import org.json.JSONObject;

public class Client {
    public static void main(String[] args) {
        JSONObject requestData = new JSONObject();
        requestData.put("restaurantId", 42);

        CustomMessage message = new CustomMessage("GET_STORES", requestData, null,null);
        ClientRequest client = new ClientRequest();
        Object response = client.sendRequest(message);

        if (response instanceof CustomMessage cm) {
            System.out.println("✅ Response received:");
            System.out.println("Action: " + cm.getAction());
            System.out.println("Parameters: " + cm.getParameters());
        } else if (response != null) {
            System.out.println("✅ Raw Response: " + response.toString());
        } else {
            System.out.println("❌ No response received (null)");
        }
    }
}


