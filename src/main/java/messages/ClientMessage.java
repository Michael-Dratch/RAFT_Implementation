package messages;

import akka.actor.typed.ActorRef;

public interface ClientMessage {
    public record Start() implements ClientMessage{}
    public record ClientResponse(boolean success) implements ClientMessage{}
    public record TimeOut() implements ClientMessage {}
    public record AlertWhenFinished(ActorRef<ClientMessage> sender) implements ClientMessage{}
    public record Finished() implements ClientMessage {}
}
