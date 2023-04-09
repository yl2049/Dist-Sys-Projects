import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;


public class SocketThread implements Runnable {

    private Socket socket;
    private ObjectInputStream socketIn;
    private Node node;
    private String clientName;
    private int socketIdx;          // index of sender socket

    public SocketThread(Socket clientSocket) {
        this.socket = clientSocket;
    }
    public SocketThread(Socket clientSocket, Node node, String clientName, int socketIdx) {
        this.socket = clientSocket;
        this.node = node;
        this.clientName = clientName;
        this.socketIdx = socketIdx;
    }

    @Override
    public void run() {
        try {
            System.err.println(String.format("%s connected to %s", node.getName(), clientName));
            socketIn = new ObjectInputStream(new BufferedInputStream(socket.getInputStream()));

            Message socketMessage = null;

                try {
                    System.err.println("Reading socket in from " + clientName);
                    while (true) {
                        socketMessage = (Message) socketIn.readObject();

                        if(socketMessage == null) {
                            throw new SocketException("Socket closed: " + socketIdx);
                        }

                        // ----- ISIS -----
                        // type 0: message asking for priority proposal
                        // ------- respond with proposed priority
                        if (socketMessage.getType() == 0)
                            node.proposePriority(socketMessage, socketIdx);

                        // type 1: message proposing priorities
                        // ------- respond with final priority if ready
                        else if (socketMessage.getType() == 1)
                            node.finalizePriority(socketMessage, socketIdx);

                        // type 2: message informing about final priority
                        // ------- R-multicast final priority and set message as deliverable
                        else
                            node.acceptAndDeliver(socketMessage, socketIdx);

                    }
                } catch (Exception e) {
                    System.err.println(e + ": disconnect from " + clientName + " " + socketIdx);
                    node.removeOutputStream(socketIdx);
                    // close connection
                    socketIn.close();
                    socket.close();
                }
        }
        catch(Exception e) {
            System.err.println(e);
        }
    }
}
