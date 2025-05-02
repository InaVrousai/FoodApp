package backend;
import org.json.JSONObject;
import java.io.*;
import java.net.Socket;

public class MasterHandler implements Runnable {
    private final Socket masterHandler;


    public MasterHandler(Socket masterHandler) {
        this.masterHandler = masterHandler;
    }

    @Override
    public void run() {
        try (
                ObjectOutputStream out = new ObjectOutputStream(masterHandler.getOutputStream());
        ) {
            out.flush();  // ensure header is sent
            try (ObjectInputStream in = new ObjectInputStream(masterHandler.getInputStream())) {
                Object received = in.readObject();
                if (!(received instanceof CustomMessage)) {
                    out.writeObject(new CustomMessage("error",
                            new JSONObject().put("message", "Invalid message type"),null,null));
                    return;
                }

                CustomMessage request = (CustomMessage) received;
                System.out.println("Received action: " + request.getAction());
                System.out.println("Payload: " + request.getParameters().toString());
                //call handle action that handles the request
                handleAction(request,masterHandler);
                //out.writeObject(response);
            }
        } catch (Exception e) {
            System.err.println("Error handling client: " + e.getMessage());
            try (ObjectOutputStream out = new ObjectOutputStream(masterHandler.getOutputStream())) {
                out.writeObject(new CustomMessage("error",
                        new JSONObject().put("message", "Server error: " + e.getMessage()),null, null));
            } catch (IOException ex) {
                System.err.println("Error sending error message to client: " + ex.getMessage());
            }
        } finally {
            try {
                System.out.println("Closing client connection.");
                masterHandler.close();
            } catch (IOException e) {
                System.err.println("Failed to close client socket: " + e.getMessage());
            }
        }
    }

