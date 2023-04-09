// raft in progress

package raft

//
// this is an outline of the API that raft must expose to
// the service (or tester). see comments below for
// each of these functions for more details.
//
// rf = Make(...)
//   create a new Raft server.
// rf.Start(command interface{}) (index, term, isleader)
//   start agreement on a new log entry
// rf.GetState() (term, isLeader)
//   ask a Raft for its current term, and whether it thinks it is leader
// ApplyMsg
//   each time a new entry is committed to the log, each Raft peer
//   should send an ApplyMsg to the service (or tester)
//   in the same server.
//

import (
	"math/rand"
	"raft/labrpc"
	"sync"
	"sync/atomic"
	"time"
)

const (
	Empty = -1

	Leader    = 0
	Candidate = 1
	Follower  = 2

	minTimeout        = 350 * time.Millisecond
	maxTimeout        = 600 * time.Millisecond
	TimeoutRange      = 250 * time.Millisecond
	heartbeatInterval = 120 * time.Millisecond
)

type LogEntry struct {
	Term    int
	Command interface{}
}

type AppendEntriesArgs struct {
	Term         int // leader's term
	LeaderID     int
	PrevLogIndex int // index of log entry immediately preceding new ones

	PrevLogTerm int        // term of prevLogIndex entry
	Entries     []LogEntry // log entries to store (empty for heartbeat; may send more than one for efficiency)

	LeaderCommit int // leader's commitIndex
}

type AppendEntriesReply struct {
	Term        int  // currentTerm, for leader to update itself
	Success     bool // true if follower entry matches prevLogIndex and prevLogTerm
	Conflict    bool // true if follower entry does not match at nextIndex
	NextIndex   int  // nextIndex of follower's log
	LastLogTerm int  // the term of its last log
}

// as each Raft peer becomes aware that successive log entries are
// committed, the peer should send an ApplyMsg to the service (or
// tester) on the same server, via the applyCh passed to Make(). set
// CommandValid to true to indicate that the ApplyMsg contains a newly
// committed log entry.
type ApplyMsg struct {
	CommandValid bool
	Command      interface{}
	CommandIndex int
}

// A Go object implementing a single Raft peer.
type Raft struct {
	mu    sync.Mutex          // Lock to protect shared access to this peer's state
	peers []*labrpc.ClientEnd // RPC end points of all peers
	me    int                 // this peer's index into peers[]
	dead  int32               // set by Kill()

	// Your data here (2A, 2B).
	// Look at the paper's Figure 2 for a description of what
	// state a Raft server must maintain.
	// You may also need to add other state, as per your implementation.

	// persistent state
	currentTerm int
	votedFor    int
	log         []LogEntry

	// volatile state
	commitIndex int
	lastApplied int

	// volatile state on leaders
	nextIndex  []int //for each server, index of the next log entry to send to that server (initialized to leader last log index + 1)
	matchIndex []int //for each server, index of highest log entry known to be replicated on server (initialized to 0, increases monotonically)

	membership      int // 0: leader, 1: candidate, 2: follower
	electionTimeout time.Duration
	electionTime    time.Time
	applyCh         chan ApplyMsg
	applyChecker    chan int
}

// return currentTerm and whether this server
// believes it is the leader.
func (rf *Raft) GetState() (int, bool) {

	var term int
	var isleader bool
	// Your code here (2A).
	term = rf.currentTerm
	isleader = rf.membership == Leader
	return term, isleader
}

// example RequestVote RPC arguments structure.
// field names must start with capital letters!
type RequestVoteArgs struct {
	// Your data here (2A, 2B).
	Term         int // candidate's term
	CandidateId  int // candidate requesting vote
	LastLogIndex int // index of candidate's last log entry
	LastLogTerm  int // term of candidate's last log entry
}

// example RequestVote RPC reply structure.
// field names must start with capital letters!
type RequestVoteReply struct {
	// Your data here (2A).
	Term        int  // currentTerm, for candidate to update itself
	VoteGranted bool // true means candidate received vote
}

