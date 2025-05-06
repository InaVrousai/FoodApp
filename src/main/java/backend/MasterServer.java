package backend;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONObject;


public class MasterServer {
    // Global list of workers and MapReduce results (can later be replaced by a dedicated MapReduce object)
    protected static final List<WorkerInfo> workers = new ArrayList<>();

    protected static final Map<String,Integer> storeNameToId = new HashMap<>();
    //used for pending requests
    protected static final Map<Integer, Socket> socketMap = new HashMap<>();

    // For order ID generation with thread-safe incrementation
    private static int restaurantId = 0;
    private static int mapID = 0;
    //used for synchronisation
    private static final Object restaurantIdSyncObj = new Object();
    private static final Object mapIdSyncObj = new Object();
    protected static final Object socketMapLock = new Object();

    public static int getNextRestaurantId() {
        synchronized (restaurantIdSyncObj) {
            return restaurantId++;
        }
    }
    //creates synchronized a unique mapID
    public static int getNextMapId() {
        synchronized (mapIdSyncObj) {
            return mapID++;
        }
    }

    protected static int hash(int restaurantId) {
        int numOfWorkers = workers.size();
        if (numOfWorkers == 0){
            throw new IllegalStateException("No workers available");
        }

        double A = 0.618033;  // Knuth's constant
        double product = restaurantId * A;
        double fractionalPart = product - Math.floor(product);
        return (int) (numOfWorkers * fractionalPart);
    }

    protected static CustomMessage sendMessageExpectReply(Object msg, int workerId) {
        WorkerInfo worker = workers.get(workerId);
        try (Socket workerSocket = new Socket(worker.getAddress(), worker.getPort());
             ObjectOutputStream out = new ObjectOutputStream(workerSocket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(workerSocket.getInputStream())) {

            out.writeObject(msg);
            out.flush();
            return (CustomMessage) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error communicating with worker ID " + worker.getId());
            return null;
        }
    }

    protected static void sendMessageToWorker(Object msg, int workerId) {
        WorkerInfo worker = workers.get(workerId);
        try (Socket workerSocket = new Socket(worker.getAddress(), worker.getPort());
             ObjectOutputStream out = new ObjectOutputStream(workerSocket.getOutputStream())) {

            out.writeObject(msg);
            out.flush();
        } catch (IOException e) {
            System.err.println("Error sending to worker ID " + worker.getId());
        }
    }


//    protected static Object sendMessageToWorkerWithRetry(Object msg, int workerId, int retries) {
//        int attempts = 0;
//        while (attempts < retries) {
//            Object response = sendMessageExpectReply(msg, workerId);
//            if (response != null) {
//                return response;
//            }
//            attempts++;
//            System.err.println("Retrying (" + attempts + "/" + retries + ") to worker " + workerId);
//            try {
//                Thread.sleep(300);
//            } catch (InterruptedException ignored) {}
//        }
//        return null;

//    }
    protected static void broadcastMessageToWorkers(CustomMessage msg) {
        for (WorkerInfo worker : workers) {
            sendMessageToWorker(msg, worker.getId());
        }
    }

//    private static void seedInitialStores() {
//        System.out.println("[Setup] Seeding initial stores…");
//        JasonParser parser = new JasonParser();
//
//        // 1) figure out where your JSON lives
//        String dataDir = System.getProperty("user.dir") + File.separator + "data";
//        File dir = new File(dataDir);
//        if (!dir.exists() || !dir.isDirectory()) {
//            System.err.println("[Setup] Data directory not found: " + dataDir);
//            return;
//        }
//
//        // 2) grab every .json file
//        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".json"));
//        if (files == null || files.length == 0) {
//            System.err.println("[Setup] No JSON files found in: " + dataDir);
//            return;
//        }
//
//        // 3) load & send each store
//        for (File file : files) {
//            try {
//                System.out.println("[Setup] Loading store from: " + file.getName());
//                Store store = parser.jsonReader(file.getAbsolutePath());
//
//                // assign a new ID
//                int id = getNextRestaurantId();
//                store.setId(id);
//                storeNameToId.put(store.getStoreName(), id);
//
//                // forward to the hashed worker
//                int workerId = hash(id);
//                CustomMessage msg = new CustomMessage("AddStore", new JSONObject(), store, null);
//                Object resp = sendMessageExpectReply(msg, workerId);
//
//                if (resp instanceof CustomMessage cm && "ACK".equals(cm.getAction())) {
//                    System.out.println("[Setup] Added `" + store.getStoreName()
//                            + "` (ID=" + id + ") to worker " + workerId);
//                } else {
//                    System.err.println("[Setup] Failed to add `" + store.getStoreName() + "`");
//                }
//            } catch (Exception e) {
//                System.err.println("[Setup] Error reading " + file.getName() + ": " + e.getMessage());
//            }
//        }
//    }


    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java MasterServer <num_of_workers>");
            System.exit(1);
        }

        int numOfWorkers = Integer.parseInt(args[0]);

        // Start the server to accept connections from clients
        try (ServerSocket serverSocket = new ServerSocket(5000, 10)) {
            serverSocket.setReuseAddress(true);
            System.out.println("MasterServer is running on port 5000...");

            // Listen to incoming worker connections
            for (int i = 0; i < numOfWorkers; i++) {
                Socket workerSocket = serverSocket.accept();
                String workerAddress = "localhost";
                System.out.printf("\n> Worker:%s connected.%n", workerAddress);
                try (DataInputStream workerSocketIn = new DataInputStream(workerSocket.getInputStream())) {
                    workers.add(new WorkerInfo(i, workerAddress, 5001+i));
                } catch (IOException e) {
                    System.err.println("\n! Server.main(): Failed to read port from Worker: " + workerAddress);
                }
            }

            //seedInitialStores();

//            try (ServerSocket reducerSocket = new ServerSocket(6000)) {
//                System.out.println("MasterServer listening for reducer messages on port 6000...");
//                while (true) {
//                    // Accept connections from Reducer
//                    Socket reducerSocketClient = reducerSocket.accept();
//                    System.out.println("> Reducer connected: " + reducerSocketClient.getInetAddress().getHostAddress());
//
//                    // Handle the reducer's message in a separate thread using MasterHandler or custom logic
//                    new Thread(() -> {
//                        // Handle reducer messages in a separate thread
//                        MasterHandler reducerHandler = new MasterHandler(reducerSocketClient);
//                        new Thread(reducerHandler).start();
//                    }).start();
//                }
//
//            } catch (IOException e) {
//                System.err.println("Error in reducer listener: " + e.getMessage());
            //}


            while (true) {
                Socket masterHandler = serverSocket.accept();
                System.out.println("\n> New client connected: " + masterHandler.getInetAddress().getHostAddress());
                MasterHandler masterHandlerThread = new MasterHandler(masterHandler);
                new Thread(masterHandlerThread).start();
            }


        } catch (IOException e) {
            System.err.println("Error starting server: " + e.getMessage());
        }
    }
}