    private void handleAction(CustomMessage msg,Socket socket ) throws Exception {
        String action = msg.getAction();
        JSONObject params = msg.getParameters();

        switch (action) {
            case "AddStore": {
                // fetch the Store object from the message
                Store store = msg.getStore();
                String storeName = store.getStoreName();

                // assign a new ID and set on the Store
                int storeId = MasterServer.getNextRestaurantId();
                store.setId(storeId);

                MasterServer.storeNameToId.put(storeName, storeId);

                // select worker via consistent hashing
                int workerId = MasterServer.hash(storeId);

                // forward to the chosen worker
                CustomMessage workerMsg = new CustomMessage("AddStore",null, store, null
                );
                CustomMessage workerResponse = MasterServer.sendMessageExpectReply(workerMsg, workerId);

                try(ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
                    out.writeObject(workerResponse);
                }
                break;
            }
            case "AddProduct":{
                // extract store name
                String storeName = msg.getParameters().getString("StoreName");
                Product prod = msg.getProduct();

                // 2) pick worker by storeName hash
                Integer storeId = MasterServer.storeNameToId.get(storeName);

                if (storeId == null) {
                    try(ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
                        out.writeObject(new CustomMessage("ERROR",
                                new JSONObject().put("message", "Unknown store: " + storeName),
                                null, null));
                    }
                    break;
                }
                //find store id using the hash
                int workerId = MasterServer.hash(storeId);

                CustomMessage workerMsg = new CustomMessage("AddProduct",
                        new JSONObject().put("restaurantId", storeId),
                        null,
                        msg.getProduct());
                CustomMessage cstMessage = MasterServer.sendMessageExpectReply(workerMsg, workerId);

                try(ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
                    out.writeObject(cstMessage);
                }
                break;
            }
            case "RemoveProduct": {
                // extract store name
                String storeName = msg.getParameters().getString("StoreName");
                Product prod = msg.getProduct();

                // 2) look up the storeId
                Integer storeId = MasterServer.storeNameToId.get(storeName);

                if (storeId == null) {
                    try(ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
                        out.writeObject(new CustomMessage("ERROR",
                                new JSONObject().put("message", "Unknown store: " + storeName),
                                null, null));
                    }
                    break;
                }
                //stores the restaurantID in the existent json
                params.put("restaurantId",storeId);
                // pick the worker
                int workerId = MasterServer.hash(storeId);
                CustomMessage cstMessage = new CustomMessage("RemoveProduct",params, null, null
                );

                try(ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
                    out.writeObject(cstMessage);
                }
                break;
            }
            case "IncreaseProductAmount": {
                // extract store name
                String storeName = msg.getParameters().getString("StoreName");
                Product prod = msg.getProduct();

                // 2) look up the storeId
                Integer storeId = MasterServer.storeNameToId.get(storeName);

                if (storeId == null) {
                    try(ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
                        out.writeObject(new CustomMessage("ERROR",
                                new JSONObject().put("message", "Unknown store: " + storeName),
                                null, null));
                    }
                    break;
                }
                //stores the restaurantID in the existent json
                params.put("restaurantId", storeId);
                // pick the worker
                int workerId = MasterServer.hash(storeId);
                CustomMessage workerMsg = new CustomMessage("IncreaseProductAmount", params, null, null
                );
                Object cstMessage = MasterServer.sendMessageExpectReply(workerMsg, workerId);


                try(ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
                    out.writeObject(cstMessage);
                }
                break;
            }
            case "DecreaseProductAmount": {
                // extract store name
                String storeName = msg.getParameters().getString("StoreName");
                Product prod = msg.getProduct();

                // 2) look up the storeId
                Integer storeId = MasterServer.storeNameToId.get(storeName);

                if (storeId == null) {
                    try(ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
                        out.writeObject(new CustomMessage("ERROR",
                                new JSONObject().put("message", "Unknown store: " + storeName),
                                null, null));
                    }
                    break;
                }
                //stores the restaurantID in the existent json
                params.put("restaurantId", storeId);
                // pick the worker
                int workerId = MasterServer.hash(storeId);
                CustomMessage workerMsg = new CustomMessage("DecreaseProductAmount", params, null, null
                );
                CustomMessage cstMessage = MasterServer.sendMessageExpectReply(workerMsg, workerId);

                try(ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
                    out.writeObject(cstMessage);
                }
                break;

            }
            case "TotalSales": {
                // extract store name
                String storeName = msg.getParameters().getString("StoreName");
                Product prod = msg.getProduct();

                // 2) look up the storeId
                Integer storeId = MasterServer.storeNameToId.get(storeName);

                if (storeId == null) {
                    try(ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
                        out.writeObject(new CustomMessage("ERROR",
                                new JSONObject().put("message", "Unknown store: " + storeName),
                                null, null));
                    }
                    break;
                }
                //stores the restaurantID in the existent json
                params.put("restaurantId", storeId);
                // pick the worker
                int workerId = MasterServer.hash(storeId);
                CustomMessage workerMsg = new CustomMessage("TotalSales", params, null, null
                );

                CustomMessage cstMessage = MasterServer.sendMessageExpectReply(workerMsg, workerId);

                try(ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
                    out.writeObject(cstMessage);
                }
                break;

            }
            //cases that handle reduce messages requests
            case "TotalSalesProductType":{
                // assign a new mapID and insert it to the jason
                int mapID = MasterServer.getNextMapId();
                params.put("MapID",mapID);
                //locking socket map to prevent race conditions
                synchronized (MasterServer.socketMapLock){
                    MasterServer.socketMap.put(mapID,socket);
                }
                //broadcasts the message to the workers
                MasterServer.broadcastMessageToWorkers(new CustomMessage("TotalSalesProductType",params,null,null));
                break;
            }
            case "TotalSalesStoreCategory":{
                // assign a new mapID and insert it to the jason
                int mapID = MasterServer.getNextMapId();
                params.put("MapID",mapID);
                //locking socket map to prevent race conditions
                synchronized (MasterServer.socketMapLock){
                    MasterServer.socketMap.put(mapID,socket);
                }
                //broadcasts the message to the workers
                MasterServer.broadcastMessageToWorkers(new CustomMessage("TotalSalesStoreCategory",params,null,null));
                break;
            }

            case "Search":{
                // assign a new mapID and insert it to the jason
                int mapID = MasterServer.getNextMapId();
                params.put("MapID",mapID);
                //locking socket map to prevent race conditions
                synchronized (MasterServer.socketMapLock){
                    MasterServer.socketMap.put(mapID,socket);
                }
                //broadcasts the message to the workers
                MasterServer.broadcastMessageToWorkers(new CustomMessage("Search",params,null,null));
                break;
            }
            case "Buy":{
                String storeName = msg.getParameters().getString("StoreName");
                // look up the storeId
                Integer storeId = MasterServer.storeNameToId.get(storeName);


                if (storeId == null) {
                    try(ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
                        out.writeObject(new CustomMessage("ERROR",
                                new JSONObject().put("message", "Unknown store: " + storeName),
                                null, null));
                    }
                    break;
                }
                //stores the restaurantID in the existent json
                params.put("restaurantId",storeId);
                // pick the worker
                int workerId = MasterServer.hash(storeId);
                CustomMessage workerMsg = new CustomMessage("Buy",params, null, null
                );
                try(ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
                    out.writeObject(workerMsg);
                }
                break;

            }
            case "Rate": {
                //get store name from json
                String storeName = msg.getParameters().getString("StoreName");
                // look up the storeId
                Integer storeId = MasterServer.storeNameToId.get(storeName);

                if (storeId == null) {
                    try(ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
                        out.writeObject(new CustomMessage("ERROR",
                                new JSONObject().put("message", "Unknown store: " + storeName),
                                null, null));
                    }
                    break;
                }
                //stores the restaurantID in the existent json
                params.put("restaurantId",storeId);
                // pick the worker
                int workerId = MasterServer.hash(storeId);
                CustomMessage workerMsg = new CustomMessage("Rate",params, null, null
                );
                try(ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
                    out.writeObject(workerMsg);
                }
                break;
            }
            //cases that handle reduced messages
            case "ReducedSearch":{
                Socket socketClient;
                //locking socket map to prevent race conditions
                synchronized (MasterServer.socketMapLock){
                    socketClient = MasterServer.socketMap.get(params.getInt("MapID"));
                    // Clean up data structures for this MapID
                    MasterServer.socketMap.remove(params.getInt("MapID"));
                }
                if (socketClient == null) {
                    System.err.println("MapID not found or socket already removed: " + params.getInt("MapID"));
                    break; // Cannot respond, so we just exit the case
                }

                try(ObjectOutputStream out = new ObjectOutputStream(socketClient.getOutputStream())) {
                    out.writeObject(msg);
               }
                break;

            }case"ReducedTotalSales": {
                Socket managerSocket;
                //locking socket map to prevent race conditions
                synchronized (MasterServer.socketMapLock) {
                    managerSocket = MasterServer.socketMap.get(params.getInt("MapID"));
                    // Clean up data structures for this MapID
                    MasterServer.socketMap.remove(params.getInt("MapID"));
                }
                //if socket is null
                if (managerSocket == null) {
                    System.err.println("MapID not found or socket already removed: " + params.getInt("MapID"));
                    break; // Cannot respond, so we just exit the case
                    }

                    try (ObjectOutputStream out = new ObjectOutputStream(managerSocket.getOutputStream())) {
                        out.writeObject(msg);
                    }
                    break;

            }default: {
                try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
                    out.writeObject(new CustomMessage(
                            "ERROR",
                            new JSONObject().put("message", "Unknown action: " + action),
                            null,
                            null));
                }
                break;
            }
        }
    }
}