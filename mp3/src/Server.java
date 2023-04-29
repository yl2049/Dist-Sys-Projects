//import java.io.DataInputStream;
//import java.io.DataOutputStream;
import java.io.File;
import java.rmi.Naming;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.RemoteException;
import java.net.InetAddress;
import java.util.PriorityQueue;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;


/*
    works as an entry point to communicate with client, also as storage;
    use ClientService to forward transaction request to corresponding server.ServerService
    use serverService to actually processes the transaction
 */
public class Server {
    protected String name;
    protected int serverID;
    protected String[] peerNameArray;
    protected String[] ipArray;
    protected int[] portArray;
    protected ServerService[] serverServiceArray;
    protected ConcurrentHashMap<String, Account> accounts;    // account storage

    class Pair<String, Thread> {
        String transactionID;
        Thread thread;
        Pair(String transactionID, Thread thread) {
            this.transactionID = transactionID;
            this.thread = thread;
        }
    }

    public Server(String args[]) {
        name = args[0];
        serverID = 0;
        ipArray = new String[5];
        peerNameArray = new String[5];
        portArray = new int[5];
        serverServiceArray = new ServerService[5];
        accounts = new ConcurrentHashMap<>();
        String[][] confLines = new String[5][3];

        File conf;
        Scanner reader;

        try {
            conf = new File(args[1]);
            reader = new Scanner(conf);
            // input: A sp23-cs425-0101.cs.illinois.edu 1234
            for(int i = 0; i < 5; i++) {
                String[] lineArray = reader.nextLine().split(" ");
                confLines[i] = lineArray;
                peerNameArray[i] = lineArray[0];
                ipArray[i] = InetAddress.getByName(lineArray[1]).getHostAddress();
                portArray[i] = Integer.valueOf(lineArray[2]);
                if(peerNameArray[i].equals(name)) {
                    serverID = i;
                }
            }
            reader.close();

            // initialize connection address (server connection and client connection)
            LocateRegistry.createRegistry(portArray[serverID]);
            ClientService clientService = new ClientServiceImpl(this);
            Naming.rebind("rmi://" + ipArray[serverID] + ":" + portArray[serverID] + "/client", clientService);
//            System.err.println(String.format("Server%d %s starts client service: rmi://%s:%s/client",
//                    serverID, name, ipArray[serverID], portArray[serverID]));
            ServerService serverService = new ServerServiceImpl(this);
            Naming.rebind("rmi://" + ipArray[serverID] + ":" + portArray[serverID] + "/server", serverService);
            serverServiceArray[serverID] = serverService;   // assign serverService to itself
//            System.err.println(String.format("Server%d %s starts server service: rmi://%s:%s/server",
//                    serverID, name, ipArray[serverID], portArray[serverID]));
        } catch (Exception e) {
//            System.err.println("Reading configuration: " + e);
            return;
        }

        int registryCount = 0;

        // connect to other servers via address
        while(registryCount < 4) {
            for(int i = 0; i < 5; i++) {
                if (serverID == i || serverServiceArray[i] != null) {
                    continue;
                }
                try {
                    Registry registry = LocateRegistry.getRegistry(ipArray[i], portArray[i]);
                    serverServiceArray[i] = (ServerService) registry.lookup("server");
                    registryCount++;
                    // call methods of serverServiceArray[i] to communicate with other servers
//                    System.err.println(serverServiceArray[i].testConnection());
                } catch (Exception e) {
//                    System.err.println("Connecting to other server: " + e);
                }
            }
        }


    }



    // command: ./server name config.txt
    public static void main(String args[]) {
        if (args.length != 2) {
            System.err.println("Invalid number of arguments");
        }

        Server server = new Server(args);

    }

    // for client to get serverID
    public String getNameID() {
        return String.format("%s, id %d", name, serverID);
    }

    /*
        can assume always valid
     */
    public ServerService getServerService(String serverName) {
        for(int i = 0; i < 5; i++) {
            if(peerNameArray[i].equals(serverName)) {
                return serverServiceArray[i];
            }
        }
        return null;
    }

    // get serverService by serverID
    public ServerService getServerService(int serverID) {
        return serverServiceArray[serverID];
    }
}