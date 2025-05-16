package backend;

import org.json.JSONObject;

import java.io.Serializable;

public class CustomMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String action;
    private final String jsonString;
    private Store store;
    private Product product;

    public CustomMessage(String action, JSONObject json, Store store, Product product) {
        this.action = action;
        this.jsonString = json != null ? json.toString() : "{}";  // Prevent null by using an empty JSON object
        this.store = store;
        this.product = product;
    }

    public String getAction() {
        return action;
    }

    public JSONObject getParameters() {
        return new JSONObject(jsonString != null ? jsonString : "{}");  // Safe fallback to an empty JSON object
    }

    public Store getStore() {
        return store;
    }

    public Product getProduct() {
        return product;
    }

    public String getJsonString() {
        return jsonString;
    }

    @Override
    public String toString() {
        return "CustomMessage{" +
                "action='" + action + '\'' +
                ", json=" + jsonString +
                '}';
    }
}
