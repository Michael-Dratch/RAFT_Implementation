import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.Receive;

public class Candidate extends AbstractBehavior<RaftMessage> {
    @Override
    public Receive<RaftMessage> createReceive() {
        return newReceiveBuilder().onMessage(RaftMessage.class, this::dispatch).build();
    }

    private Behavior<RaftMessage> dispatch(RaftMessage message){
        switch (message){
            default:
                break;
        }
        return this;
    }
}

