import java.io.*;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;

public class EchoThread extends Thread{
    protected Socket socket;
    private int nodeIdx;
    private DataInputStream in = null;

    private String clientName;
    public EchoThread(Socket clientSocket, int nodeIdx) {
        this.socket = clientSocket;
        this.nodeIdx = nodeIdx;
        clientName = "";
    }

    public void run() {
        try {
            in = new DataInputStream(
                    new BufferedInputStream(socket.getInputStream()));

            String line = "";

            try {
                line = in.readUTF();
                clientName = line.split("-")[1];
                System.out.format(line + " connected\n");
            } catch (Exception e) {
                System.err.println(
                        String.format("Initialize connection failed at : %s", clientName, e.toString())
                );
            }
            // write to a single file
            // data output: {node name, client timestamp, server timestamp}
            FileWriter timestampWriter = new FileWriter(clientName + "_timestamps.txt", false);

            while (true) {
                try {
                    // *** required output ***
                    line = in.readUTF();
                    String serverTime = NanoTimer.getTime();
                    byte[] byteOfMessage = line.getBytes("UTF-8");
                    // {client timestamp, client name, event id}
                    String[] strs = line.split(" ");

                    // *** for graph use ***
//                    line = String.format("%s %s %s", strs[0], strs[1], strs[2]);
//                    line = String.format("%s %s %s %d %s", strs[0], strs[1], serverTime, byteOfMessage.length, strs[2]);

                    System.out.println(line);

                    // write to a single file
                    timestampWriter.write(String.format("%s %s %s %d\n", strs[1], strs[0], serverTime, byteOfMessage.length));
                } catch (Exception e) {
                    System.err.println(e.toString());
                    break;
                }
            }
            String disconnectedTime = NanoTimer.getTime();
            System.out.format(disconnectedTime + " -" + clientName + " disconnected\n");

            // close connection
            socket.close();
            in.close();
        }
        catch(Exception e) {
            System.err.println(e.toString());
        }
    }
}
