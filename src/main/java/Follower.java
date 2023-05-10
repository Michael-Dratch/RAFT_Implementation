import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorRefResolver;
import akka.actor.typed.Behavior;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.*;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.min;




public class Follower extends AbstractBehavior<RaftMessage> {

    public static Behavior<RaftMessage> create(ServerDataManager dataManager){
        return Behaviors.<RaftMessage>supervise(
            Behaviors.setup(context -> Behaviors.withTimers(timers -> new Follower(context, timers, dataManager)))
        ).onFailure(SupervisorStrategy.restart());
    }

// constructor without restart supervision strategy for debugging
//    public static Behavior<RaftMessage> create(ServerDataManager dataManager){
//        return Behaviors.setup(context -> {
//                    return Behaviors.withTimers(timers -> {
//                        return new Follower(context, timers, dataManager);
//                    });
//                });
//    }



    @Override
    public Receive<RaftMessage> createReceive() {
        return newReceiveBuilder().
                onMessage(RaftMessage.class, this::dispatch)
                .build();
    }

    protected TimerScheduler<RaftMessage> timer;

    protected ServerDataManager dataManager;

    protected int currentTerm;

    protected ActorRef<RaftMessage> votedFor;

    protected List<Entry> log;

    protected int commitIndex;

    protected int lastApplied;

    protected Follower(ActorContext<RaftMessage> context, TimerScheduler<RaftMessage> timers, ServerDataManager dataManager){
        super(context);
        this.timer = timers;
        this.dataManager = dataManager;
        this.commitIndex = -1;
        this.lastApplied = -1;
        this.currentTerm = 0;
        this.votedFor = null;
        this.log = new ArrayList<Entry>();

        initializeDataManager(context, dataManager);
        initializeState(dataManager);
    }

    private void initializeDataManager(ActorContext<RaftMessage> context, ServerDataManager dataManager) {
        dataManager.setActorRefResolver(ActorRefResolver.get(context.getSystem()));
        dataManager.setServerID(context.getSelf().path().uid());
        System.out.println(ActorRefResolver.get(context.getSystem()));
    }

    private void initializeState(ServerDataManager dataManager) {
        this.currentTerm = dataManager.getCurrentTerm();
        this.votedFor = dataManager.getVotedFor();
        if (this.votedFor.equals(getContext().getSystem().deadLetters())){
            this.votedFor = null;
        }
        this.log = dataManager.getLog();
    }


    private Behavior<RaftMessage> dispatch(RaftMessage msg){
        switch(msg) {
            case RaftMessage.AppendEntries message:
                handleAppendEntries(message);
                break;
            case RaftMessage.RequestVote message:
                handleRequestVote(message);
                break;
            case RaftMessage.TestMessage message:
                handleTestMessage(message);
                break;
            case RaftMessage.Failure message:   // Used to simulate node failure
                throw new RuntimeException("Test Failure");
            default:
                break;
        }
        return this;
    }


    private void handleAppendEntries(RaftMessage.AppendEntries msg){
        updateCurrentTerm(msg.term());
        if (doesAppendEntriesFail(msg)){
            msg.leaderRef().tell(new RaftMessage.AppendEntriesResponse(this.currentTerm, false));
        } else {
            processSuccessfulAppendEntries(msg);
            msg.leaderRef().tell(new RaftMessage.AppendEntriesResponse(this.currentTerm, true));
        }
    }

    private void updateCurrentTerm(int senderTerm) {
        if (senderTerm > this.currentTerm){this.currentTerm = senderTerm;}
        this.dataManager.saveCurrentTerm(this.currentTerm);
    }

    private boolean doesAppendEntriesFail(RaftMessage.AppendEntries msg) {
        if (msg.term() < this.currentTerm){return true;}
        else if (msg.prevLogIndex() == -1){return false;}
        else if (this.log.size() < msg.prevLogIndex()){return true;}
        else if(this.log.get(msg.prevLogIndex()).term() != msg.prevLogTerm()){return true;}
        return false;
    }

    private void processSuccessfulAppendEntries(RaftMessage.AppendEntries msg) {
        int entryCount = msg.entries().size();

        for (int i = 0; i < entryCount; i++){
            if (entryIndexExceedsLogSize(msg, i)){
                addRemainingEntriesToLog(msg, i);
                break;
            }
            else if (isConflictBetweenMessageAndLogEntry(msg, i)){
                removeConflictingLogEntries(msg.prevLogIndex(), i);
                addRemainingEntriesToLog(msg, i);
                break;
            }
        }
        updateCommitIndex(msg);
        this.dataManager.saveLog(this.log);
    }

    private boolean entryIndexExceedsLogSize(RaftMessage.AppendEntries msg, int i) {
        return msg.prevLogIndex() + i > log.size() - 1 || msg.prevLogIndex() + 1 + i >= log.size();
    }

    private void addRemainingEntriesToLog(RaftMessage.AppendEntries msg, int i) {
        this.log.addAll(msg.entries().subList(i, msg.entries().size()));
        this.log = new ArrayList<>(this.log);
    }

    private boolean isConflictBetweenMessageAndLogEntry(RaftMessage.AppendEntries msg, int i) {
        return msg.entries().get(i).term() != this.log.get(msg.prevLogIndex() + 1 + i).term();
    }

    private void removeConflictingLogEntries(int prevLogIndex, int i) {
        this.log = this.log.subList(0, prevLogIndex + i + 1);
    }

    private void updateCommitIndex(RaftMessage.AppendEntries msg) {
        if (msg.leaderCommit() > this.commitIndex){
            this.commitIndex = min(msg.leaderCommit(), this.log.size() - 1);
        }
    }

    private void handleRequestVote(RaftMessage.RequestVote msg) {
        updateCurrentTerm(msg.term());
        if (doesRequestVoteFail(msg)) {
            returnRequestVoteResponse(msg, false);
        }else{
            this.votedFor = msg.candidateRef();
            this.dataManager.saveVotedFor(this.votedFor);
            returnRequestVoteResponse(msg, true);
        }
    }

    private boolean doesRequestVoteFail(RaftMessage.RequestVote msg){
        if (msg.term() < this.currentTerm) return true;

        else if (votedFor != null) {
            return true;}

        else if (this.log.size() == 0) return false;

        else if (msg.lastLogTerm() < this.log.get(log.size()-1).term()) return true;

        else if (msg.lastLogTerm() == this.log.get(log.size()-1).term()) {
            if (msg.lastLogIndex() < this.log.size() - 1) return true;
        }
        return false;
    }

    private void returnRequestVoteResponse(RaftMessage.RequestVote msg, boolean voteGranted) {
        msg.candidateRef().tell(new RaftMessage.RequestVoteResponse(this.currentTerm, voteGranted));
    }


    protected void handleTestMessage(RaftMessage.TestMessage msg){
        //implemented in TestableFollower Class
        return;
    }

    protected void writeEntriesToLogFile(List<Entry> entries){
        this.dataManager.saveLog(entries);
    }
}
