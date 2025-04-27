package main.java.backend;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONObject;


public class MasterServer {
    // Global list of workers and MapReduce results (can later be replaced by a dedicated MapReduce object)
    protected static final List<WorkerInfo> workers = new ArrayList<>();

    protected static final Map<String,Integer> storeNameToId = new ConcurrentHashMap<>();

    // For order ID generation with thread-safe incrementation
    private static int restaurantId = 0;
    private static int productId = 0;
    private static int mapId = 0;

    private static final Object restaurantIdSyncObj = new Object();
    private static final Object productIdSyncObj = new Object();
    private static final Object mapIdSyncObj = new Object();

    public static int getNextRestaurantId() {
        synchronized (restaurantIdSyncObj) {
            return restaurantId++;
        }
    }
    public static int getNextProductId() {
        synchronized (productIdSyncObj) {
            return productId++;
        }
    }
//    public static int getNextMapId() {
//        synchronized (mapIdSyncObj) {
//            return mapId++;
//        }
//    }


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

    protected static Object sendMessageExpectReply(Object msg, int workerId) {
        WorkerInfo worker = workers.get(workerId);
        try (Socket workerSocket = new Socket(worker.getAddress(), worker.getPort());
             ObjectOutputStream out = new ObjectOutputStream(workerSocket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(workerSocket.getInputStream())) {

            out.writeObject(msg);
            out.flush();
            return in.readObject();
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

    protected static void broadcastMessageToWorkers(Object msg) {
        for (WorkerInfo worker : workers) {
            sendMessageToWorker(msg, worker.getId());
        }
    }

    protected static boolean broadcastMessageAndWaitForAck(Object msg) {
        for (WorkerInfo worker : workers) {
            Object response = sendMessageExpectReply(msg, worker.getId());
            if (response == null || !(response instanceof String) || !((String) response).equalsIgnoreCase("ACK")) {
                System.err.println("Worker " + worker.getId() + " failed to acknowledge.");
                return false;
            }
        }
        return true;
    }



    protected static Map<Integer, Object> broadcastAndCollectResponses(Object msg) {
        Map<Integer, Object> responses = new HashMap<>();
        for (WorkerInfo worker : workers) {
            Object response = sendMessageExpectReply(msg, worker.getId());
            responses.put(worker.getId(), response);
        }
        return responses;
    }

    private static void seedInitialStores() {
        System.out.println("[Setup] Seeding initial stores…");
        JasonParser parser = new JasonParser();

        // 1) figure out where your JSON lives
        String dataDir = System.getProperty("user.dir") + File.separator + "data";
        File dir = new File(dataDir);
        if (!dir.exists() || !dir.isDirectory()) {
            System.err.println("[Setup] Data directory not found: " + dataDir);
            return;
        }

        // 2) grab every .json file
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".json"));
        if (files == null || files.length == 0) {
            System.err.println("[Setup] No JSON files found in: " + dataDir);
            return;
        }

        // 3) load & send each store
        for (File file : files) {
            try {
                System.out.println("[Setup] Loading store from: " + file.getName());
                Store store = parser.jsonReader(file.getAbsolutePath());

                // assign a new ID
                int id = getNextRestaurantId();
                store.setId(id);
                storeNameToId.put(store.getStoreName(), id);

                // forward to the hashed worker
                int workerId = hash(id);
                CustomMessage msg = new CustomMessage("AddStore", new JSONObject(), store, null);
                Object resp = sendMessageExpectReply(msg, workerId);

                if (resp instanceof CustomMessage cm && "ACK".equals(cm.getAction())) {
                    System.out.println("[Setup] Added `" + store.getStoreName()
                            + "` (ID=" + id + ") to worker " + workerId);
                } else {
                    System.err.println("[Setup] Failed to add `" + store.getStoreName() + "`");
                }
            } catch (Exception e) {
                System.err.println("[Setup] Error reading " + file.getName() + ": " + e.getMessage());
            }
        }
    }


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

            seedInitialStores();

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("\n> New client connected: " + clientSocket.getInetAddress().getHostAddress());
                ClientHandler clientThread = new ClientHandler(clientSocket);
                new Thread(clientThread).start();
            }



        } catch (IOException e) {
            System.err.println("Error starting server: " + e.getMessage());
        }
    }


}
