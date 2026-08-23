package network;

import crdt.Operation;
import crdt.RGAReplica;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CRDTServer {
    private static final Logger logger = Logger.getLogger(CRDTServer.class.getName());
    int port = 9999;
    final RGAReplica replica;
    List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private volatile boolean running = false;

    private volatile ServerSocket serverSocket;
    private final ExecutorService pool = Executors.newCachedThreadPool(daemonThreadFactory());

    public CRDTServer(RGAReplica replica){
        this.replica = replica;
        logger.setLevel(Level.OFF);
    }


    public void go() {
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            throw new RuntimeException("CRDT Server failed to start on port " + port, e);
        }
        running = true;
        pool.execute(this::runServer);
    }

    public void runServer(){
        try {
            while(running){
                Socket client = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(client);
                clients.add(clientHandler);
                pool.execute(clientHandler);
            }
        } catch (IOException e) {
            if (running) {
                logger.log(Level.WARNING, "Server error", e);
            }
        } finally {
             if (serverSocket != null && !serverSocket.isClosed()) {
                try {
                    serverSocket.close();
                } catch (IOException et) {
                    logger.log(Level.WARNING, "Server Closing error", et);
                }
            }
        }
    }

    void broadcast(Operation op, ClientHandler except){
        for(ClientHandler client : clients){
            if(client != except)
                client.send(op);
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            for (ClientHandler client : clients) {
                client.socket.close();
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Problem Stopping Server",e);
        } finally {
            clients.clear();
            pool.shutdownNow();
        }

    }


    private static ThreadFactory daemonThreadFactory(){
        AtomicInteger count = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, "crdt-server-" + count.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    private class ClientHandler implements Runnable{
        private final Socket socket;
        private ObjectOutputStream out;
        public ClientHandler(Socket socket){
            this.socket = socket;
        }
        public void run(){
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                // Send full CRDT snapshot
                synchronized (replica) {
                    out.writeObject(replica.deepCopy());
                }
                out.flush();
                while (true) {
                    Operation op = (Operation) in.readObject();
                    broadcast(op,this);
                }
            } catch(IOException | ClassNotFoundException ignored){

            } finally {
                clients.remove(this);
                try { socket.close(); } catch (IOException e) {
                    logger.log(Level.WARNING, "Problem in closing socket of a ClientHandler" + e.getMessage());
                }
            }
        }
        void send(Operation msg) {
            try {
                out.writeObject(msg);
                out.flush();
            } catch (IOException e) {
                logger.log(Level.WARNING, "Couldn't send op to client", e);            }
        }
    }
}