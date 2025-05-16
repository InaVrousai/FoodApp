package backend;
import org.json.JSONObject;
import java.io.*;
import java.net.Socket;

public class MasterHandler implements Runnable {
    private final Socket clientSocket;
    private boolean running = true;

    public MasterHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        try {
            ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
            out.flush(); // Ensure stream header is sent

            try {
                ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
                Object received = in.readObject();

                if (!(received instanceof CustomMessage)) {
                    // Respond with error if the message type is invalid
                    sendResponse(new CustomMessage("ERROR",
                            new JSONObject().put("message", "Invalid message type"), null, null), out);
                    return;
                }

                CustomMessage request = (CustomMessage) received;
                System.out.println("Received action: " + request.getAction());
                System.out.println("Payload: " + request.getJsonString());

                handleAction(request, out);
            } catch (IOException e) {
                System.err.println("Failed to close client socket: " + e.getMessage());
            }

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error handling client: " + e.getMessage());
            try (ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream())) {
                sendResponse(new CustomMessage("error",
                        new JSONObject().put("message", "Server error: " + e.getMessage()), null, null), out);
            } catch (IOException ex) {
                System.err.println("Error sending error message to client: " + ex.getMessage());
            }
        } finally {
            try {
                System.out.println("Closing client connection.");
                if (false) // ← this seems to be a placeholder or debugging line
                    clientSocket.close();
            } catch (IOException e) {
                System.err.println("Failed to close client socket: " + e.getMessage());
            }
        }
    }

    private void handleAction(CustomMessage msg, ObjectOutputStream socket) throws IOException {
        String action = msg.getAction();
        JSONObject params = msg.getParameters();

        switch (action) {
            case "AddStore": {
                // Retrieve Store object
                Store store = msg.getStore();
                String storeName = store.getStoreName();

                // Check if store already exists
                if (MasterServer.storeNameToId.containsKey(storeName)) {
                    sendResponse(new CustomMessage("ERROR",
                            new JSONObject().put("ERROR", "The store already exists!"), null, null), socket);
                    return;
                }

                // Assign a new ID to the store
                int storeId = MasterServer.getNextRestaurantId();
                store.setId(storeId);
                MasterServer.storeNameToId.put(storeName, storeId);

                // Select worker via consistent hashing
                int workerId = MasterServer.hash(storeId);

                // Forward the message to the chosen worker
                CustomMessage workerMsg = new CustomMessage("AddStore", new JSONObject(), store, null);
                Object rawResp = MasterServer.sendMessageExpectReply(workerMsg, workerId);

                // Send ACK or ERROR back to manager
                if (rawResp instanceof CustomMessage cm && "ACK".equals(cm.getAction())) {
                    sendResponse(new CustomMessage("ACK", new JSONObject(), null, null), socket);
                } else {
                    sendResponse(new CustomMessage("ERROR", new JSONObject(), null, null), socket);
                }
                clientSocket.close();
                break;
            }

            case "AddProduct": {
                // Extract store name from parameters
                String storeName = msg.getParameters().getString("Store");

                // Look up store ID
                Integer storeId = MasterServer.storeNameToId.get(storeName);
                if (storeId == null) {
                    sendResponse(new CustomMessage("ERROR",
                            new JSONObject().put("message", "Unknown store: " + storeName), null, null), socket);
                    return;
                }

                int workerId = MasterServer.hash(storeId);

                // Forward to worker
                CustomMessage workerMsg = new CustomMessage("AddProduct",
                        new JSONObject().put("restaurantId", storeId),
                        null, msg.getProduct());

                Object raw = MasterServer.sendMessageExpectReply(workerMsg, workerId);

                // Respond with ACK or ERROR
                if (raw instanceof CustomMessage cm && cm.getAction().equals("ACK")) {
                    sendResponse(new CustomMessage("ACK", new JSONObject(), null, null), socket);
                } else {
                    sendResponse(new CustomMessage("ERROR", new JSONObject(), null, null), socket);
                }
                clientSocket.close();
                break;
            }

            case "RemoveProduct": {
                System.out.println("[DEBUG] Known stores: " + MasterServer.storeNameToId.keySet());

                String storeName = params.getString("Store");
                String productName = params.getString("Product");

                Integer storeId = MasterServer.storeNameToId.get(storeName);
                if (storeId == null) {
                    sendResponse(new CustomMessage("ERROR",
                            new JSONObject().put("message", "Unknown store: " + storeName), null, null), socket);
                    return;
                }

                int workerId = MasterServer.hash(storeId);
                CustomMessage workerMsg = new CustomMessage("RemoveProduct",
                        new JSONObject().put("restaurantId", storeId).put("Product", productName),
                        null, null);

                Object raw = MasterServer.sendMessageExpectReply(workerMsg, workerId);

                if (raw instanceof CustomMessage cm && cm.getAction().equals("ACK")) {
                    sendResponse(new CustomMessage("ACK", new JSONObject(), null, null), socket);
                } else {
                    sendResponse(new CustomMessage("ERROR", new JSONObject(), null, null), socket);
                }
                clientSocket.close();
                break;
            }

            case "IncreaseProductAmount":
            case "DecreaseProductAmount": {
                String storeName = params.getString("Store");
                String productName = params.getString("Product");
                int delta = params.getInt("Amount");

                Integer storeId = MasterServer.storeNameToId.get(storeName);
                if (storeId == null) {
                    sendResponse(new CustomMessage("ERROR",
                            new JSONObject().put("message", "Unknown store: " + storeName), null, null), socket);
                    return;
                }

                int workerId = MasterServer.hash(storeId);

                CustomMessage workerMsg = new CustomMessage(action,
                        new JSONObject()
                                .put("restaurantId", storeId)
                                .put("Product", productName)
                                .put("Amount", delta),
                        null, null);

                Object raw = MasterServer.sendMessageExpectReply(workerMsg, workerId);

                if (raw instanceof CustomMessage cm && cm.getAction().equals("ACK")) {
                    sendResponse(new CustomMessage("ACK", new JSONObject(), null, null), socket);
                } else {
                    sendResponse(new CustomMessage("ERROR", new JSONObject(), null, null), socket);
                }
                clientSocket.close();
                break;
            }

            case "TotalSales": {
                String storeName = params.getString("Store");
                Integer storeId = MasterServer.storeNameToId.get(storeName);
                if (storeId == null) {
                    sendResponse(new CustomMessage("ERROR",
                            new JSONObject().put("message", "Unknown store: " + storeName), null, null), socket);
                    return;
                }

                int wid = MasterServer.hash(storeId);
                params.put("restaurantId", storeId);
                CustomMessage wm = new CustomMessage("TotalSales", params, null, null);
                Object raw = MasterServer.sendMessageExpectReply(wm, wid);

                if (raw instanceof CustomMessage cm && cm.getAction().equals("ACK")) {
                    sendResponse(new CustomMessage("ACK", cm.getParameters(), null, null), socket);
                } else {
                    sendResponse(new CustomMessage("ERROR", new JSONObject(), null, null), socket);
                }
                clientSocket.close();
                break;
            }

            // Cases that handle reduce message requests
            case "TotalSalesProductType":
            case "TotalSalesStoreCategory":
            case "Search": {
                int mapID = MasterServer.getNextMapId();
                params.put("MapID", mapID);

                synchronized (MasterServer.streamMapLock) {
                    MasterServer.streamMap.put(mapID, socket);
                }

                MasterServer.broadcastMessageToWorkers(new CustomMessage(action, params, null, null));
                break;
            }

            case "Buy": {
                String storeName = msg.getParameters().getString("Store");
                Integer storeId = MasterServer.storeNameToId.get(storeName);
                if (storeId == null) {
                    sendResponse(new CustomMessage("ERROR",
                            new JSONObject().put("message", "Unknown store: " + storeName), null, null), socket);
                    return;
                }

                JSONObject newParams = msg.getParameters().put("restaurantId", storeId);
                int workerId = MasterServer.hash(storeId);
                CustomMessage workerMsg = new CustomMessage("Buy", newParams, null, null);
                Object raw = MasterServer.sendMessageExpectReply(workerMsg, workerId);

                if (raw instanceof CustomMessage cm && cm.getAction().equals("ACK")) {
                    sendResponse(new CustomMessage("ACK", new JSONObject(), null, null), socket);
                } else {
                    sendResponse(new CustomMessage("ERROR", new JSONObject(), null, null), socket);
                }
                clientSocket.close();
                break;
            }

            case "Rate": {
                String storeName = msg.getParameters().getString("Store");
                Integer storeId = MasterServer.storeNameToId.get(storeName);
                if (storeId == null) {
                    sendResponse(new CustomMessage("ERROR",
                            new JSONObject().put("message", "Unknown store: " + storeName), null, null), socket);
                    return;
                }

                JSONObject newParams = msg.getParameters().put("restaurantId", storeId);
                int workerId = MasterServer.hash(storeId);
                CustomMessage workerMsg = new CustomMessage("Rate", newParams, null, null);
                Object raw = MasterServer.sendMessageExpectReply(workerMsg, workerId);

                if (raw instanceof CustomMessage cm && cm.getAction().equals("ACK")) {
                    sendResponse(new CustomMessage("ACK", new JSONObject(), null, null), socket);
                } else {
                    sendResponse(new CustomMessage("ERROR", new JSONObject(), null, null), socket);
                }
                clientSocket.close();
                break;
            }

            default: {
                sendResponse(new CustomMessage("ERROR", null, null, null), socket);
            }
        }
    }

    // Sends a response back to the client
    private void sendResponse(CustomMessage message, ObjectOutputStream shout) throws IOException {
        shout.writeObject(message);
        shout.flush();
    }
}
