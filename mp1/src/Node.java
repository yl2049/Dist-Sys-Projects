import java.io.*;
import java.util.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;


public class Node {
    static final int TIMEOUT = 4 * 5 + 1;   // at most 3 node failures, need a timeout > 4 * 5

    private volatile HashMap<Integer, Socket> socketMap;
    private volatile HashMap<Integer, String> clientNameMap;
    private volatile HashMap<Integer, ObjectOutputStream> outStreamMap;
    private volatile String name;
    private volatile int nodeID;
    private volatile HashMap<String, String> failedTimeMap;
    private volatile int localPriority;
    private volatile PriorityBlockingQueue<Message> priorityQueue;
    private volatile ConcurrentHashMap<String, HashSet<Integer>> proposedPriorityReceivedMap;
    // <Message.identifier, HashSet<socketID (connected nodeID)>>
    private volatile ConcurrentHashMap<String, Message> identifierMessageMap;
    // <Message.identifier, original message <=> message stored in priorityQueue>
    private volatile Transaction accountDatabase;

    public Node(String name, int nodeID) {
        socketMap = new HashMap<>();
        clientNameMap = new HashMap<>();
        outStreamMap = new HashMap<>();
        failedTimeMap = new HashMap<>();

        this.name = name;
        this.nodeID = nodeID;

        localPriority = 0;

        priorityQueue = new PriorityBlockingQueue<Message>(10000, (a, b) -> a.compareTo(b));
        proposedPriorityReceivedMap = new ConcurrentHashMap<>();
        identifierMessageMap = new ConcurrentHashMap<>();

        accountDatabase = new Transaction();
    }

    public synchronized Socket getSocket(int socketIdx) {
        return socketMap.get(socketIdx);
    }
    public synchronized String getName() {
        return this.name;
    }

    public void addSocket(int socketIdx, Socket socket, String clientName) {
        socketMap.put(socketIdx, socket);
        clientNameMap.put(socketIdx, clientName);
        try {
            outStreamMap.put(socketIdx, new ObjectOutputStream(socket.getOutputStream()));
            System.err.println("outstream created " + socket.getInetAddress());
        } catch (Exception e) {
            System.err.println("initializeOutStream: " + e);
        }
    }

    public synchronized void removeOutputStream(int socketIdx) {
        if(socketMap.containsKey(socketIdx)) {
            // Remove message tunnel
            outStreamMap.remove(socketIdx);
            socketMap.remove(socketIdx);
            // Remove from hashmap
            String failedName = clientNameMap.remove(socketIdx);
            failedTimeMap.put(failedName, NanoTimer.getTime());
            // Remove socketIdx of the failed node from the hashsets of all entries of proposedPriorityReceivedMap
            // i.e. for all messages initiated by this node itself, stop waiting for priority proposal from the failed node
            for (String identifier: proposedPriorityReceivedMap.keySet()) {
                proposedPriorityReceivedMap.get(identifier).remove(socketIdx);
                // If all priority proposal have been received, process deliverable message
                if (proposedPriorityReceivedMap.get(identifier).isEmpty())
                    processDeliverable(identifier);
            }

            System.err.println("socket removed, socket id: " + socketIdx);
        }
    }

    public synchronized void multicastMessage(Message message) {
        for(int i : outStreamMap.keySet()) {
            try {
                outStreamMap.get(i).writeObject(message);
            } catch (Exception e) {
                System.err.println("multicastMessage: " + e);
            }
        }
    }

    public synchronized void multicastMessage(Message message, int exceptIdx) {
        for(int i : outStreamMap.keySet()) {
            if (i == exceptIdx)
                continue;
            try {
                outStreamMap.get(i).writeObject(message);
            } catch (Exception e) {
                System.err.println("multicastMessage: " + e);
            }
        }
    }

    public synchronized void unicastMessage(int socketIdx, Message message) {
        try {
            outStreamMap.get(socketIdx).writeObject(message);
        } catch (Exception e) {
            System.err.println("unicastMessage: " + e);
        }
    }

    // ----- ISIS -----
    // Create multicast message received from gentx.py
    public synchronized Message addNewEvent(String content) {
        // Create event message
        String currTime = NanoTimer.getTime();
        int priority = ++localPriority;
        String identifier = String.format("%s#%s", name, currTime);
        Message msg = new Message(content, currTime, name, priority, nodeID, 0, identifier);

        // Add event message to local memory
        priorityQueue.add(msg);
        identifierMessageMap.put(msg.getIdentifier(), msg);
        proposedPriorityReceivedMap.put(msg.getIdentifier(), new HashSet<Integer>(clientNameMap.keySet()));

        return msg;
    }

