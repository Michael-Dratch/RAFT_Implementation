package messages;

public interface ClientMessage {
    public record Start() implements ClientMessage{}
    public record ClientResponse(boolean success) implements ClientMessage{}
    public record TimeOut() implements ClientMessage {}
}
