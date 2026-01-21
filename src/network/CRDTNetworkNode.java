package network;

import crdt.Operation;
import crdt.RGAReplica;
import javafx.application.Platform;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.LinkedList;
import java.util.Queue;

public class CRDTNetworkNode {
    private final int PORT = 9999;
    private String HOST = "localhost";
    private Socket socket;
    private final RGAReplica replica;
    private ObjectOutputStream out;
    private final Runnable uiRefresh;
    private final Queue<Operation> pendingOps = new LinkedList<>();
    private volatile boolean connected = false;
    private final Runnable conRefresh;

    public CRDTNetworkNode(RGAReplica replica, Runnable uiRefresh, Runnable conRefresh) {
        this.replica = replica;
        this.uiRefresh = uiRefresh;
        this.conRefresh = conRefresh;
    }

    public void connect(String ip) {
        if(!ip.isEmpty()) HOST = ip;
        try {
            this.socket = new Socket(HOST, PORT);
            out = new ObjectOutputStream(socket.getOutputStream());
            System.out.println(" connected to the servet at port" + PORT);
        } catch (IOException e) {
            System.out.println("No server found");
        }
        new Thread(this::listen).start();
    }

    public void listen(){
        try {
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            connected = true;
            RGAReplica other = (RGAReplica)in.readObject();
            replica.replaceWith(other);
            while(!pendingOps.isEmpty()){
                Operation c = pendingOps.poll();
                c.applyTo(replica);
                send(c);
            }

            Platform.runLater(uiRefresh);

            while(connected){
                Operation op = (Operation) in.readObject();
                op.applyTo(replica);
//                System.out.println(op.getClass() + " some ops received " + replica.getText());
                Platform.runLater(uiRefresh);
            }
            Platform.runLater(conRefresh);
        }catch (Exception e){
            System.out.println("server stopped responding ");
            Platform.runLater(conRefresh);
        }
    }

    public void send(Operation op){
        if (!isConnected()) {
            pendingOps.add(op);
            return;
        }

        try {
            out.writeObject(op);
            out.flush();
        } catch (IOException e) {
            pendingOps.add(op); // fallback
        }
    }


    private boolean isConnected(){
        return out != null;
    }

    public void disconnect() {
        connected = false;
        try {
            if (out != null) {
                out.close();
            }
        } catch (IOException e) {
            System.out.println("Problem while disconnecting");
        }

        System.out.println("Disconnected from server");
    }
}
