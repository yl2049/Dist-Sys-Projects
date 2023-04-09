import java.io.Serializable;

public class Message implements Serializable {

    private String content;
    private boolean deliverable;
    private int[] priorityNodeID;
    private String eventStartTime;
    private int type; // {0: message, 1: propose priority message, 2: agreed priority message}
    private String originalSender;
    private String multicastSender;
    private String identifier;
    private boolean isMulticasted;



    public Message(String content, String eventStartTime, String originalSender, int priority, int nodeID, int type, String identifier) {
        this.content = content;
        this.eventStartTime = eventStartTime;
        this.originalSender = originalSender;
        this.priorityNodeID = new int[]{priority, nodeID};
        this.multicastSender = null;
        this.identifier = identifier;
        this.type = type;
        this.isMulticasted = false;
        this.deliverable = false;
    }

    public Message(String content, String originalSender, int priority, int nodeID) {
        this.content = content;
        this.eventStartTime = NanoTimer.getTime();
        this.originalSender = originalSender;
        this.identifier = String.format("%s#%s", originalSender, this.eventStartTime);
        this.priorityNodeID = new int[]{priority, nodeID};
    }

    // Ascending order
    public int compareTo(Message message) {
        if(priorityNodeID[0] < message.priorityNodeID[0]) {
            return -1;
        } else if(priorityNodeID[0] == message.priorityNodeID[0]) {
            return priorityNodeID[1] - message.priorityNodeID[1];
        }
        return 1;
    }

    public String getContent() {
        return this.content;
    }

    public int getType() {
        return this.type;
    }

    public String getIdentifier() {
        return this.identifier;
    }

    public String getOriginalSender() {
        return this.originalSender;
    }

    // Get priority
    public int getPriorityEventId() { return this.priorityNodeID[0]; }
    public int getPriorityNodeId() {
        return this.priorityNodeID[1];
    }

    public void setPriority(Message msg) {
        priorityNodeID[0] = msg.getPriorityEventId();
        priorityNodeID[1] = msg.getPriorityNodeId();
    }

    public void setDeliverable() {
        this.deliverable = true;
    }

    public boolean isDeliverable() {
        return this.deliverable;
    }

    public void setMulticasted() {
        this.isMulticasted = true;
    }

    public boolean isMulticasted() {
        return this.isMulticasted;
    }

    public String getEventStartTime() {
        return eventStartTime;
    }
}
