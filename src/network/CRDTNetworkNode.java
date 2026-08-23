package network;

import crdt.Operation;
import crdt.RGAReplica;
import javafx.application.Platform;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class CRDTNetworkNode {
    private final int PORT = 9999;
    private String HOST = "localhost";
    private Socket socket;
    private final RGAReplica replica;
    private ObjectOutputStream out;
    private final Runnable uiRefresh;
    private final Queue<Operation> pendingOps = new ArrayDeque<>();
    private volatile boolean connected = false;
    private final Runnable conRefresh;
    private boolean serverOn = false;


    private final ExecutorService pool = Executors.newCachedThreadPool(daemonThreadFactory());

    public CRDTNetworkNode(RGAReplica replica, Runnable uiRefresh, Runnable conRefresh) {
        this.replica = replica;
        this.uiRefresh = uiRefresh;
        this.conRefresh = conRefresh;
    }

    public void setServerOn(){
        serverOn = true;
    }
    public void setServerOff(){serverOn = false;}

    public void connect(String ip) {
        if(!ip.isEmpty()) HOST = ip;
        try {
            this.socket = new Socket(HOST, PORT);
            out = new ObjectOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            Platform.runLater(conRefresh);
            return;
        }
        pool.execute(this::listen);
    }

    public void listen(){
        try {
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            connected = true;
            if(serverOn){
                in.readObject();
            }
            else{
                RGAReplica other = (RGAReplica) in.readObject();
                replica.replaceWith(other);
                while (!pendingOps.isEmpty()) {
                    Operation c = pendingOps.poll();
                    c.applyTo(replica);
                    send(c);
                }
            }
            Platform.runLater(uiRefresh);
            while(connected){
                Operation op = (Operation) in.readObject();
                op.applyTo(replica);
                Platform.runLater(uiRefresh);
            }
            Platform.runLater(conRefresh);
        }catch (Exception e){
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
        return connected && out != null;
    }

    public void disconnect() {
        connected = false;
        try {
            if (out != null) {
                out.close();
            }
        } catch (IOException ignored) {
        } finally {
            out = null;
        }
    }


    private static ThreadFactory daemonThreadFactory(){
        AtomicInteger count = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, "crdt-client-" + count.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}