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

public class CRDTServer {
    int port = 9999;
    final RGAReplica replica;
    List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private volatile boolean running = false;
    ServerSocket serverSocket;

    public CRDTServer(RGAReplica replica){
        this.replica = replica;
    }

    public void go(){
        new Thread(this::runServer).start();
    }

    public void runServer(){

        try {
            serverSocket = new ServerSocket(port);
            running = true;
            System.out.println("CRDT Server started on port " + port);
            while(running){
                Socket client =  serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(client);
                clients.add(clientHandler);
                new Thread(clientHandler).start();
            }
            serverSocket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);

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
            System.out.println("Some problem while stopping the server");
        }finally {
            clients.clear();
        }
        System.out.println("Server stopped");
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
//                out.writeObject(replica);
                out.flush();

                while (true) {
                    Operation op = (Operation) in.readObject();
//                    op.applyTo(replica);
                    broadcast(op,this);
                }
            } catch(IOException | ClassNotFoundException e){
                System.out.println("Client disconnected");
//                e.printStackTrace();
            }finally {
                clients.remove(this);
                try { socket.close(); } catch (IOException ignored) {
                    System.out.println("problem in closing socket");
                }
            }
        }

        void send(Operation msg) {
            try {
                out.writeObject(msg);
                out.flush();
            } catch (IOException ignored) {
                System.out.println("couldn't send the ops something happening here ");
            }
        }
    }
}
