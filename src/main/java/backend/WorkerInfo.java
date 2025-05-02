package backend;

import java.util.ArrayList;

public class WorkerInfo {
    private final int id;
    private final String address;
    private final int port;


    public WorkerInfo(int id, String address, int port) {
        this.id = id;
        this.address = address;
        this.port = port;
    }


    public int getId() {
        return id;
    }

    public String getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }
}