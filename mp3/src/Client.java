import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.InputStreamReader;
import java.io.File;
import java.net.InetAddress;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Scanner;
import java.util.Random;

public class Client {
    // initialize socket and input output streams
    private String name;
    private BufferedReader reader = null;
    private String[] serverNameArray;
    private String[] ipArray;
    private int[] portArray;

    public Client(String name, String configFile) {
        DataInputStream input = null;
        serverNameArray = new String[5];
        ipArray = new String[5];
        portArray = new int[5];
        this.name = name;

        File conf;
        Scanner confReader;
        Scanner stdin;

        // read config
        try {
            conf = new File(configFile);
            confReader = new Scanner(conf);
            // input: A sp23-cs425-0101.cs.illinois.edu 1234
            for(int i = 0; i < 5; i++) {
                String[] lineArray = confReader.nextLine().split(" ");
                serverNameArray[i] = lineArray[0];
//                System.err.println(lineArray[1]);
                ipArray[i] = InetAddress.getByName(lineArray[1]).getHostAddress();
//                System.err.println(ipArray[i]);
                portArray[i] = Integer.valueOf(lineArray[2]);
            }
            confReader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        ClientService clientService;
        // establish connection to server. all transaction requests are forwarded to this server's corresponding clientService
        try {
            // randomly pick one server as coordinator
            int coordinatorServerID = new Random().nextInt(0, 5);
            Registry registry = LocateRegistry.getRegistry(ipArray[coordinatorServerID], portArray[coordinatorServerID]);
//            Registry registry = LocateRegistry.getRegistry(ipArray[0], portArray[0]);   // for test use
            clientService = (ClientService) registry.lookup("client");
//            System.err.println(clientService.testConnection(this.name));

            // test multithreading
//            int i = 0;
//            while(i < 100) {
//                sleep(1000);
//                System.err.println(clientService.testConnection(this.name));
//            }
        } catch (Exception e) {
            System.err.println(e);
            return;
        }

        // string to read message from input
        String line = "";

        /* input are always valid:
            BEGIN
            DEPOSIT A.foo 20
            DEPOSIT A.foo 30
            WITHDRAW A.foo 10
            DEPOSIT C.zee 10
            BALANCE A.foo
            COMMIT
         */
        try {
//            System.out.println(String.format("Client %s try to read", name));
            boolean continueFlag = true;
            // takes input from terminal
            input = new DataInputStream(System.in);
            reader = new BufferedReader(new InputStreamReader(input));
            stdin = new Scanner(reader);
            while(continueFlag && (line = stdin.nextLine()) != null) {
//                System.out.println(String.format("Client %s read %s", name, line));

                String[] strs = line.split(" ");
                String[] serverAndAccount;
//                System.out.println(strs[0]);

                switch (strs[0]) {
                    case "BEGIN":
                        if (clientService.transactionBegin(name)) {
                            // OK
                            System.out.println("OK");
                        } else {
                            // aborted
                            System.out.println("ABORTED");
                            continueFlag = false;
                        }
                        break;
                    case "COMMIT":
                        if (clientService.transactionCommit(name)) {
                            System.out.println("COMMIT OK");
                        } else {
                            System.out.println("ABORTED");
                        }
                        continueFlag = false;
                        break;
                    case "ABORT":
                        // should always succeed
                        if (!clientService.transactionAbort(name)) {
//                            System.err.println("ABORT FAILED");
                        }
                        System.out.println("ABORTED");
                        continueFlag = false;
                        break;
                    case "DEPOSIT":
                        serverAndAccount = strs[1].split("\\.");
//                        System.out.println(serverAndAccount[0] + " " + serverAndAccount[1] + " " + strs[2]);
                        if (clientService.transactionDeposit(name, serverAndAccount[0], serverAndAccount[1], Integer.valueOf(strs[2]))) {
                            System.out.println("OK");
                        } else {
                            System.out.println("ABORTED");
                            continueFlag = false;
                        }
                        break;
                    case "WITHDRAW":
                        serverAndAccount = strs[1].split("\\.");
                        try {
                            if (clientService.transactionWithdraw(name, serverAndAccount[0], serverAndAccount[1], Integer.valueOf(strs[2]))) {
                                System.out.println("OK");
                            } else {
                                System.out.println("ABORTED");
                                continueFlag = false;
                            }
                        } catch (Exception e) {
                            System.out.println("NOT FOUND, ABORTED");
                            continueFlag = false;
                        }
                        break;
                    case "BALANCE":
                        // BALANCE: abort if not found
                        String[] args = strs[1].split("\\.");
                        String res = null;
                        try {
                            res = clientService.transactionBalance(name, args[0], args[1]);
                            if (res.equals("ABORTED")) {
                                continueFlag = false;
                            }
                        } catch (Exception e) {
//                            System.out.println(e);
                            System.out.println("NOT FOUND, ABORTED");
                            continueFlag = false;
                            break;
                        }
                        System.out.println(res);
                        break;
                    default:
                        System.err.println("Invalid command");
                        break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // close the connection
        try {
            input.close();
        } catch (Exception e) {
//            System.err.println(e.toString());
        }
    }

    // command: ./client name config.txt
    public static void main(String args[]) {
//        System.out.println(String.format("Client %s started", args[0]));
        if (args.length != 2) {
            System.err.println("Invalid number of arguments");
        }
        Client client = new Client(args[0], args[1]);
    }
}