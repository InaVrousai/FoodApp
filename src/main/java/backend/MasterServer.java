package backend;

import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.util.*;


public class MasterServer {
    // Global list of worker nodes and stream tracking for MapReduce responses
    protected static final List<WorkerInfo> workers = new ArrayList<>();

    protected static final Map<String, Integer> storeNameToId = new HashMap<>();

    // Used for tracking pending responses by MapID
    protected static final Map<Integer, ObjectOutputStream> streamMap = new HashMap<>();

    // Thread-safe ID generation
    private static int restaurantId = 0;
    private static int mapID = 0;

    // Synchronization locks
    private static final Object restaurantIdSyncObj = new Object();
    private static final Object mapIdSyncObj = new Object();
    protected static final Object streamMapLock = new Object();

    // Generate a unique store ID
    public static int getNextRestaurantId() {
        synchronized (restaurantIdSyncObj) {
            return restaurantId++;
        }
    }

    // Generate a unique MapID for reducer coordination
    public static int getNextMapId() {
        synchronized (mapIdSyncObj) {
            return mapID++;
        }
    }

    // Consistent hashing function for store-to-worker assignment
    protected static int hash(int restaurantId) {
        int numOfWorkers = workers.size();
        if (numOfWorkers == 0) {
            throw new IllegalStateException("No workers available");
        }

        double A = 0.618033;  // Knuth's constant
        double product = restaurantId * A;
        double fractionalPart = product - Math.floor(product);
        return (int) (numOfWorkers * fractionalPart);
    }

    // Send a message to a specific worker and wait for the response
    protected static CustomMessage sendMessageExpectReply(CustomMessage msg, int workerId) {
        WorkerInfo worker = workers.get(workerId);
        try (Socket workerSocket = new Socket(worker.getAddress(), worker.getPort());
             ObjectOutputStream out = new ObjectOutputStream(workerSocket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(workerSocket.getInputStream())) {

            System.out.println("MasterServer sending to Worker " + workerId + ": " + msg.getAction());
            out.writeObject(msg);
            out.flush();
            return (CustomMessage) in.readObject();

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error communicating with worker ID " + worker.getId());
            return null;
        }
    }

    // Send a message to a specific worker without expecting a reply
    protected static void sendMessageToWorker(Object msg, int workerId) {
        WorkerInfo worker = workers.get(workerId);
        try (Socket workerSocket = new Socket(worker.getAddress(), worker.getPort());
             ObjectOutputStream out = new ObjectOutputStream(workerSocket.getOutputStream())) {

            System.out.println("Sending message to worker ID: " + worker.getId());
            out.writeObject(msg);
            out.flush();

        } catch (IOException e) {
            System.err.println("Error sending to worker ID " + worker.getId());
        }
    }

    // Broadcast a message to all workers (typically for MapReduce operations)
    protected static void broadcastMessageToWorkers(CustomMessage msg) {
        for (WorkerInfo worker : workers) {
            sendMessageExpectReply(msg, worker.getId());
        }
    }

    // Preload initial stores from JSON files in the /data directory
    private static void seedInitialStores() {
        System.out.println("[Setup] Seeding initial stores…");
        JasonParser parser = new JasonParser();

        String dataDir = System.getProperty("user.dir") + File.separator + "data";
        File dir = new File(dataDir);
        if (!dir.exists() || !dir.isDirectory()) {
            System.err.println("[Setup] Data directory not found: " + dataDir);
            return;
        }

        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".json"));
        if (files == null || files.length == 0) {
            System.err.println("[Setup] No JSON files found in: " + dataDir);
            return;
        }

        for (File file : files) {
            try {
                System.out.println("[Setup] Loading store from: " + file.getName());
                Store store = parser.jsonReader(file.getAbsolutePath());

                int id = getNextRestaurantId();
                store.setId(id);
                storeNameToId.put(store.getStoreName(), id);

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

        try (ServerSocket serverSocket = new ServerSocket(5000, 10)) {
            serverSocket.setReuseAddress(true);
            System.out.println("MasterServer is running on port 5000...");

            // Accept initial connections from worker nodes
            for (int i = 0; i < numOfWorkers; i++) {
                Socket workerSocket = serverSocket.accept();
                String workerAddress = "192.168.1.7"; // Hardcoded for simplicity
                System.out.printf("\n> Worker:%s connected.%n", workerAddress);
                try (DataInputStream workerSocketIn = new DataInputStream(workerSocket.getInputStream())) {
                    workers.add(new WorkerInfo(i, workerAddress, 5001 + i));
                } catch (IOException e) {
                    System.err.println("\nServer.main(): Failed to read port from Worker: " + workerAddress);
                }
            }

            // Start a thread to handle incoming reducer responses
            new Thread(() -> {
                try (ServerSocket reducerSocket = new ServerSocket(7000)) {
                    while (true) {
                        Socket s = reducerSocket.accept();
                        MasterReducerHandler handler = new MasterReducerHandler(s);
                        new Thread(handler).start();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();

            // Load and distribute initial stores
            seedInitialStores();

            // Main loop: accept and handle incoming client requests
            while (true) {
                Socket masterHandler = serverSocket.accept();
                System.out.println("\n> New client connected: " + masterHandler.getInetAddress().getHostAddress());
                MasterHandler handler = new MasterHandler(masterHandler);
                new Thread(handler).start();
            }

        } catch (IOException e) {
            System.err.println("Error starting server: " + e.getMessage());
        }
    }
}
