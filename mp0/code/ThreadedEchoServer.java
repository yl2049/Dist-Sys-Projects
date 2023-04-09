import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;


public class ThreadedEchoServer {
    static int PORT;

    public static void main(String args[]) {
        PORT = Integer.parseInt(args[0]);
        ServerSocket serverSocket = null;
        Socket socket = null;

        try {
            serverSocket = new ServerSocket(PORT);
        } catch (Exception e) {
            System.err.println(e.toString());
        }

        int nodeIdx = 1;
        while (true) {
            try {
                socket = serverSocket.accept();
            } catch (Exception e) {
                System.err.println("Socket connection error: " + e.toString());
            }
            // new thread for a client
            new EchoThread(socket, nodeIdx).start();
            nodeIdx++;
        }
    }
}