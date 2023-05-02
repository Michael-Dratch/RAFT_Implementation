import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.min;


public class Follower extends AbstractBehavior<RaftMessage> {

    public static Behavior<RaftMessage> create(){
        return Behaviors.setup(context -> {
            return Behaviors.withTimers(timers -> {
                return new Follower(context, timers);
            });
        });
    }



    @Override
    public Receive<RaftMessage> createReceive() {
        return newReceiveBuilder().
                onMessage(RaftMessage.class, this::dispatch)
                .build();
    }

    protected TimerScheduler<RaftMessage> timer;

    protected int currentTerm;

    protected ActorRef<RaftMessage> votedFor;

    protected List<Entry> log;

    protected int commitIndex;

    protected int lastApplied;

    protected Follower(ActorContext<RaftMessage> context, TimerScheduler<RaftMessage> timers){
        super(context);
        timer = timers;
        commitIndex = -1;
        lastApplied = -1;
        currentTerm = 0;
        votedFor = null;
        log = new ArrayList<Entry>();
    }


    private Behavior<RaftMessage> dispatch(RaftMessage msg){
        switch(msg) {
            case RaftMessage.AppendEntries message:
                handleAppendEntries(message);
                break;
            case RaftMessage.TestMessage message:
                handleTestMessage(message);
                break;
            default:
                break;
        }
        return this;
    }


    private void handleAppendEntries(RaftMessage.AppendEntries msg){
        if (doesAppendEntriesFail(msg)){
            msg.leaderRef().tell(new RaftMessage.AppendEntriesResponse(this.currentTerm, false));
        } else {
            processSuccessfulAppendEntries(msg);
            msg.leaderRef().tell(new RaftMessage.AppendEntriesResponse(this.currentTerm, true));
        }

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
            if (isConflictBetweenMessageAndLogEntry(msg, i)){
                removeConflictingLogEntries(msg.prevLogIndex(), i);
                addRemainingEntriesToLog(msg, i);
                break;
            }
        }
        updateCommitIndex(msg);
    }

    private boolean entryIndexExceedsLogSize(RaftMessage.AppendEntries msg, int i) {
        return msg.prevLogIndex() + i > log.size() - 1;
    }

    private void addRemainingEntriesToLog(RaftMessage.AppendEntries msg, int i) {
        this.log.addAll(msg.entries().subList(i, msg.entries().size()));
    }

    private boolean isConflictBetweenMessageAndLogEntry(RaftMessage.AppendEntries msg, int i) {
        return msg.entries().get(i).term() != this.log.get(msg.prevLogIndex() + i + 1).term();
    }

    private void removeConflictingLogEntries(int prevLogIndex, int i) {
        this.log = this.log.subList(0, prevLogIndex + i + 1);
    }

    private void updateCommitIndex(RaftMessage.AppendEntries msg) {
        if (msg.leaderCommit() > this.commitIndex){
            this.commitIndex = min(msg.leaderCommit(), this.log.size() - 1);
        }
    }

    protected void handleTestMessage(RaftMessage.TestMessage msg){
        //implemented in TestableFollower Class
        return;
    }
}
