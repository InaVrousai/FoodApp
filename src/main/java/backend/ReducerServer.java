package backend;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ReducerServer implements Runnable{
    private static final Object lock = new Object();//used for synchronisation
    private Socket socket;
    //map to store workers intermediate results
    private static Map<Integer, ArrayList<CustomMessage>> intermediateResults = new HashMap<>();
    //map to know if we have received messages from all the workers
    private static Map<Integer, Integer> resultsCount = new HashMap<>();

    private static  int numberOfWorkers;
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java ReducerServer <port>");
            System.exit(1);
        }

        try (ServerSocket serverSocket = new ServerSocket(6000)) {
            System.out.println("Reducer is running on port " + 6000);

            // accept Master connection and get worker count
            try (Socket masterInitSocket = serverSocket.accept();
                 DataInputStream in = new DataInputStream(masterInitSocket.getInputStream())) {

                numberOfWorkers = in.readInt();
                System.out.println("Reducer received numberOfWorkers: " + numberOfWorkers);
            }

            while (true) {
                Socket masterSocket = serverSocket.accept();
                //creates a new thread
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
        try (
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream())
        ) {
            Object obj = in.readObject();
            if (!(obj instanceof CustomMessage)) {
                System.err.println("Invalid message received.");
                return;
            }

            CustomMessage message = (CustomMessage) obj;
            String action = message.getAction();
            JSONObject params = message.getParameters();


            synchronized (lock){
                if (message.getAction().equals("Search") || message.getAction().equals("TotalSalesStoreCategory") || message.getAction().equals("TotalSalesProductType")) {
                    int mapID = params.getInt("MapID");//the mapID that the message carries
                    //if this is the first mapID we receive for a message
                    if (!intermediateResults.containsKey(mapID)) {
                        intermediateResults.put(mapID, new ArrayList<>());//if the map dosent have this mapID
                        // it creates a new array to store the intermediate messages
                        resultsCount.put(mapID, 0); //initializes the worker response counter
                    }
                    intermediateResults.get(mapID).add(message); // Store the CustomMessage
                    resultsCount.put(mapID, resultsCount.get(mapID) + 1); //increases the worker response counter


                    if (resultsCount.get(mapID) < numberOfWorkers) {
                        try {
                            lock.wait(); // waits until all worker messages arrive
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    //if all messages for a mapID arrived from workers
                    if (resultsCount.get(mapID) == numberOfWorkers) {
                        CustomMessage reducedMessage = reduce(intermediateResults.get(mapID), action);
                        out.writeObject(reducedMessage);
                        out.flush();
                        // Clean up data structures for this MapID
                        intermediateResults.remove(mapID);
                        resultsCount.remove(mapID);
                    }
                    lock.notifyAll();//wakes up threads
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
    //Takes an arrayList full of custom messages and reduces based on the action
    private static CustomMessage reduce(ArrayList<CustomMessage> intermediates,String action) {

        if (action.equals("Search")) {
            JSONObject reducedMessage = new JSONObject(); // main wrapper
            JSONArray storesArray = new JSONArray(); // collect all store JSONObjects
            reducedMessage.put("MapID", intermediates.get(0).getParameters().getInt("MapID"));
            //for every CustomMessage
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
        }else{
            JSONObject reducedMessage = new JSONObject(); //stores the reduced data and mapID
            JSONArray reducedArray = new JSONArray(); //stores all the store data
            //inserts the mapID to the reduced message
            reducedMessage.put("MapID", intermediates.get(0).getParameters().getInt("MapID"));

            for (CustomMessage msg : intermediates) {
                if (msg.getParameters() != null) {
                    //A jason array that stores the jasoOobject that contains storeName totalSales
                    JSONArray jArray = msg.getParameters().getJSONArray("IntermediateData");
                    if (jArray == null)
                        continue;
                    for (int i = 0; i < jArray.length(); i++) {
                        reducedArray.put(jArray.getJSONObject(i));// inserts jsonObj in the array
                    }
                }
            }
            reducedMessage.put("Stores", reducedArray);
            return new CustomMessage("ReducedTotalSales", reducedMessage, null, null);
        }

    }


}
