import akka.actor.TypedActor;
import akka.actor.typed.*;
import akka.actor.typed.javadsl.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static java.lang.Math.min;


public class Follower extends RaftServer {

    public static Behavior<RaftMessage> create(ServerDataManager dataManager){
        return Behaviors.<RaftMessage>supervise(
            Behaviors.setup(context -> Behaviors.withTimers(timers -> new Follower(context, timers, dataManager)))
        ).onFailure(SupervisorStrategy.restart());
    }

    public static Behavior<RaftMessage> create(ServerDataManager dataManager,
                                               Object timerKey,
                                               int currentTerm,
                                               List<ActorRef<RaftMessage>> groupRefs,
                                               int commitIndex,
                                               int lastApplied){
        return Behaviors.<RaftMessage>supervise(
                Behaviors.setup(context -> Behaviors.withTimers(timers -> new Follower(context, timers, dataManager, timerKey, currentTerm, groupRefs, commitIndex, lastApplied)))
        ).onFailure(SupervisorStrategy.restart());
    }


    @Override
    public Receive<RaftMessage> createReceive() {
        return newReceiveBuilder().
                onMessage(RaftMessage.class, this::dispatch)
                .build();
    }


    protected Follower(ActorContext<RaftMessage> context, TimerScheduler<RaftMessage> timers, ServerDataManager dataManager){
        super(context, timers, dataManager, -1,-1);
    }

    protected Follower(ActorContext<RaftMessage> context,
                        TimerScheduler<RaftMessage> timers,
                        ServerDataManager dataManager,
                        Object timerKey,
                        int currentTerm,
                        List<ActorRef<RaftMessage>> groupRefs,
                        int commitIndex,
                        int lastApplied){
        super(context, timers, dataManager, commitIndex, lastApplied);
        this.currentTerm = currentTerm;
        this.dataManager.saveCurrentTerm(this.currentTerm);
        this.TIMER_KEY = timerKey;
        this.groupRefs = groupRefs;
        this.dataManager.saveGroupRefs(this.groupRefs);
        startTimer();
    }


    private Behavior<RaftMessage> dispatch(RaftMessage message){
        switch(message) {
            case RaftMessage.Start msg:
                startTimer();
                break;
            case RaftMessage.SetGroupRefs msg:
                this.groupRefs = msg.groupRefs();
                break;
            case RaftMessage.AppendEntries msg:
                handleAppendEntries(msg);
                break;
            case RaftMessage.RequestVote msg:
                handleRequestVote(msg);
                break;
            case RaftMessage.TimeOut msg:
                handleTimeOut();
                return Candidate.create(this.dataManager, this.TIMER_KEY, this.currentTerm, this.groupRefs, this.commitIndex, this.lastApplied);
            case RaftMessage.TestMessage msg:
                handleTestMessage(msg);
                break;
            case RaftMessage.Failure msg:   // Used to simulate node failure
                throw new RuntimeException("Test Failure");
            default:
                break;
        }
        return this;
    }

    private void handleAppendEntries(RaftMessage.AppendEntries msg){
        updateCurrentTerm(msg.term());
        if (doesAppendEntriesFail(msg)){
            sendAppendEntriesResponse(msg, false);
        } else {
            startTimer();
            processSuccessfulAppendEntries(msg);
            sendAppendEntriesResponse(msg, true);
        }
    }

    private boolean doesAppendEntriesFail(RaftMessage.AppendEntries msg) {
        if (msg.term() < this.currentTerm) return true;
        else if (msg.prevLogIndex() == -1) return false;
        else if (this.log.size() < msg.prevLogIndex()) return true;
        if (this.log.size() < 1) return false;
        else if(this.log.get(msg.prevLogIndex()).term() != msg.prevLogTerm()) return true;
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
            startTimer();
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

    protected void handleTestMessage(RaftMessage.TestMessage message){
        switch(message){
            case RaftMessage.TestMessage.GetBehavior msg:
                msg.sender().tell(new RaftMessage.TestMessage.GetBehaviorResponse("FOLLOWER"));
                break;
            default:
                break;
        }
    }

    protected void writeEntriesToLogFile(List<Entry> entries){
        this.dataManager.saveLog(entries);
    }
}
