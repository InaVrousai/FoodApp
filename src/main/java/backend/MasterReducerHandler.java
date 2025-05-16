package backend;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class MasterReducerHandler implements Runnable {
    private final Socket sock;
    public MasterReducerHandler(Socket s) { this.sock = s; }
    public void run() {
        try (ObjectInputStream in = new ObjectInputStream(sock.getInputStream())) {
            CustomMessage reduced = (CustomMessage) in.readObject();
            int mapID = reduced.getParameters().getInt("MapID");
            System.out.println(reduced.getJsonString());
            ObjectOutputStream client;
            synchronized(MasterServer.streamMapLock) {
                client = MasterServer.streamMap.remove(mapID);
            }
            if (client != null) {
              //  try (ObjectOutputStream out = new ObjectOutputStream(client.getOutputStream())) {
                    client.writeObject(reduced);
                    client.flush();
               // }
               // client.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
