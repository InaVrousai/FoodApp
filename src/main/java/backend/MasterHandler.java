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
        try (
                ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream())
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
                System.out.println("Payload: " + request.getJsonString());

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
                String storeName = msg.getParameters().getString("Store");


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
                if (raw instanceof CustomMessage cm && cm.getAction().equals("ACK")) {
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
                if (raw instanceof CustomMessage cm && cm.getAction().equals("ACK")) {
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

                if (raw instanceof CustomMessage cm && cm.getAction().equals("ACK")) {
                    return new CustomMessage("ACK", new JSONObject(), null, null);
                } else {
                    return new CustomMessage("ERROR", new JSONObject(), null, null);
                }
            }
           case "TotalSales": {
                String storeName = params.getString("Store");
                Integer storeId = MasterServer.storeNameToId.get(storeName);
                if (storeId == null) {
                    return new CustomMessage("ERROR",
                        new JSONObject().put("message","Unknown store: "+storeName),
                        null, null
                    );
                }
                int wid = MasterServer.hash(storeId);
                params.put("restaurantId", storeId);
                CustomMessage wm = new CustomMessage("TotalSales", params, null, null);
                Object raw = MasterServer.sendMessageExpectReply(wm, wid);

                if (raw instanceof CustomMessage cm) {
                    // forward the JSON body of the ACK
                    return new CustomMessage("ACK", cm.getParameters(), null, null);
                } else {
                    return new CustomMessage("ERROR", new JSONObject(), null, null);
                }
            }
//            //cases that handle reduce messages requests
            case "TotalSalesProductType": {
                // assign MapID
                int mapId = MasterServer.getNextMapId();
                params.put("MapID", mapId);

                // stow away this client socket for the ReducerHandler later
                synchronized (MasterServer.socketMapLock) {
                    MasterServer.socketMap.put(mapId, clientSocket);
                }

                // broadcast to all workers
                MasterServer.broadcastMessageToWorkers(
                        new CustomMessage(action, params, null, null)
                );

                // immediately ACK (the real reply will come back via MasterReducerHandler)
                return new CustomMessage("ACK", new JSONObject().put("MapID", mapId), null, null);
            }
//            }
//            case "TotalSalesStoreCategory":{
//                // assign a new mapID and insert it to the jason
//                int mapID = MasterServer.getNextMapId();
//                params.put("MapID",mapID);
//                //locking socket map to prevent race conditions
//                synchronized (MasterServer.socketMapLock){
//                    MasterServer.socketMap.put(mapID,socket);
//                }
//                //broadcasts the message to the workers
//                MasterServer.broadcastMessageToWorkers(new CustomMessage("TotalSalesStoreCategory",params,null,null));
//                break;
//            }
           case "Search": {
                // assign a new mapID and insert it to the jason
                int mapID = MasterServer.getNextMapId();
                JSONObject newParams = msg.getParameters().put("MapID", mapID);
                //locking socket map to prevent race conditions
                //synchronized (MasterServer.socketMapLock) {
                   // MasterServer.socketMap.put(mapID, objectOut);
                //}
                //broadcasts the message to the workers
               // MasterServer.broadcastMessageToWorkers(new CustomMessage("Search", newParams, null, null));
               Object raw = MasterServer.sendMessageExpectReply(new CustomMessage("Search", newParams, null, null),0);
               if (raw instanceof CustomMessage cm ) {
                   // forward the JSON body of the ACK
                   return new CustomMessage("ACK", cm.getParameters(), null, null);
               } else {
                   return new CustomMessage("ERROR", new JSONObject(), null, null);
               }
            }
            case "Buy": {
                String storeName = msg.getParameters().getString("Store");
                // look up the storeId
                Integer storeId = MasterServer.storeNameToId.get(storeName);


                if (storeId == null) {
                    return new CustomMessage("ERROR",
                            new JSONObject().put("message", "Unknown store: " + storeName),
                            null, null);
                }

                //stores the restaurantID in a new json
                JSONObject newParams = msg.getParameters().put("restaurantId", storeId);

                // pick the worker
                int workerId = MasterServer.hash(storeId);
                CustomMessage workerMsg = new CustomMessage("Buy", newParams, null, null
                );

                return MasterServer.sendMessageExpectReply(workerMsg, workerId);

            }
            case "Rate": {
                //get store name from json
                String storeName = msg.getParameters().getString("Store");
                // look up the storeId
                Integer storeId = MasterServer.storeNameToId.get(storeName);

                if (storeId == null) {
                    return new CustomMessage("ERROR",
                            new JSONObject().put("message", "Unknown store: " + storeName),
                            null, null);
                }
                //stores the restaurantID in the existent json
                //stores the restaurantID in a new json
                JSONObject newParams = msg.getParameters().put("restaurantId", storeId);

                // pick the worker
                int workerId = MasterServer.hash(storeId);
                CustomMessage workerMsg = new CustomMessage("Rate", newParams, null, null
                );

                return MasterServer.sendMessageExpectReply(workerMsg, workerId);

            }
//            //cases that handle reduced messages
//            case "ReducedSearch":{
//                Socket socketClient;
//                //locking socket map to prevent race conditions
//                synchronized (MasterServer.socketMapLock){
//                    socketClient = MasterServer.socketMap.get(params.getInt("MapID"));
//                    // Clean up data structures for this MapID
//                    MasterServer.socketMap.remove(params.getInt("MapID"));
//                }
//                if (socketClient == null) {
//                    System.err.println("MapID not found or socket already removed: " + params.getInt("MapID"));
//                    break; // Cannot respond, so we just exit the case
//                }
//
//                try(ObjectOutputStream out = new ObjectOutputStream(socketClient.getOutputStream())) {
//                    out.writeObject(msg);
//               }
//                break;
//
//            }case"ReducedTotalSales": {
//                Socket managerSocket;
//                //locking socket map to prevent race conditions
//                synchronized (MasterServer.socketMapLock) {
//                    managerSocket = MasterServer.socketMap.get(params.getInt("MapID"));
//                    // Clean up data structures for this MapID
//                    MasterServer.socketMap.remove(params.getInt("MapID"));
//                }
//                //if socket is null
//                if (managerSocket == null) {
//                    System.err.println("MapID not found or socket already removed: " + params.getInt("MapID"));
//                    break; // Cannot respond, so we just exit the case
//                    }
//
//                    try (ObjectOutputStream out = new ObjectOutputStream(managerSocket.getOutputStream())) {
//                        out.writeObject(msg);
//                    }
//                    break;}

        default: {
                return new CustomMessage("ERROR",
                        new JSONObject().put("message", "Unknown action: " + action),
                        null, null);
            }
        }
    }
}