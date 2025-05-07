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

            Socket client;
            synchronized(MasterServer.socketMapLock) {
                client = MasterServer.socketMap.remove(mapID);
            }
            if (client != null) {
                try (ObjectOutputStream out = new ObjectOutputStream(client.getOutputStream())) {
                    out.writeObject(reduced);
                }
                client.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
