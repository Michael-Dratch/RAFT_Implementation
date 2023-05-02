import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.TimerScheduler;

import java.util.List;

public class TestableFollower extends Follower{

    public static Behavior<RaftMessage> create(int currentTerm, List<Entry> log){
        return Behaviors.setup(context -> {
            return Behaviors.withTimers(timers -> {
                return new TestableFollower(context, timers, currentTerm, log);
            });
        });
    }

    private TestableFollower(ActorContext<RaftMessage> context, TimerScheduler<RaftMessage> timers, int currentTerm, List<Entry> log){
        super(context, timers);
        this.timer = timers;
        this.commitIndex = 0;
        this.lastApplied = 0;
        this.currentTerm = currentTerm;
        this.votedFor = null;
        this.log = log;
    }

    public static Behavior<RaftMessage> create(int currentTerm, List<Entry> log, int commitIndex){
        return Behaviors.setup(context -> {
            return Behaviors.withTimers(timers -> {
                return new TestableFollower(context, timers, currentTerm, log, commitIndex);
            });
        });
    }

    private TestableFollower(ActorContext<RaftMessage> context, TimerScheduler<RaftMessage> timers, int currentTerm, List<Entry> log, int commitIndex){
        super(context, timers);
        this.timer = timers;
        this.commitIndex = commitIndex;
        this.lastApplied = 0;
        this.currentTerm = currentTerm;
        this.votedFor = null;
        this.log = log;
    }

    protected void handleTestMessage(RaftMessage.TestMessage message){
        switch (message) {
            case RaftMessage.TestMessage.GetLog msg:
                msg.sender().tell(new RaftMessage.TestMessage.GetLogResponse(this.log));
                break;
            case RaftMessage.TestMessage.GetCommitIndex msg:
                msg.sender().tell(new RaftMessage.TestMessage.GetCommitIndexResponse(this.commitIndex));
                break;
            default:
                break;
        }
    }
}
