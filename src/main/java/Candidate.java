import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorRefResolver;
import akka.actor.typed.Behavior;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Candidate extends RaftServer {

    public static Behavior<RaftMessage> create(ServerDataManager dataManager,
                                               Object timerKey,
                                               int currentTerm,
                                               List<ActorRef<RaftMessage>> groupRefs,
                                               int commitIndex,
                                               int lastApplied){
        return Behaviors.<RaftMessage>supervise(
                Behaviors.setup(context -> Behaviors.withTimers(timers -> new Candidate(context, timers, dataManager, timerKey, currentTerm, groupRefs, commitIndex, lastApplied)))
        ).onFailure(SupervisorStrategy.restart());
    }

    @Override
    public Receive<RaftMessage> createReceive() {
        return newReceiveBuilder().onMessage(RaftMessage.class, this::dispatch).build();
    }

    private int votesReceived;
    private int votesRequired;

    protected Candidate(ActorContext<RaftMessage> context,
                        TimerScheduler<RaftMessage> timers,
                        ServerDataManager dataManager,
                        Object timerKey,
                        int currentTerm,
                        List<ActorRef<RaftMessage>> groupRefs,
                        int commitIndex,
                        int lastApplied){
        super(context, timers, dataManager, timerKey, commitIndex, lastApplied);
        this.currentTerm = currentTerm;
        this.dataManager.saveCurrentTerm(this.currentTerm);
        this.groupRefs = groupRefs;
        this.dataManager.saveGroupRefs(this.groupRefs);
        votesReceived = 0;
        votesRequired = this.groupRefs.size()/2;
        startTimer();
    }

    private Behavior<RaftMessage> dispatch(RaftMessage message){
        switch(message) {
            case RaftMessage.AppendEntries msg:
                if (msg.term() < this.currentTerm) sendFalseAppendEntriesResponse(msg);
                else return Follower.create(dataManager);
                break;
            case RaftMessage.RequestVote msg:
                if (msg.term() > this.currentTerm) return Follower.create(this.dataManager);
                else sendRequestVoteFailResponse(msg);
                break;
            case RaftMessage.RequestVoteResponse msg:
                if (msg.term() > this.currentTerm) return Follower.create(this.dataManager);
                else {
                    handleRequestVoteResponse(msg);
                    if (votesReceived >= votesRequired) return getLeaderBehavior();
                }
                break;
            case RaftMessage.TimeOut msg:
                handleTimeOut();
                votesReceived = 0;
                break;
            case RaftMessage.Failure msg:   // Used to simulate node failure
                throw new RuntimeException("Test Failure");
            case RaftMessage.TestMessage msg:
                handleTestMessage(msg);
                break;
            default:
                break;
        }
        return this;
    }

    private Behavior<RaftMessage> getLeaderBehavior() {
        return Leader.create(this.dataManager,
                this.TIMER_KEY,
                this.currentTerm,
                this.groupRefs,
                this.commitIndex,
                this.lastApplied);
    }

    private void handleRequestVoteResponse(RaftMessage.RequestVoteResponse msg) {
        if (msg.voteGranted() == true){
            votesReceived++;
        }
    }

    private void sendFalseAppendEntriesResponse(RaftMessage.AppendEntries msg) {
        msg.leaderRef().tell(new RaftMessage.AppendEntriesResponse(this.currentTerm, false));
    }

    private void handleTestMessage(RaftMessage.TestMessage message) {
        switch(message) {
            case RaftMessage.TestMessage.GetBehavior msg:
                msg.sender().tell(new RaftMessage.TestMessage.GetBehaviorResponse("CANDIDATE"));
                break;
            default:
                break;
        }
    }

    private void sendRequestVoteFailResponse(RaftMessage.RequestVote msg) {
        msg.candidateRef().tell(new RaftMessage.RequestVoteResponse(this.currentTerm, false));
    }
}

