import java.io.IOException;
import java.net.Socket;
import java.io.*;
import java.util.Scanner;

public class Client {
    // initialize socket and input output streams
    private String name;
    private Socket socket = null;
    private DataInputStream input = null;
    private DataOutputStream out = null;

    // constructor to put ip address and port
    public Client(String name, String address, int port) {
        // establish a connection
        try {
            socket = new Socket(address, port);

            // takes input from terminal
            input = new DataInputStream(System.in);
            // sends output to the socket
            out = new DataOutputStream(socket.getOutputStream());

            String connectedTime = NanoTimer.getTime();
            System.out.println("Connected at " + connectedTime);
            out.writeUTF(connectedTime + " - " + name);
        } catch (Exception e) {
            System.err.println(e.toString());
            return;
        }

        // string to read message from input
        String line = "";

        while (true) {
            try {
                String[] strs = input.readLine().split(" ");
                // {client timestamp, client name, event id}
                line = String.format("%s %s %s", strs[0], name, strs[1]);
                out.writeUTF(line);
            } catch (Exception e) {
                System.err.println(e.toString());
                break;
            }
        }

        // close the connection
        try {
            input.close();
            out.close();
            socket.close();
        } catch (Exception e) {
            System.err.println(e.toString());
        }
    }

    public static void main(String args[]) {
        Client client = new Client(args[0], args[1], Integer.parseInt(args[2]));
    }
}