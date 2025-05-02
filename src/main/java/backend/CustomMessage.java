package backend;

import org.json.JSONObject;
import java.io.Serializable;
import java.time.LocalDateTime;

public class CustomMessage implements Serializable {

    private String action;         // Η ενέργεια που πρέπει να εκτελεστεί
    private JSONObject parameters; // Το payload ή οι παράμετροι
    private Store store;
    private Product product;

    public CustomMessage(String action, JSONObject parameters,Store store,Product product) {
        this.action = action;
        this.parameters = parameters;
        this.store = store;
        this.product = product;
    }

    public String getAction() {
        return action;
    }

    public JSONObject getParameters() {
        return parameters;
    }

    public Store getStore(){ return store;}

    public Product getProduct() { return product; }

}
