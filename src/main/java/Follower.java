import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;

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

    private TimerScheduler<RaftMessage> timer;

    private Follower(ActorContext<RaftMessage> context, TimerScheduler<RaftMessage> timers){
        super(context);
        timer = timers;
    }

    private Behavior<RaftMessage> dispatch(RaftMessage msg){
        return this;
    }
}
