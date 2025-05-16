package backend;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ReducerServer implements Runnable {
    private static final Object lock = new Object(); // Used for synchronization
    private final Socket socket;

    // Map to store workers' intermediate results by MapID
    private static final Map<Integer, ArrayList<CustomMessage>> intermediateResults = new HashMap<>();

    // Map to track how many worker results have been received for each MapID
    private static final Map<Integer, Integer> resultsCount = new HashMap<>();

    private static int numberOfWorkers;

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java ReducerServer <number_of_workers>");
            System.exit(1);
        }

        try (ServerSocket serverSocket = new ServerSocket(6000)) {
            System.out.println("Reducer is running on port 6000");

            numberOfWorkers = Integer.parseInt(args[0]);
            System.out.println("Reducer received numberOfWorkers: " + numberOfWorkers);

            // Accept incoming messages from workers
            while (true) {
                Socket masterSocket = serverSocket.accept();
                // Handle each worker message in a new thread
                new Thread(() -> handleWorkerMessage(masterSocket)).start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public ReducerServer(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        handleWorkerMessage(socket);
    }

    private static void handleWorkerMessage(Socket socket) {
        try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            Object obj = in.readObject();
            if (!(obj instanceof CustomMessage)) {
                System.err.println("Invalid message received.");
                return;
            }

            CustomMessage message = (CustomMessage) obj;
            String action = message.getAction();
            JSONObject params = message.getParameters();

            System.out.println("Received action: " + action);
            System.out.println("Payload: " + message.getJsonString());

            synchronized (lock) {
                if (action.equals("Search") || action.equals("TotalSalesStoreCategory") || action.equals("TotalSalesProductType")) {
                    int mapID = params.getInt("MapID"); // MapID carried in the message

                    // Initialize tracking for new MapID
                    if (!intermediateResults.containsKey(mapID)) {
                        intermediateResults.put(mapID, new ArrayList<>());
                        resultsCount.put(mapID, 0);
                    }

                    intermediateResults.get(mapID).add(message); // Store the message
                    resultsCount.put(mapID, resultsCount.get(mapID) + 1); // Increment count

                    // Wait until all worker responses are received
                    while (resultsCount.containsKey(mapID) && resultsCount.get(mapID) < numberOfWorkers) {
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }

                    // Check if already processed by another thread
                    if (!resultsCount.containsKey(mapID)) {
                        lock.notifyAll();
                        return;
                    }

                    // All messages received, perform reduce
                    if (resultsCount.get(mapID) == numberOfWorkers) {
                        CustomMessage reducedMessage = reduce(intermediateResults.get(mapID), action);
                        System.out.println("Reduced message: " + reducedMessage.getJsonString());

                        // Send the reduced result to the MasterServer
                        try (Socket masterSocket = new Socket("192.168.1.7", 7000);
                             ObjectOutputStream masterOut = new ObjectOutputStream(masterSocket.getOutputStream())) {
                            masterOut.writeObject(reducedMessage);
                            masterOut.flush();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        // Cleanup
                        intermediateResults.remove(mapID);
                        resultsCount.remove(mapID);
                    }
                    lock.notifyAll(); // Wake up other waiting threads
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    // Reduces a list of intermediate messages into one final result, based on the action type
    private static CustomMessage reduce(ArrayList<CustomMessage> intermediates, String action) {
        if (action.equals("Search")) {
            JSONObject reducedMessage = new JSONObject();
            JSONArray storesArray = new JSONArray();

            reducedMessage.put("MapID", intermediates.get(0).getParameters().getInt("MapID"));

            for (CustomMessage msg : intermediates) {
                if (msg.getParameters() != null) {
                    JSONArray intermediateStores = msg.getParameters().getJSONArray("IntermediateData");
                    for (int i = 0; i < intermediateStores.length(); i++) {
                        storesArray.put(intermediateStores.getJSONObject(i));
                    }
                }
            }

            reducedMessage.put("Stores", storesArray);
            return new CustomMessage("ReducedSearch", reducedMessage, null, null);

        } else {
            JSONObject reducedMessage = new JSONObject();
            JSONArray reducedArray = new JSONArray();

            reducedMessage.put("MapID", intermediates.get(0).getParameters().getInt("MapID"));

            for (CustomMessage msg : intermediates) {
                if (msg.getParameters() != null) {
                    JSONArray jArray = msg.getParameters().optJSONArray("IntermediateData");
                    if (jArray == null) continue;
                    for (int i = 0; i < jArray.length(); i++) {
                        reducedArray.put(jArray.getJSONObject(i));
                    }
                }
            }

            reducedMessage.put("Stores", reducedArray);
            return new CustomMessage("ReducedTotalSales", reducedMessage, null, null);
        }
    }
}
