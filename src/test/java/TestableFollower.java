import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.TimerScheduler;

import java.util.List;

public class TestableFollower extends Follower{

    public static Behavior<RaftMessage> create(int currentTerm, ActorRef<RaftMessage> votedFor, List<Entry> log){
        return Behaviors.setup(context -> {
            return Behaviors.withTimers(timers -> {
                return new TestableFollower(context, timers, currentTerm, votedFor, log);
            });
        });
    }

    private TestableFollower(ActorContext<RaftMessage> context, TimerScheduler<RaftMessage> timers, int currentTerm, ActorRef<RaftMessage> votedFor, List<Entry> log){
        super(context, timers);
        this.timer = timers;
        this.commitIndex = 0;
        this.lastApplied = 0;
        this.currentTerm = currentTerm;
        this.votedFor = votedFor;
        this.log = log;
    }

    protected void handleTestMessage(RaftMessage.TestMessage msg){
        switch (msg) {
            case RaftMessage.TestMessage.GetLog message:
                message.sender().tell(new RaftMessage.TestMessage.GetLogResponse(this.log));
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + msg);
        }
    }
}