// example RequestVote RPC handler.
func (rf *Raft) RequestVote(args *RequestVoteArgs, reply *RequestVoteReply) {
	// Your code here (2A, 2B).
	// Read the fields in "args",
	// and accordingly assign the values for fields in "reply".
	rf.mu.Lock()
	defer rf.mu.Unlock()
	DPrintf("Server %d term %d received RequestVote from Server %d term %d", rf.me, rf.currentTerm, args.CandidateId, args.Term)
	if args.Term < rf.currentTerm {
		reply.VoteGranted = false
		reply.Term = rf.currentTerm
		// return
	} else {
		// rf.resetElectionTimer() // CHECK: do we need to randomize the election timeout?
		if args.Term > rf.currentTerm {
			DPrintf("Server %d term %d: received RequestVote from Server %d term %d, update term",
				rf.me, rf.currentTerm, args.CandidateId, args.Term)
			rf.currentTerm = args.Term
			rf.votedFor = Empty
			rf.membership = Follower
		}
		if rf.votedFor == Empty || rf.votedFor == args.CandidateId {
			if args.LastLogTerm > rf.log[len(rf.log)-1].Term ||
				(args.LastLogTerm == rf.log[len(rf.log)-1].Term &&
					args.LastLogIndex >= len(rf.log)-1) {
				rf.resetElectionTimer() // CHECK: do we need to randomize the election timeout?
				rf.votedFor = args.CandidateId
				reply.VoteGranted = true
			} else {
				reply.VoteGranted = false
			}

			// code snippet for 2A
			// rf.votedFor = args.CandidateId
			// reply.VoteGranted = true
			// // rf.membership = Follower
		} else {
			reply.VoteGranted = false
		}
		reply.Term = rf.currentTerm
	}
}

// TODO: log handling
// CHECK: when to reset timeout
func (rf *Raft) AppendEntries(args *AppendEntriesArgs, reply *AppendEntriesReply) {
	rf.mu.Lock()
	defer rf.mu.Unlock()
	DPrintf("Server %d term %d: received AppendEntries %v from Server %d term %d\n", rf.me, args.Term, args, args.LeaderID, args.Term)
	reply.Term = rf.currentTerm
	if args.Term < rf.currentTerm {
		// received AppendEntries from lower term, ignore
		reply.Success = false
		return
	} else {
		rf.resetElectionTimer()
		if rf.membership == Candidate {
			rf.membership = Follower
		}
		reply.Success = false // CHECK: is this correct
		if args.Term > rf.currentTerm {
			DPrintf("Server %d term %d: received AppendEntries from higher term, change to term %d\n", rf.me, rf.currentTerm, args.Term)
			// reply.Success = false	// CHECK: is this correct
			rf.currentTerm = args.Term
			rf.membership = Follower
			rf.votedFor = Empty
			return
		}
		if rf.membership == Candidate {
			rf.membership = Follower
		}
		DPrintf("%v", rf.log)
		DPrintf("1: %d %d", args.PrevLogIndex, len(rf.log))

		if args.PrevLogIndex > len(rf.log)-1 {
			DPrintf("1: %d", args.PrevLogIndex)
			reply.Conflict = true
			reply.NextIndex = len(rf.log)
			// reply.LastLogTerm = rf.log[len(rf.log) - 1].Term
			reply.LastLogTerm = -1
			return
		}
		DPrintf("2: %d %d %d", args.PrevLogTerm, args.PrevLogIndex, len(rf.log))
		if args.PrevLogTerm != rf.log[args.PrevLogIndex].Term {
			DPrintf("2: %d %d", args.PrevLogTerm, rf.log[args.PrevLogIndex].Term)
			reply.Conflict = true
			conflictTerm := rf.log[args.PrevLogIndex].Term
			for i := args.PrevLogIndex; i >= 0; i-- {
				if rf.log[i].Term != conflictTerm {
					reply.NextIndex = i + 1
					break
				}
			}
			reply.LastLogTerm = conflictTerm
			return
		}
		// args.PrevLogTerm == rf.log[args.PrevLogIndex].Term
		rf.log = append(rf.log[0:args.PrevLogIndex+1], args.Entries...)
		DPrintf("Server %d term %d: log updated to %v\n", rf.me, rf.currentTerm, rf.log)
		reply.NextIndex = len(rf.log)
		reply.Success = true
		// TODO: handle commit
		if args.LeaderCommit > rf.commitIndex {
			rf.commitIndex = args.LeaderCommit
			rf.applyChecker <- 1
		}
	}
}