    // Multicast received transaction message
    public synchronized void multicastNewEvent(String content) {
        // If all other nodes failed, deliver message directly
        if (socketMap.isEmpty()) {
            accountDatabase.processTransaction(new Message(content, name, ++localPriority, nodeID));
            return;
        }

        // Create new event and set original priority for multicasting
        Message msg = addNewEvent(content);
        multicastMessage(msg);
    }

    // Propose priority based on event ID
    public synchronized void proposePriority(Message orgMsg, int socketIdx) {
        // Create reply message containing the proposed priority
        int priority = ++localPriority;
        Message newMsg = new Message(orgMsg.getContent(),
                                     orgMsg.getEventStartTime(),
                                     orgMsg.getOriginalSender(),
                                     priority,
                                     nodeID,
                                     1,
                                     orgMsg.getIdentifier());

        // Add new message to local memory
        priorityQueue.add(newMsg);
        identifierMessageMap.put(newMsg.getIdentifier(), newMsg);

        // Reply
        unicastMessage(socketIdx, newMsg);
    }

    // Update priority based on received proposal & deliver if possible
    public synchronized void finalizePriority(Message msg, int socketIdx) {
        // Update local memory
        String identifier = msg.getIdentifier();
        if (proposedPriorityReceivedMap.get(identifier).remove(socketIdx)) {
            Message orgMsg = identifierMessageMap.get(identifier);
            // If old priority less than new proposed priority, update message priority
            if (orgMsg.compareTo(msg) < 0) {
                identifierMessageMap.remove(identifier);
                priorityQueue.remove(orgMsg);
                orgMsg.setPriority(msg);
                identifierMessageMap.put(identifier, orgMsg);
                priorityQueue.add(orgMsg);
            }

            // If all proposals have been received, multicast final priority and set current message as deliverable
            if (proposedPriorityReceivedMap.get(identifier).isEmpty())
                processDeliverable(identifier);
        }
    }

    private synchronized void processDeliverable(String identifier) {
        Message orgMsg = identifierMessageMap.get(identifier);
        //remove current message from proposedPriorityReceivedMap
        proposedPriorityReceivedMap.remove(identifier);
        // Create acknowledge message carrying the final priority
        Message ackMsg = new Message(orgMsg.getContent(),
                orgMsg.getEventStartTime(),
                orgMsg.getOriginalSender(),
                orgMsg.getPriorityEventId(),
                orgMsg.getPriorityNodeId(),
                2,
                orgMsg.getIdentifier());
        // multicast
        multicastMessage(ackMsg);
        // deliver message
        deliverMessage(ackMsg);
    }

    // Accept final priority & deliver if possible
    public synchronized void acceptAndDeliver(Message ackMsg, int socketIdx) {
        // R-multicast
        if (!ackMsg.isMulticasted()) {
            ackMsg.setMulticasted();
            multicastMessage(ackMsg, socketIdx);
        }
        deliverMessage(ackMsg);
    }

    // Deliver message if possible
    public synchronized void deliverMessage(Message ackMsg) {
        // Update local priority
        localPriority = Math.max(localPriority, ackMsg.getPriorityEventId());

        String identifier = ackMsg.getIdentifier();
        if (identifierMessageMap.get(identifier) != null) {
            // Remove message from identifierMessageMap
            Message msg = identifierMessageMap.remove(identifier);
            // Update final priority and deliverable status in priorityQueue
            priorityQueue.remove(msg);
            msg.setPriority(ackMsg);
            msg.setDeliverable();
            priorityQueue.add(msg);

            // Deliver deliverable messages based on priority, if possible
            // Detect and remove timeout message
            while (!priorityQueue.isEmpty() && (priorityQueue.peek().isDeliverable() ||
                    failedTimeMap.containsKey(priorityQueue.peek().getOriginalSender()))) {
                Message currMsg = priorityQueue.peek();

                // Deliver deliverable message
                if (currMsg.isDeliverable()) {
                    priorityQueue.poll();
                    accountDatabase.processTransaction(currMsg);
                }

                // Remove message if timeout
                else if (Double.valueOf(NanoTimer.getTime()) - Double.valueOf(currMsg.getEventStartTime()) > TIMEOUT)
                    priorityQueue.poll();

                else {
                    // System.err.println("Timing out for transaction " + currMsg.getIdentifier());
                    break;
                }
            }
        }
    }
}
