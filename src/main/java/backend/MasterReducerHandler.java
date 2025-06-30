package backend;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class MasterReducerHandler implements Runnable {
    private final Socket sock;

    public MasterReducerHandler(Socket s) {
        this.sock = s;
    }

    @Override
    public void run() {
        try (ObjectInputStream in = new ObjectInputStream(sock.getInputStream())) {
            CustomMessage reduced = (CustomMessage) in.readObject();

            // Extract MapID to identify the originating client request
            int mapID = reduced.getParameters().getInt("MapID");

            System.out.println(reduced.getJsonString());

            ObjectOutputStream client;
            // Retrieve and remove the client's output stream associated with the MapID
            synchronized (MasterServer.streamMapLock) {
                client = MasterServer.streamMap.remove(mapID);
            }

            if (client != null) {
                // Send the reduced response back to the original client
                client.writeObject(reduced);
                client.flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
