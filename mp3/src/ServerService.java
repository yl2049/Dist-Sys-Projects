import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.HashSet;

/*
    Handles transaction
 */
public interface ServerService extends Remote {
    String testConnection() throws RemoteException;     // test connection
    public String getServer() throws RemoteException;
    String balance(String transactionID, String accountName) throws RemoteException;
    boolean deposit(String transactionID, String accountName, int amount) throws RemoteException;
    boolean withdraw(String transactionID, String accountName, int amount) throws RemoteException;
    boolean commit(HashSet<String> accountNames, String transactionID) throws RemoteException;
    boolean tryCommit(HashSet<String> accountNames, String transactionID) throws RemoteException;
    boolean abort(HashSet<String> accountNames, String transactionID) throws RemoteException;
}
