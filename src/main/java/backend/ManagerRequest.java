package backend;

import java.io.*;
import java.net.*;

public class ManagerRequest {

    public CustomMessage sendRequest(CustomMessage message) {
        CustomMessage response;
        try (Socket managerSocket = new Socket("192.168.1.7", 5000);
             ObjectOutputStream out = new ObjectOutputStream(managerSocket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(managerSocket.getInputStream())) {

            System.out.println("Manager connected");

            // Send the message to the server
            out.writeObject(message);
            out.flush();

            // Read the server's response and return it
            response = (CustomMessage) in.readObject();
            return response;

        } catch (Exception e) {
            System.out.println(e);
        }

        // If an error occurs, return a negative acknowledgment
        return new CustomMessage("NACK", null, null, null);
    }

}