// example code to send a RequestVote RPC to a server.
// server is the index of the target server in rf.peers[].
// expects RPC arguments in args.
// fills in *reply with RPC reply, so caller should
// pass &reply.
// the types of the args and reply passed to Call() must be
// the same as the types of the arguments declared in the
// handler function (including whether they are pointers).
//
// The labrpc package simulates a lossy network, in which servers
// may be unreachable, and in which requests and replies may be lost.
// Call() sends a request and waits for a reply. If a reply arrives
// within a timeout interval, Call() returns true; otherwise
// Call() returns false. Thus Call() may not return for a while.
// A false return can be caused by a dead server, a live server that
// can't be reached, a lost request, or a lost reply.
//
// Call() is guaranteed to return (perhaps after a delay) *except* if the
// handler function on the server side does not return.  Thus there
// is no need to implement your own timeouts around Call().
//
// look at the comments in ../labrpc/labrpc.go for more details.
//
// if you're having trouble getting RPC to work, check that you've
// capitalized all field names in structs passed over RPC, and
// that the caller passes the address of the reply struct with &, not
// the struct itself.
func (rf *Raft) sendRequestVote(server int, args *RequestVoteArgs, reply *RequestVoteReply) bool {
	ok := rf.peers[server].Call("Raft.RequestVote", args, reply)
	return ok
}

func (rf *Raft) sendAppendEntries(server int, args *AppendEntriesArgs, reply *AppendEntriesReply) bool {
	ok := rf.peers[server].Call("Raft.AppendEntries", args, reply)
	return ok
}

func (rf *Raft) startElection() {
	rf.mu.Lock()
	rf.currentTerm++
	rf.membership = Candidate
	rf.votedFor = rf.me
	rf.resetElectionTimerRand()

	RequestVoteArgs := RequestVoteArgs{
		Term:         rf.currentTerm,
		CandidateId:  rf.me,
		LastLogIndex: len(rf.log) - 1,
		LastLogTerm:  rf.log[len(rf.log)-1].Term,
	}
	DPrintf("Server %d starts election in term %d", rf.me, rf.currentTerm)
	rf.mu.Unlock()

	voteChan := make(chan bool, len(rf.peers)) // channel to receive votes
	// send RequestVote RPCs to all other servers
	for i := 0; i < len(rf.peers); i++ {
		if i != rf.me {
			go func(i int, voteChan chan bool) {
				reply := RequestVoteReply{}
				rf.sendRequestVote(i, &RequestVoteArgs, &reply)
				rf.mu.Lock()
				defer rf.mu.Unlock()
				if rf.membership == Candidate && reply.VoteGranted {
					// reply is true
					voteChan <- true
					DPrintf("Server %d received vote from server %d", rf.me, i)
				} else {
					// reply is false, check if the reply's term is larger
					voteChan <- false
					if reply.Term > rf.currentTerm {
						rf.currentTerm = reply.Term
						rf.membership = Follower
						rf.votedFor = Empty
						DPrintf("Server %d: term %d: received larger term %d from server %d, become follower", rf.me, rf.currentTerm, reply.Term, i)
					}
				}
			}(i, voteChan)
		}
	}

	replyCount := 1
	voteCount := 1
	for replyCount < len(rf.peers) {
		vote := <-voteChan
		replyCount++
		if vote {
			voteCount++
			if rf.membership != Candidate {
				return
			}
			if rf.membership == Candidate && voteCount > len(rf.peers)/2 {
				DPrintf("Server %d becomes leader in term %d", rf.me, rf.currentTerm)
				rf.membership = Leader
				// When a leader elected, initialize all nextIndex values to the index just after the last one in its log
				nextLogIndex := len(rf.log)
				for i := 0; i < len(rf.peers); i++ {
					rf.nextIndex[i] = nextLogIndex
					rf.matchIndex[i] = 0
				}
				rf.resetElectionTimerRand()
				rf.startHeartBeat()
				return
			}
		}
		if replyCount-voteCount >= len(rf.peers)/2 {
			// cannot be the leader, end election
			rf.mu.Lock()
			rf.membership = Follower
			rf.mu.Unlock()
			return
		}
	}
}

