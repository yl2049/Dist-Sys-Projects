import java.io.Serializable;
import java.rmi.NoSuchObjectException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
//import java.util.concurrent.locks.ReadWriteLock;
//import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Account implements Serializable {

    protected String name;
    protected int balance;  // committed value
    protected String lastCommitID; // transaction id of the last committed transaction, default: -1
    protected TreeSet<String> RTS; // read timestamp
    protected TreeMap<String, Integer> TW; // tentative write; key: transactionID, value: balance after operation
//    protected Lock lock;

    public Account(String name) {
        this.name = name;
        this.balance = 0;
        this.lastCommitID = "";
        this.RTS = new TreeSet<>();
        this.TW = new TreeMap<>();
//        this.lock = new ReentrantLock();
    }

    /*
        return the max read timestamp in RTS
     */
    public String getMaxReadTS() {
        if (RTS.isEmpty()) {
            return "";
        }
        return RTS.last();
    }

    /*
        return the max read timestamp <= transactionID
     */
    public String getMaxWriteTS(String transactionID) {
        String ret = TW.floorKey(transactionID);
        return ret == null ? "" : ret;
    }
    /*
        return the tentative write amount of transactionID
     */
    public int getTWAmount(String transactionID) {
        Integer ret = TW.get(transactionID);
        return ret == null ? 0 : ret;
    }

    public int getBalance() {
        return balance;
    }

    public String getLastCommitID() {
        return lastCommitID;
    }

    /*
        return true if no smaller transactionID in TW
     */
    public boolean canCommit(String transactionID) {
        String ret = TW.floorKey(transactionID);
        return ret == null || ret.equals(transactionID);
    }

    /*
        return true if balance to commit is not negative
     */
    public boolean checkConsistency(String transactionID) {
        if (TW.containsKey(transactionID)) {
            return TW.get(transactionID) >= 0;
        }
        return true;
    }

    public void addTW(String transactionID, int amount) {
        TW.put(transactionID, amount);
    }

    public void addRTS(String transactionID) {
        RTS.add(transactionID);
    }

    /*
        commit the transaction
        remove transaction from TW
     */
    public void commit(String transactionID) {
        if (TW.containsKey(transactionID)) {
//            balance += TW.get(transactionID);
            balance = TW.get(transactionID);
            TW.remove(transactionID);
            lastCommitID = transactionID;
//            System.err.println("Account.commit: transactionID " + transactionID + " committed");
        } else {
//            System.err.println("Account.commit: transactionID not found in TW");
        }
    }


}
