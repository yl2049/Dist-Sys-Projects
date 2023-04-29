import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

/*
    interface for receiving and forwarding client requests
    Stores transaction information
 */
public interface ClientService extends Remote {
    //TODO: methods

    /*
     test connection
     */
    String testConnection(String clientName) throws RemoteException;

    boolean transactionBegin(String clientName) throws RemoteException;

    boolean transactionCommit(String clientName) throws RemoteException;

    boolean transactionAbort(String clientName) throws RemoteException;

    boolean transactionDeposit(String clientName, String serverName, String accountName, int amount) throws RemoteException;

    boolean transactionWithdraw(String clientName, String serverName, String accountName, int amount) throws RemoteException;

    String transactionBalance(String clientName, String serverName, String accountNumber) throws  RemoteException;

}
