import akka.actor.typed.*;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.javadsl.TimerScheduler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Leader extends RaftServer{

    public static Behavior<RaftMessage> create(ServerDataManager dataManager,
                                               StateMachine stateMachine,
                                               Object timerKey,
                                               FailFlag failFlag,
                                               int currentTerm,
                                               List<ActorRef<RaftMessage>> groupRefs,
                                               int commitIndex,
                                               int lastApplied){
            return Behaviors.<RaftMessage>supervise(
                    Behaviors.setup(context -> Behaviors.withTimers(timers -> new Leader(context, timers, dataManager, stateMachine, failFlag, timerKey, currentTerm, groupRefs, commitIndex, lastApplied)))
            ).onFailure(SupervisorStrategy.restart());
    }

    protected Leader(ActorContext<RaftMessage> context,
                        TimerScheduler<RaftMessage> timers,
                        ServerDataManager dataManager,
                        StateMachine stateMachine,
                        FailFlag failFlag,
                        Object timerKey,
                        int currentTerm,
                        List<ActorRef<RaftMessage>> groupRefs,
                        int commitIndex,
                        int lastApplied){
        super(context, timers, dataManager, stateMachine, failFlag, timerKey, commitIndex, lastApplied);
        this.currentTerm = currentTerm;
        this.dataManager.saveCurrentTerm(this.currentTerm);
        this.groupRefs = groupRefs;
        this.dataManager.saveGroupRefs(this.groupRefs);
        checkFailure();
        initializeNextIndex();
        initializeMatchIndex();
        logEntryClientRefs = new HashMap<>();
        startTimer();
    }

    private void checkFailure() {
    }


    private void initializeNextIndex() {
        this.nextIndex = new HashMap<>();
        for (ActorRef<RaftMessage> node: this.groupRefs){
            nextIndex.put(node, this.log.size());
        }
    }

    private void initializeMatchIndex() {
        this.matchIndex = new HashMap<>();
        for (ActorRef<RaftMessage> node: this.groupRefs){
            matchIndex.put(node, -1);
        }
    }

    @Override
    public Receive<RaftMessage> createReceive() {
        return newReceiveBuilder()
                .onMessage(RaftMessage.class, this::dispatch)
                .onSignal(PreRestart.class, this::handlePreRestart)
                .build();
    }



    private HashMap<ActorRef<RaftMessage>, Integer> nextIndex;

    private HashMap<ActorRef<RaftMessage>, Integer> matchIndex;

    private HashMap<Integer, ActorRef<ClientMessage>> logEntryClientRefs;

    private Behavior<RaftMessage> dispatch(RaftMessage message){
        if (!this.failFlag.failed) {
            switch (message) {
                case RaftMessage.ClientRequest msg:
                    handleClientRequest(msg);
                    break;
                case RaftMessage.AppendEntries msg:
                    if (msg.term() < this.currentTerm) sendAppendEntriesResponse(msg, false);
                    else return Follower.create(dataManager, stateMachine, failFlag);
                    break;
                case RaftMessage.RequestVote msg:
                    if (msg.term() < this.currentTerm) sendRequestVoteResponse(msg, false);
                    else return Follower.create(dataManager, stateMachine, failFlag);
                    break;
                case RaftMessage.AppendEntriesResponse msg:
                    if (msg.term() > this.currentTerm) return Follower.create(dataManager, stateMachine, failFlag);
                    handleRequestVoteResponse(msg);
                    break;
                case RaftMessage.RequestVoteResponse msg:
                    if (msg.term() > this.currentTerm) return Follower.create(dataManager, stateMachine, failFlag);
                    break;
                case RaftMessage.TimeOut msg:
                    handleTimeOut();
                    break;
                case RaftMessage.Failure msg:   // Used to simulate node failure
                    throw new RuntimeException("Test Failure");
                case RaftMessage.TestMessage msg:
                    handleTestMessage(msg);
                    break;
                default:
                    break;
            }
            return this;
        } else {
            resetTransientState();
            this.failFlag.failed = false;
            getContext().getSelf().tell(message);
            return Follower.create(this.dataManager, this.stateMachine, this.failFlag);
        }
    }

    private void handleClientRequest(RaftMessage.ClientRequest msg) {
        Entry entry = new Entry(this.currentTerm, msg.command());
        this.log.add(entry);
        this.dataManager.saveLog(this.log);
        this.logEntryClientRefs.put(this.log.indexOf(entry), msg.clientRef());
        for (ActorRef<RaftMessage> node: groupRefs){
            sendAppendEntriesToFollower(node);
        }
    }

    private void sendAppendEntriesToFollower(ActorRef<RaftMessage> follower) {
        int nodeNextIndex = this.nextIndex.get(follower);
        int prevLogTerm = getPrevLogTerm(nodeNextIndex - 1);
        List<Entry> entries = this.log.subList(nodeNextIndex, this.log.size());
        follower.tell(new RaftMessage.AppendEntries(this.currentTerm, getContext().getSelf(), nodeNextIndex - 1, prevLogTerm, entries, this.commitIndex));
    }

    private int getPrevLogTerm(int prevLogIndex) {
        if (prevLogIndex < 0){
            return -1;
        } else {
            return this.log.get(prevLogIndex).term();
        }

    }

    private void handleRequestVoteResponse(RaftMessage.AppendEntriesResponse msg) {
        if (msg.success() == true){
            matchIndex.put(msg.sender(), this.log.size());
            if (isEntryIndexSuccessfullyReplicated(msg.matchIndex())) updateCommitIndex(msg.matchIndex());
        } else {
            nextIndex.put(msg.sender(), nextIndex.get(msg.sender()) - 1);
            sendAppendEntriesToFollower(msg.sender());
        }
    }

    private boolean isEntryIndexSuccessfullyReplicated(int entryIndex) {
        int numReplicas = getEntryReplicaCount(entryIndex);
        if (numReplicas >= (groupRefs.size()/2)) return true;
        else return false;
    }

    private void updateCommitIndex(int entryIndex) {
        if (entryIndex <= this.commitIndex) return;
        getContext().getLog().info("Up to entry " + entryIndex + " committed");
        this.commitIndex = entryIndex;
        int prevCommit = this.lastApplied;
        this.applyCommittedEntriesToStateMachine();
        sendClientResponsesForNewCommittedRequests(prevCommit, this.commitIndex);
    }

    private void sendClientResponsesForNewCommittedRequests(int oldCommit, int newCommit) {
        for (int i = oldCommit + 1; i <= newCommit; i++){
            ActorRef<ClientMessage> clientRef = this.logEntryClientRefs.get(i);
            clientRef.tell(new ClientMessage.ClientResponse(true));
        }
    }

    private int getEntryReplicaCount(int entryIndex) {
        int numReplicas = 0;
        for (Integer match: matchIndex.values()){
            if (match >= entryIndex) numReplicas++;
        }
        return numReplicas;
    }

    @Override
    protected void startTimer(){
        this.timer.startSingleTimer(TIMER_KEY, new RaftMessage.TimeOut(), Duration.ofMillis(150));
    }

    @Override
    protected void handleTimeOut() {
        for (ActorRef<RaftMessage> node: groupRefs){
            sendHeartBeatMessage(node);
        }
        startTimer();
    }

    private void sendHeartBeatMessage(ActorRef<RaftMessage> node) {
        node.tell(new RaftMessage.AppendEntries(this.currentTerm, getContext().getSelf(), -1, -1, new ArrayList<>(), this.commitIndex));
    }


    private void handleTestMessage(RaftMessage.TestMessage message) {
        switch(message) {
            case RaftMessage.TestMessage.GetBehavior msg:
                msg.sender().tell(new RaftMessage.TestMessage.GetBehaviorResponse("LEADER"));
                break;
            case RaftMessage.TestMessage.SaveEntries msg:
                this.log = msg.entries();
                this.dataManager.saveLog(this.log);
                this.initializeNextIndex();
                break;
            case RaftMessage.TestMessage.GetStateMachineCommands msg:
                msg.sender().tell(new RaftMessage.TestMessage.GetStateMachineCommandsResponse(this.stateMachine.getCommands()));
                break;
            case RaftMessage.TestMessage.GetState msg:
                msg.sender().tell(new RaftMessage.TestMessage.GetStateResponse(this.currentTerm, this.votedFor, this.log, this.commitIndex, this.lastApplied));
                break;
            default:
                break;
        }
    }
}
