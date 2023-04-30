import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashSet;

/*
    Handles transaction
 */
public class ServerServiceImpl extends UnicastRemoteObject implements ServerService{
    Server server;
    public ServerServiceImpl(Server server) throws RemoteException {
        this.server = server;
    }

    @Override
    public String testConnection() throws RemoteException {
        return "Connected to " + server.getNameID();
    }

    @Override
    public String getServer() throws RemoteException {
        return server.getNameID();
    }

    /*
        return String of balance
        throw NoSuchObjectException if accountName does not exist
     */
    @Override
    public String balance(String transactionID, String accountName) throws RemoteException {
        if (!server.accounts.containsKey(accountName)) {
            throw new NoSuchObjectException("Account does not exist");
        }
        Integer balance = null;
        Account account = server.accounts.get(accountName);
        synchronized (account) {
            while(true) {
                String lastCommitID = account.getLastCommitID();
                if (lastCommitID.equals("") &&
                        (account.TW.size() == 0 || account.TW.firstKey().compareTo(transactionID) > 0)) {
                    // no transaction has committed, and no tentative write, the account does not exist
                    throw new NoSuchObjectException("Account does not exist");
                }
                if (transactionID.compareTo(lastCommitID) > 0) {
                    String maxWTS = account.getMaxWriteTS(transactionID);  // max WTS <= transactionID
                    if (maxWTS.compareTo(lastCommitID) <= 0) {
                        balance = account.getBalance();
                        account.addRTS(transactionID);
                        return String.valueOf(balance);
                    } else {
                        if (maxWTS.equals(transactionID)) {
                            balance = account.getTWAmount(transactionID);
                            return String.valueOf(balance);
                        } else {
                            // wait for the transaction maxWTS to commit or abort
                            // timestamped ordering requires a transaction to commit after all previous transactions
                            // have committed/aborted, so we can just use the last commit ID here
                            // when commit/abort, call account.notifyAll()
                            if (maxWTS.compareTo(account.getLastCommitID()) > 0) {
                                try {
                                    account.wait(); // wait to be notified
                                } catch (InterruptedException e) {
//                                System.err.println(e);
                                }
                            }
                        }
                    }
                } else {
//                    System.err.println("Transaction " + transactionID + " is too late to read from " + accountName);
                    return null;
                }
            }
        }
    }

    /*
        return true if the transaction can proceed
        return false if any transaction with a larger id has already written to the account
     */
    @Override
    public boolean deposit(String transactionID, String accountName, int amount) throws RemoteException {
        // create new account
        if (!server.accounts.containsKey(accountName)) {
            server.accounts.put(accountName, new Account(accountName));
        }
        // read
        String balance = null;
        try {
            balance = balance(transactionID, accountName);
            if (balance == null) {
                balance = "0";
            }
        } catch (Exception e) {
            balance = "0";
        }
        int newAmount = Integer.valueOf(balance) + amount;

        // update existing account
        Account account = server.accounts.get(accountName);
        // synchronize the whole account
        synchronized (account) {
            if (transactionID.compareTo(account.getMaxReadTS()) >= 0 &&
                    transactionID.compareTo(account.getLastCommitID()) > 0) {
                account.addTW(transactionID, newAmount);
            } else {
//                System.err.println("Transaction " + transactionID + " is too late to write to " + accountName);
                return false;
            }
            return true;
        }
    }

    /*
        return true if the transaction can proceed
        return false if any transaction with a larger id has already written to the account
        throw NoSuchObjectException if the account does not exist
     */
    @Override
    public boolean withdraw(String transactionID, String accountName, int amount) throws RemoteException {
        if (!server.accounts.containsKey(accountName)) {
            throw new NoSuchObjectException("Account does not exist");
        }
        Account account = server.accounts.get(accountName);

        // read
        String balance = null;
        try {
            balance = balance(transactionID, accountName);
            if (balance == null) {
                return false;   // not sure
            }
        } catch (Exception e) {
            throw new NoSuchObjectException("Account does not exist");
        }
        int newAmount = Integer.valueOf(balance) - amount;

        synchronized (account) {
            if (transactionID.compareTo(account.getMaxReadTS()) >= 0 &&
                    transactionID.compareTo(account.getLastCommitID()) > 0) {
                account.addTW(transactionID, newAmount);
            } else {
//                System.err.println("Transaction " + transactionID + " is too late to write to " + accountName);
                return false;
            }
            return true;
        }
    }

    /*
        Prepare for commit
        return true if can commit on this server

        wait here until all accounts are ready to commit
        FIXME: check locking design
     */
    @Override
    public boolean tryCommit(HashSet<String> accountNames, String transactionID) throws RemoteException {
//        ArrayList<Account> accounts = new ArrayList<>();
        for (String name : accountNames) {
            Account account = server.accounts.get(name);
            synchronized (account) {
//                accounts.add(account);
//                account.lock.lock();
                while (!account.canCommit(transactionID)) {
                    try {
//                        account.lock.wait();
                        account.wait();
                    } catch (InterruptedException e) {
//                    e.printStackTrace();
                    }
//                return false;
                }
                // negative balance, abort
                if (!account.checkConsistency(transactionID)) {
//                    for (Account a : accounts) {
//                        a.lock.unlock();
//                    }
                    return false;
                }
            }
        }
        return true;
    }

    /*
        Commit an operation to an account on a server
        Return true if commit is successful

        FIXME: lock the entire map of accounts?
     */
    @Override
    public boolean commit(HashSet<String> accountNames, String transactionID) {
        for (String name : accountNames) {
            Account account = server.accounts.get(name);
//            account.lock.unlock();
            synchronized (account) {
                account.commit(transactionID);
//                account.lock.unlock();
                account.notifyAll();    // notify all threads waiting on this account
            }
        }
        for (Account account : server.accounts.values()) {
            if (account.getBalance() > 0) {
                System.out.println(String.format("%s.%s = %s", server.name, account.name, account.getBalance()));
            }
        }
        return true;
    }

    /*
        Abort all the operations to accounts on a server
        Return true if abort is successful
     */
    @Override
    public boolean abort(HashSet<String> accountNames, String transactionID) {
        for (String name : accountNames) {
            Account account = server.accounts.get(name);
            synchronized (account) {
                account.TW.remove(transactionID);
                account.notifyAll();    // notify all threads waiting on this account
            }
        }
        return true;
    }
}