// TODO: log handling; check lock
func (rf *Raft) startHeartBeat() {
	for i := 0; i < len(rf.peers); i++ {
		if i == rf.me {
			continue
		}
		if rf.membership != Leader {
			return
		}
		peerNextIndex := rf.nextIndex[i]
		if peerNextIndex == 0 {
			peerNextIndex = 1
		} else if peerNextIndex > len(rf.log) {
			peerNextIndex = len(rf.log) - 1
		}
		prevLogIndex := peerNextIndex - 1
		args := AppendEntriesArgs{
			Term:         rf.currentTerm,
			LeaderID:     rf.me,
			PrevLogTerm:  rf.log[prevLogIndex].Term,
			PrevLogIndex: prevLogIndex,
			Entries:      append([]LogEntry{}, rf.log[peerNextIndex:]...),
			LeaderCommit: rf.commitIndex,
		}
		DPrintf("Server %d term %d: prepare AppendEntriesArgs %v", rf.me, rf.currentTerm, args)
		go func(i int) {
			AppendEntriesReply := AppendEntriesReply{}
			ret := rf.sendAppendEntries(i, &args, &AppendEntriesReply)
			if ret {
				rf.mu.Lock()
				// reply from larger term
				if rf.currentTerm < AppendEntriesReply.Term {
					DPrintf("Server %d term %d: received reply of larger term %d from server %d, become follower",
						rf.me, rf.currentTerm, AppendEntriesReply.Term, i)
					rf.currentTerm = AppendEntriesReply.Term
					rf.membership = Follower
					rf.votedFor = Empty
					rf.resetElectionTimer()
					rf.mu.Unlock()
					return
				}
				// no longer leader or term not match
				if rf.membership != Leader || AppendEntriesReply.Term != rf.currentTerm {
					rf.mu.Unlock()
					return
				}

				if AppendEntriesReply.Success {
					// DPrintf("success: %d %d %d", prevLogIndex, len(args.Entries), rf.nextIndex[i])
					if AppendEntriesReply.NextIndex > rf.nextIndex[i] {
						rf.nextIndex[i] = AppendEntriesReply.NextIndex
						rf.matchIndex[i] = rf.nextIndex[i] - 1
					}
					DPrintf("Server %d term %d: success AppendEntries reply from server %d, nextIndex %d and matchIndex %d",
						rf.me, rf.currentTerm, i, rf.nextIndex[i], rf.matchIndex[i])
				} else if AppendEntriesReply.Conflict {
					// may not need this
					DPrintf("Server %d term %d to server %d nextIndex %d: failed AppendEntries reply nextIndex %d",
						rf.me, rf.currentTerm, i, rf.nextIndex[i], AppendEntriesReply.NextIndex)
					if AppendEntriesReply.LastLogTerm == -1 {
						// follower's log length is smaller than prevLogIndex
						rf.nextIndex[i] = AppendEntriesReply.NextIndex
					} else {
						// follower's log length is larger than prevLogIndex
						// find the last index of log before the conflicting term
						j := prevLogIndex
						for ; j > 0; j-- {
							if rf.log[j].Term < AppendEntriesReply.LastLogTerm {
								break
							}
						}
						if j < 0 {
							rf.nextIndex[i] = AppendEntriesReply.NextIndex
						} else {
							rf.nextIndex[i] = j + 1
						}
					}
				} else {
					if rf.nextIndex[i] > 1 {
						rf.nextIndex[i]--
					}
				}
				rf.mu.Unlock()
				rf.tryCommit()
			}
		}(i)
	}

}

// TODO: check
func (rf *Raft) tryCommit() {
	if rf.membership != Leader {
		return
	}

	for logIndex := rf.commitIndex + 1; logIndex < len(rf.log); logIndex++ {
		if rf.log[logIndex].Term != rf.currentTerm {
			continue
		}
		serverCount := 1
		for serverId := 0; serverId < len(rf.peers); serverId++ {
			if serverId != rf.me && rf.matchIndex[serverId] >= logIndex {
				serverCount++
			}
			if serverCount > len(rf.peers)/2 {
				rf.commitIndex = logIndex
				rf.applyChecker <- 1 // notify apply thread to apply logs
				break
			}
		}
	}
}

// TODO: check
func (rf *Raft) startApplyLogs() {
	rf.mu.Lock()
	defer rf.mu.Unlock()
	var msgArray []ApplyMsg
	msgArray = make([]ApplyMsg, 0)
	for i := rf.lastApplied + 1; i <= rf.commitIndex; i++ {
		msg := ApplyMsg{
			CommandValid: true,
			Command:      rf.log[i].Command,
			CommandIndex: i,
		}
		msgArray = append(msgArray, msg)
	}
	DPrintf("Server %d term %d: apply logs: %v", rf.me, rf.currentTerm, msgArray)

	for _, msg := range msgArray {
		rf.applyCh <- msg
		rf.lastApplied = msg.CommandIndex
	}
}

