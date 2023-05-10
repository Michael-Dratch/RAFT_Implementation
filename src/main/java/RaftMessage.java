import akka.actor.typed.ActorRef;

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
                              int lastLogTerm
                              ) implements RaftMessage {}

    public record AppendEntriesResponse(int term, boolean success) implements RaftMessage {}

    public record RequestVoteResponse(int term, boolean voteGranted) implements RaftMessage {}

    public record SetGroupRefs(List<ActorRef<RaftMessage>> groupRefs) implements RaftMessage {}

    public record Start() implements RaftMessage{}

    public record TimeOut() implements RaftMessage {}

    public record HeartBeatTimeOut() implements RaftMessage {}

    public record Failure() implements RaftMessage {}



    public interface TestMessage extends RaftMessage{
        public record GetState(ActorRef<RaftMessage> sender) implements TestMessage{}
        public record GetStateResponse(int currentTerm,
                                       ActorRef<RaftMessage> votedFor,
                                       List<Entry> log,
                                       int commitIndex,
                                       int lastApplied) implements TestMessage {}
        public record GetCurrentTerm(ActorRef<RaftMessage> sender) implements TestMessage{}
        public record GetCurrentTermResponse(int currentTerm) implements TestMessage {}
        public record GetLog(ActorRef<RaftMessage> sender) implements TestMessage{}
        public record GetLogResponse(List<Entry> log) implements TestMessage{}
        public record GetCommitIndex(ActorRef<RaftMessage> sender) implements TestMessage {}
        public record GetCommitIndexResponse(int commitIndex) implements TestMessage {}
        public record SaveEntries(List<Entry> entries) implements TestMessage {}
        public record testFail() implements TestMessage {}
    }
}
