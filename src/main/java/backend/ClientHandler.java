package main.java.backend;
import org.json.JSONObject;
import java.io.*;
import java.net.Socket;
import java.util.Map;

public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private boolean running = true;

    public ClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        try (
                ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
        ) {
            out.flush();  // ensure header is sent
            try (ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream())) {
                Object received = in.readObject();
                if (!(received instanceof CustomMessage)) {
                    out.writeObject(new CustomMessage("error",
                            new JSONObject().put("message", "Invalid message type"),null,null));
                    return;
                }

                CustomMessage request = (CustomMessage) received;
                System.out.println("Received action: " + request.getAction());
                System.out.println("Payload: " + request.getParameters().toString());

                CustomMessage response = handleAction(request);
                out.writeObject(response);
            }
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error handling client: " + e.getMessage());
            try (ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream())) {
                out.writeObject(new CustomMessage("error",
                        new JSONObject().put("message", "Server error: " + e.getMessage()),null, null));
            } catch (IOException ex) {
                System.err.println("Error sending error message to client: " + ex.getMessage());
            }
        } finally {
            try {
                System.out.println("Closing client connection.");
                clientSocket.close();
            } catch (IOException e) {
                System.err.println("Failed to close client socket: " + e.getMessage());
            }
        }
    }

    private CustomMessage handleAction(CustomMessage msg) {
        String action = msg.getAction();
        JSONObject params = msg.getParameters();

        switch (action) {
            case "AddStore": {
                // 1) fetch the Store object from the message
                Store store = msg.getStore();
                String storeName = store.getStoreName();

                // 2) assign a new ID and set on the Store
                int storeId = MasterServer.getNextRestaurantId();
                store.setId(storeId);

                MasterServer.storeNameToId.put(storeName, storeId);

                // 3) select worker via consistent hashing
                int workerId = MasterServer.hash(storeId);

                // 4) forward to the chosen worker
                CustomMessage workerMsg = new CustomMessage(
                        "AddStore",
                        new JSONObject(),
                        store,
                        null
                );
                Object rawResp = MasterServer.sendMessageExpectReply(workerMsg, workerId);

                // 5) reply ACK or ERROR back to manager
                if (rawResp instanceof CustomMessage cm && "ACK".equals(cm.getAction())) {
                    return new CustomMessage("ACK", new JSONObject(), null, null);
                } else {
                    return new CustomMessage("ERROR", new JSONObject(), null, null);
                }
            }
            case "AddProduct":{
                // 1) extract
                String storeName = msg.getParameters().getString("StoreName");
                Product prod = msg.getProduct();

                // 2) pick worker by storeName hash
                Integer storeId = MasterServer.storeNameToId.get(storeName);

                if (storeId == null) {
                    return new CustomMessage("ERROR",
                            new JSONObject().put("message", "Unknown store: " + storeName),
                            null, null);
                }
                int workerId = MasterServer.hash(storeId);

                CustomMessage workerMsg = new CustomMessage("AddProduct",
                        new JSONObject().put("restaurantId", storeId),
                        null,
                        msg.getProduct());
                Object raw = MasterServer.sendMessageExpectReply(workerMsg, workerId);

                // ACK or ERROR
                String reply = (raw instanceof CustomMessage cm && "ACK".equals(cm.getAction()))
                        ? "ACK" : "ERROR";
                return new CustomMessage(reply, new JSONObject(), null, null);
            }
            case "RemoveProduct": {
                System.out.println("[DEBUG] Known stores: " + MasterServer.storeNameToId.keySet());
                // 1) pull store name & product name from the incoming JSON
                String storeName   = params.getString("Store");
                String productName = params.getString("Product");

                // 2) look up the storeId (as you do for AddProduct)
                Integer storeId = MasterServer.storeNameToId.get(storeName);
                if (storeId == null) {
                    return new CustomMessage(
                            "ERROR",
                            new JSONObject().put("message", "Unknown store: " + storeName),
                            null,
                            null
                    );
                }

                // 3) pick the worker by consistent hashing & forward
                int workerId = MasterServer.hash(storeId);
                CustomMessage workerMsg = new CustomMessage(
                        "RemoveProduct",
                        new JSONObject()
                                .put("restaurantId", storeId)
                                .put("Product",       productName),
                        null,
                        null
                );
                Object raw = MasterServer.sendMessageExpectReply(workerMsg, workerId);


                // 4) return ACK or ERROR back to the manager
                if (raw instanceof CustomMessage cm && "ACK".equals(cm.getAction())) {
                    return new CustomMessage("ACK", new JSONObject(), null, null);
                } else {
                    return new CustomMessage("ERROR", new JSONObject(), null, null);
                }
            }
            case "IncreaseProductAmount": {
                System.out.println("[DEBUG ClientHandler] IncreaseProductAmount params: " + params);
                // 1) extract
                String storeName = params.getString("Store");
                String productName = params.getString("Product");
                int delta = params.getInt("Amount");

                // 2) lookup
                Integer storeId = MasterServer.storeNameToId.get(storeName);
                if (storeId == null) {
                    return new CustomMessage("ERROR",
                            new JSONObject().put("message", "Unknown store: " + storeName),
                            null, null);
                }
                int workerId = MasterServer.hash(storeId);

                // 3) forward
                CustomMessage workerMsg = new CustomMessage(
                        "IncreaseProductAmount",
                        new JSONObject()
                                .put("restaurantId", storeId)
                                .put("Product",        productName)
                                .put("Amount",         delta),
                        null,
                        null
                );
                Object raw = MasterServer.sendMessageExpectReply(workerMsg, workerId);

                // 4) return
                if (raw instanceof CustomMessage cm && "ACK".equals(cm.getAction())) {
                    return new CustomMessage("ACK", new JSONObject(), null, null);
                } else {
                    return new CustomMessage("ERROR", new JSONObject(), null, null);
                }
            }

            case "DecreaseProductAmount": {
                // mirror of Increase but subtract
                String storeName = params.getString("Store");
                String productName = params.getString("Product");
                int delta = params.getInt("Amount");

                Integer storeId = MasterServer.storeNameToId.get(storeName);
                if (storeId == null) {
                    return new CustomMessage("ERROR",
                            new JSONObject().put("message", "Unknown store: " + storeName),
                            null, null);
                }
                int workerId = MasterServer.hash(storeId);

                CustomMessage workerMsg = new CustomMessage(
                        "DecreaseProductAmount",
                        new JSONObject()
                                .put("restaurantId", storeId)
                                .put("Product",        productName)
                                .put("Amount",         delta),
                        null,
                        null
                );
                Object raw = MasterServer.sendMessageExpectReply(workerMsg, workerId);
                System.out.println("[DEBUG ClientHandler] Worker raw response: " + raw);

                if (raw instanceof CustomMessage cm && "ACK".equals(cm.getAction())) {
                    return new CustomMessage("ACK", new JSONObject(), null, null);
                } else {
                    return new CustomMessage("ERROR", new JSONObject(), null, null);
                }
            }



            case "search":
            case "buy":
            case "rate": {
                String result = handleWorkerRequest(action, params);
                JSONObject body = new JSONObject(result);
                return new CustomMessage(action + "_RESPONSE", body, null, null);
            }

            case "nearby": {
                String combined = handleNearbyRequest(params);
                JSONObject body = new JSONObject(combined);
                return new CustomMessage("nearby_RESPONSE", body, null, null);
            }

            case "close":
                running = false;
                return new CustomMessage("Connection closed", new JSONObject(), null, null);

            default:
                return new CustomMessage(
                        "ERROR",
                        new JSONObject().put("message", "Unknown action: " + action),
                        null,
                        null
                );
        }
    }

    private String handleWorkerRequest(String action, JSONObject requestJson) {
        if (!requestJson.has("restaurantId")) {
            return new JSONObject().put("error", "Missing restaurantId").toString();
        }

        int restaurantId = requestJson.getInt("restaurantId");
        int workerId = MasterServer.hash(restaurantId);

        CustomMessage msg = new CustomMessage(action, requestJson, null, null);
        Object resp = MasterServer.sendMessageExpectReply(msg, workerId);

        if (resp instanceof JSONObject json) {
            return json.toString();
        } else if (resp instanceof CustomMessage cm) {
            JSONObject p = cm.getParameters();
            return p.length() > 0 ? p.toString() : new JSONObject().put("status", cm.getAction()).toString();
        } else {
            return new JSONObject().put("error", "No response from worker").toString();
        }
    }

    private String handleNearbyRequest(JSONObject requestJson) {
        CustomMessage msg = new CustomMessage("nearby", requestJson, null, null);
        Map<Integer, Object> responses = MasterServer.broadcastAndCollectResponses(msg);

        JSONObject combined = new JSONObject();
        for (var entry : responses.entrySet()) {
            Object r = entry.getValue();
            if (r instanceof CustomMessage cm) {
                JSONObject p = cm.getParameters();
                combined.put(
                        "worker_" + entry.getKey(),
                        p.length() > 0 ? p.toString() : cm.getAction()
                );
            } else {
                combined.put("worker_" + entry.getKey(), "No response");
            }
        }
        return combined.toString();
    }
}