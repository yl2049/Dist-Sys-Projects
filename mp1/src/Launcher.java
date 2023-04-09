import java.io.DataInputStream;
import java.io.PrintWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Scanner;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.net.UnknownHostException;

import java.util.concurrent.TimeUnit;

public class Launcher {
    /*
    command: java Node node# conf_file
    conf_file:
        3
        node1 sp23-cs425-0101.cs.illinois.edu 1234
        node2 sp23-cs425-0102.cs.illinois.edu 1234
        node3 sp23-cs425-0103.cs.illinois.edu 1234
     */

    public static void main(String args[]) {
        String name = args[0];
        int nodeID = name.charAt(name.length() - 1) - '0';
        Node node = new Node(name, nodeID);
        ArrayList<String[]> confLines = new ArrayList<>();
        ArrayList<InetAddress> ipList = new ArrayList<>();
        File conf;
        Scanner reader;
        ServerSocket server;
        int nodeCount;
        int port = 1234;

        try {
            conf = new File(args[1]);
            reader = new Scanner(conf);
            nodeCount = Integer.valueOf(reader.nextLine()) - 1;

            while(reader.hasNext()) {
                String[] lineArr = reader.nextLine().split(" ");
                // if this is the current node, update port and skip this line
                if(lineArr[0].equals(name)) {
                    port = Integer.valueOf(lineArr[2]);
                    continue;
                }

                ipList.add(InetAddress.getByName(lineArr[1]));
                confLines.add(lineArr);
            }
            server = new ServerSocket(port);
        } catch (Exception e) {
            System.err.println("Reading configuration: " + e);
            return;
        }

        nodeCount = confLines.size();
        boolean[] connected = new boolean[nodeCount];
        String nodeName = "";
        int socketCount = 0;

        // Try to connect other nodes first, if any not connected, continue to listen on port in the while loop
        for(int i = 0; i < nodeCount; i++) {
            if(connected[i]) {
                continue;
            }
            String[] c = confLines.get(i); // node# hostName port
            nodeName = c[0];
            try {
                // if connection fails, it will jump to catch block
                Socket clientSocket = new Socket(c[1], Integer.valueOf(c[2]));
                if(clientSocket != null) {
                    node.addSocket(i, clientSocket, nodeName);
                    connected[i] = true;
                    socketCount++;
                }
            } catch (Exception e) {
                // continue initializing connections
                System.err.println(String.format("Socket connection fails: %s %s", nodeName, e));
            }
        }
        System.err.println("Start listening on port: " + port);

        while(socketCount < nodeCount) {
            try {
                try {
                    Socket serverSocket = server.accept();
                    if(serverSocket != null) {
                        InetAddress ip = serverSocket.getInetAddress();
                        for (int i = 0; i < nodeCount; i++) {
                            if (connected[i]) {
                                continue;
                            }
                            // check which node is connected to. DOES NOT WORK WITH LOCALHOST
                            if (ipList.get(i).equals(ip)) {
                                node.addSocket(i, serverSocket, confLines.get(i)[0]);
                                connected[i] = true;
                                socketCount++;
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Server socket accept fails, " + e);
                }

            } catch (Exception e) {
                System.err.println("Uncaught exception" + e);
                break;
            }
        }

        int socketIdx = 0;

        while (socketIdx < socketCount) {
            try {
                Socket socket = node.getSocket(socketIdx);
                Thread t = new Thread(new SocketThread(socket, node, confLines.get(socketIdx)[0], socketIdx));
                t.start();
                socketIdx++;
            } catch (Exception e) {
                System.err.println("Create thread error: " + e);
            }
        }

        System.err.println("\n-----Start Transactions----");

        /*
        read from System.in,
        e.g.:
            DEPOSIT yxpqg 75
            TRANSFER yxpqg -> wqkby 13
         */
        DataInputStream localIn = new DataInputStream(System.in);     // read local input
        while(true) {
            try {
                String content = localIn.readLine();
                node.multicastNewEvent(content);
            } catch (Exception e) {
                System.err.println("Read local input at : " + name + e);
            }
        }
    }
}
