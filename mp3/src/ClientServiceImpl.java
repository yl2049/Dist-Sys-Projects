import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/*
    ClientService forwards requests from clients to the corresponding server (ServerService)
    call server.getServerService(id).[methods]
 */
public class ClientServiceImpl extends UnicastRemoteObject implements ClientService{
    public class Transaction {
        String transactionID;
//        ArrayList<Operation> operations;
        ConcurrentHashMap<ServerService, HashSet<String>> operations;
        // to abort, find the account at corresponding server, delete elements from TW by transactionID

        public Transaction(String id) {
            this.transactionID = id;
            operations = new ConcurrentHashMap<>();
        }
    }

    Server server;
    NanoTimer timer;

    ConcurrentHashMap<String, Transaction> transactionMap;
    // key: clientName, value: transaction

    public ClientServiceImpl(Server server) throws RemoteException {
        this.server = server;
        timer = new NanoTimer();
        transactionMap = new ConcurrentHashMap<>();
    }

    /*
     get transactionID from server 0
     return: true  - "OK"
             false - "ABORT"; probably won't happen
     */
    @Override
    public boolean transactionBegin(String clientName) throws RemoteException {
        String id = NanoTimer.getTime() + " " + clientName;
        transactionMap.put(clientName, new Transaction(id));
        return true;
    }

    /*
        Iterate through the list of operations and call serverService.commit()
        If any commit() returns false then return false
        return: true  - "OK"
                false - "ABORT"
     */
    @Override
    public boolean transactionCommit(String clientName) throws RemoteException {
        Transaction transaction = transactionMap.get(clientName);
        for (Map.Entry<ServerService, HashSet<String>> entry : transaction.operations.entrySet()) {
            if (!entry.getKey().tryCommit(entry.getValue(), transaction.transactionID)) {
                // abort
                transactionAbort(clientName);
                return false;
            }
        }
        for (Map.Entry<ServerService, HashSet<String>> entry : transaction.operations.entrySet()) {
            entry.getKey().commit(entry.getValue(), transaction.transactionID);
        }
        return true;
    }

    @Override
    public boolean transactionAbort(String clientName) throws RemoteException {
        Transaction transaction = transactionMap.get(clientName);
        for (Map.Entry<ServerService, HashSet<String>> entry : transaction.operations.entrySet()) {
            if (!entry.getKey().abort(entry.getValue(), transaction.transactionID)) {
//                System.err.println(String.format("Abort failed at server: %s", entry.getKey().getServer()));
            }
        }
        return true;
    }

    @Override
    public boolean transactionDeposit(String clientName, String serverName, String accountName, int amount) throws RemoteException {
        ServerService serverService = server.getServerService(serverName); // get corresponding server's serverService
        if (!serverService.deposit(transactionMap.get(clientName).transactionID, accountName, amount)) {
            transactionAbort(clientName);
            return false;
        }
        // add operation to transaction
        transactionMap.get(clientName).operations.computeIfAbsent(serverService, k -> new HashSet<>()).add(accountName);
        return true;
    }

    @Override
    public boolean transactionWithdraw(String clientName, String serverName, String accountName, int amount) throws RemoteException {
        ServerService serverService = server.getServerService(serverName); // get corresponding server's serverService
        try {
            if (!serverService.withdraw(transactionMap.get(clientName).transactionID, accountName, amount)) {
                transactionAbort(clientName);
                return false;
            }
        } catch (Exception e) {
            transactionAbort(clientName);
            throw new NoSuchObjectException("Account does not exist: " + serverName + "." + accountName);
        }
        // add operation to transaction
        transactionMap.get(clientName).operations.computeIfAbsent(serverService, k -> new HashSet<>()).add(accountName);
        return true;
    }

    @Override
    public String transactionBalance(String clientName, String serverName, String accountNumber) throws RemoteException {
        ServerService serverService = server.getServerService(serverName); // get corresponding server's serverService
        String amount = String.format("%s.%s = ", serverName, accountNumber);
        try {
            String balance = serverService.balance(transactionMap.get(clientName).transactionID, accountNumber);
            if (balance == null) {
                // cannot read later transaction
                transactionAbort(clientName);
                return "ABORTED";
            }
            amount += balance;    // append string
        } catch (Exception e) {
            // account does not exist
            transactionAbort(clientName);
            throw new NoSuchObjectException("Account does not exist: " + serverName + "." + accountNumber);
//            System.err.println("Account does not exist: " + serverName + "." + accountNumber);
        }
        return amount;
    }

    // for test use
    @Override
    public String testConnection(String clientName) throws RemoteException {
//        System.err.println("Called from client: " + clientName);
        return "Connected to server " + server.getNameID();
    }
}
