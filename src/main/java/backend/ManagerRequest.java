package main.java.backend;

import java.io.*;
import java.net.*;

public class ManagerRequest {

    public CustomMessage sendRequest(CustomMessage message) {
        CustomMessage response ;
        try (Socket managerSocket = new Socket("localhost",5000 );
             ObjectOutputStream out = new ObjectOutputStream(managerSocket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(managerSocket.getInputStream());){

            System.out.println("Manager connected");


            //Sending the message to the server
            out.writeObject(message);
            out.flush();

            //Reading server response and returning it
            response = (CustomMessage) in.readObject();
            return response;

        } catch (Exception e) {
            System.out.println(e);
        }


        return null;
    }

}