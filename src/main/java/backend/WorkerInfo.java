package main.java.backend;

public class WorkerInfo {
    private final int id;
    private  String address;
    private  int port;

    public WorkerInfo( int id,String address, int port) {
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
    public void setPort(int port) {
        this.port = port;
    }

    @Override
    public String toString() {
        return String.format("Worker[%s:%s]", address, port);
    }
}