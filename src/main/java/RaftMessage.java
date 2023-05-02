import akka.actor.typed.ActorRef;

import java.util.HashMap;
import java.util.List;

public interface RaftMessage {

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

    public record TimeOut() implements RaftMessage {}

    public record HeartBeatTimeOut() implements RaftMessage {}


    public interface TestMessage extends RaftMessage{
        public record GetLog(ActorRef<RaftMessage> sender) implements TestMessage{}
        public record GetLogResponse(List<Entry> log) implements TestMessage{}
        public record GetCommitIndex(ActorRef<RaftMessage> sender) implements TestMessage {}
        public record GetCommitIndexResponse(int commitIndex) implements TestMessage {}
    }
}
