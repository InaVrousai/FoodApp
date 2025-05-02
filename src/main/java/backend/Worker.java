package backend;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Worker{

    protected  static ArrayList<Store> storesList = new ArrayList<>();
    protected  static  Map<Integer, Store> storeMap = new ConcurrentHashMap<>(); //is used to find store quickly

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: java Worker <worker_id> <port>");
            System.exit(1);
        }

        int workerId = Integer.parseInt(args[0]);
        int port = Integer.parseInt(args[1]);

        try (ServerSocket workerSocket = new ServerSocket(port, 10)) {
            workerSocket.setReuseAddress(true);
            System.out.println("Worker " + workerId + " is running on port " + port);


            // Send connection information to the server (Master)
            // by sending the port number on which the worker is listening.
            try (Socket serverSocket = new Socket("localhost", 5000)) {
                DataOutputStream serverSocketOutput = new DataOutputStream(serverSocket.getOutputStream());
                serverSocketOutput.writeUTF(String.valueOf(port));
                serverSocketOutput.flush();
                System.out.println("Sent port " + port + " to server (" + "localhost" + ":" + 5000 + ")");
            } catch (IOException e) {
                System.err.println("Error connecting to the server: " + e.getMessage());
                // You can choose whether to exit or continue running.
            }

            // Accept connections from the Master and create a new thread for each request.
            while (true) {
                Socket masterSocket = workerSocket.accept();
                WorkerAction requestThread = new WorkerAction(masterSocket);
                new Thread(requestThread).start();
            }
        } catch (IOException e) {
            System.err.println("\n! Worker.main(): Error:\n" + e);
            e.printStackTrace();
        }
    }
}