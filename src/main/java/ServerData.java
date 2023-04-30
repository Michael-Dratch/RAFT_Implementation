import akka.actor.typed.ActorRef;

import java.io.Serializable;
import java.util.List;

public record ServerData(int currentTerm, ActorRef<RaftMessage> votedFor, List<Entry> log) implements Serializable {
}