// the service using Raft (e.g. a k/v server) wants to start
// agreement on the next command to be appended to Raft's log. if this
// server isn't the leader, returns false. otherwise start the
// agreement and return immediately. there is no guarantee that this
// command will ever be committed to the Raft log, since the leader
// may fail or lose an election. even if the Raft instance has been killed,
// this function should return gracefully.
//
// the first return value is the index that the command will appear at
// if it's ever committed. the second return value is the current
// term. the third return value is true if this server believes it is
// the leader.
func (rf *Raft) Start(command interface{}) (int, int, bool) {
	rf.mu.Lock()
	defer rf.mu.Unlock()
	if rf.membership != Leader {
		term := rf.currentTerm
		return -1, term, false
	}
	term := rf.currentTerm
	isLeader := rf.membership == Leader
	LogEntry := LogEntry{
		Term:    term,
		Command: command,
	}

	rf.log = append(rf.log, LogEntry)
	index := len(rf.log) - 1
	DPrintf("Server %d term %d Start(): append log entry %v at index %d", rf.me, rf.currentTerm, LogEntry, index)
	return index, term, isLeader
}

// the tester doesn't halt goroutines created by Raft after each test,
// but it does call the Kill() method. your code can use killed() to
// check whether Kill() has been called. the use of atomic avoids the
// need for a lock.
//
// the issue is that long-running goroutines use memory and may chew
// up CPU time, perhaps causing later tests to fail and generating
// confusing debug output. any goroutine with a long-running loop
// should call killed() to check whether it should stop.
func (rf *Raft) Kill() {
	atomic.StoreInt32(&rf.dead, 1)
	// Your code here, if desired.
}

func (rf *Raft) killed() bool {
	z := atomic.LoadInt32(&rf.dead)
	return z == 1
}

func (rf *Raft) resetElectionTimer() {
	rf.electionTime = time.Now().Add(rf.electionTimeout)
}

func (rf *Raft) resetElectionTimerRand() {
	rf.electionTimeout = time.Duration(rand.Int63n(int64(TimeoutRange)) + int64(minTimeout))
	rf.electionTime = time.Now().Add(rf.electionTimeout)
}

func (rf *Raft) run() {
	for !rf.killed() {
		time.Sleep(heartbeatInterval)
		if rf.membership == Leader {
			// send heartbeat to all followers
			// DPrintf("Server %d sends heartbeat in term %d", rf.me, rf.currentTerm)
			rf.resetElectionTimer()
			rf.startHeartBeat()
		}
		if time.Now().After(rf.electionTime) && rf.membership != Leader {
			// start election
			rf.startElection()
		}
	}
}

// goroutine to check if there are logs to apply
func (rf *Raft) apply() {
	for range rf.applyChecker {
		time.Sleep(10 * time.Millisecond)
		rf.startApplyLogs()
	}
}

// the service or tester wants to create a Raft server. the ports
// of all the Raft servers (including this one) are in peers[]. this
// server's port is peers[me]. all the servers' peers[] arrays
// have the same order. applyCh is a channel on which the
// tester or service expects Raft to send ApplyMsg messages.
// Make() must return quickly, so it should start goroutines
// for any long-running work.
func Make(peers []*labrpc.ClientEnd, me int,
	applyCh chan ApplyMsg) *Raft {
	rf := &Raft{}
	rf.peers = peers
	rf.me = me
	rf.applyCh = applyCh
	rf.currentTerm = 0
	rf.votedFor = Empty
	rf.membership = Follower
	rf.log = make([]LogEntry, 1)
	rf.commitIndex = 0
	rf.lastApplied = 0
	rf.nextIndex = make([]int, len(peers))
	rf.matchIndex = make([]int, len(peers))
	rf.applyChecker = make(chan int, 1000)
	rf.resetElectionTimerRand()
	// rf.messageChan = make(chan Message, 100)
	// rf.requireVote = false

	DPrintf("Server %d is created", rf.me)

	go rf.apply()

	go rf.run()

	// Your initialization code here (2A, 2B).

	return rf
}
