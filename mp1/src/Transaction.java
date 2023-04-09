import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.TreeMap;


public class Transaction {
    private volatile TreeMap<String, Integer> accounts;
    FileWriter accountLogger;   // Log file for account updates
    FileWriter timeLogger;      // Log file for message generate and deliver time


    public Transaction() {
        accounts = new TreeMap<>();

        try {
            Files.createDirectories(Paths.get("./log"));
            File accountLogFile = new File("log/account_log.txt");
            File timeLogFile = new File("log/time_log.txt");
            accountLogFile.createNewFile();
            timeLogFile.createNewFile();
            accountLogger = new FileWriter("log/account_log.txt");
            timeLogger = new FileWriter("log/time_log.txt");
        } catch (IOException e) {
            System.err.println("Error creating log file " + e);
        }
    }

    /*
    input e.g.:
        DEPOSIT yxpqg 75
        TRANSFER yxpqg -> wqkby 13
    output e.g.:
        BALANCES wqkby:23 yxpqg:62
    */
    // NOT SURE: need concurrency?
    public synchronized void processTransaction(Message msg) {
        String[] transaction = msg.getContent().split(" ");
        // DEPOSIT
        if(transaction[0].equals("DEPOSIT")) {
            // Always successful
            accounts.put(transaction[1],
                    accounts.getOrDefault(transaction[1], 0) + Integer.valueOf(transaction[2]));
        }
        // TRANSFER
        else {
            int amount = Integer.valueOf(transaction[4]);
            if(accounts.containsKey(transaction[1]) && accounts.get(transaction[1]) >= amount) {
                accounts.put(transaction[1], accounts.get(transaction[1]) - amount);
                accounts.put(transaction[3], accounts.getOrDefault(transaction[3], 0) + amount);
            } // otherwise fail
        }

        // Log transaction & print results
        StringBuilder output = new StringBuilder("BALANCES ");
        for(String name : accounts.keySet()) {
            if(accounts.get(name) > 0) {
                output.append(name + ":" + accounts.get(name) + " ");
            }
        }

        try {
            accountLogger.write("-----" + msg.getIdentifier() + "-----" + String.format("%d.%d", msg.getPriorityEventId(), msg.getPriorityNodeId())
                    + "\n" + output.toString() + "\n" + "---------------\n\n");
            timeLogger.write(msg.getEventStartTime() + " " + NanoTimer.getTime() + "\n");
        } catch (Exception e) {
            System.err.println("processTransaction: Error writing log file " + e);
        }
        System.out.println(output.toString());
    }

}
