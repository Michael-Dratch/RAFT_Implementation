import akka.actor.typed.ActorRef;

import java.util.HashMap;
import java.util.List;

public sealed interface RaftMessage {

    public record AppendEntries(int term,
                                ActorRef<RaftMessage> leaderRef,
                                int prevLogIndex,
                                int prevLogTerm,
                                List<Entry> entries ,
                                int leaderCommit
                                ) implements RaftMessage {}

    public record RequestVote(int term,
                              ActorRef<RaftMessage> candidateRef,
                              int lastLogIndex,
                              int lastLagTerm
                              ) implements RaftMessage {}

    public record AppendEntriesResponse(int term, boolean success) implements RaftMessage {}

    public record RequestVoteResponse(int term, boolean voteGranted) implements RaftMessage {}

}
