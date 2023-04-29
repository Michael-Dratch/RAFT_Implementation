import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class Follower extends AbstractBehavior<RaftMessage> {

    public static Behavior<RaftMessage> create(){
        return Behaviors.setup(context -> {
            return new Follower(context);
        });
    }

    private Follower(ActorContext<RaftMessage> context){
        super(context);
    }

    @Override
    public Receive<RaftMessage> createReceive() {
        return null;
    }
}
