import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorRefResolver;
import akka.actor.typed.Behavior;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.*;

import javax.swing.plaf.nimbus.State;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Candidate extends RaftServer {

    public static Behavior<RaftMessage> create(ServerDataManager dataManager,
                                               StateMachine stateMachine,
                                               FailFlag failFlag,
                                               Object timerKey,
                                               int currentTerm,
                                               List<ActorRef<RaftMessage>> groupRefs,
                                               int commitIndex,
                                               int lastApplied){
        return Behaviors.<RaftMessage>supervise(
                Behaviors.setup(context -> Behaviors.withTimers(timers -> new Candidate(context, timers, dataManager, stateMachine, failFlag, timerKey, currentTerm, groupRefs, commitIndex, lastApplied)))
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
                        StateMachine stateMachine,
                        FailFlag failFlag,
                        Object timerKey,
                        int currentTerm,
                        List<ActorRef<RaftMessage>> groupRefs,
                        int commitIndex,
                        int lastApplied){
        super(context, timers, dataManager, stateMachine, failFlag, timerKey, commitIndex, lastApplied);
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
                if (msg.term() < this.currentTerm) sendAppendEntriesResponse(msg, false);
                else return Follower.create(dataManager, this.stateMachine, this.failFlag);
                break;
            case RaftMessage.RequestVote msg:
                if (msg.term() > this.currentTerm) return Follower.create(this.dataManager, this.stateMachine, this.failFlag);
                else sendRequestVoteFailResponse(msg);
                break;
            case RaftMessage.RequestVoteResponse msg:
                if (msg.term() > this.currentTerm) return Follower.create(this.dataManager, this.stateMachine, this.failFlag);
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
                this.stateMachine,
                this.TIMER_KEY,
                this.failFlag,
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

    private void handleTestMessage(RaftMessage.TestMessage message) {
        switch(message) {
            case RaftMessage.TestMessage.GetBehavior msg:
                msg.sender().tell(new RaftMessage.TestMessage.GetBehaviorResponse("CANDIDATE"));
                break;
            case RaftMessage.TestMessage.GetStateMachineCommands msg:
                msg.sender().tell(new RaftMessage.TestMessage.GetStateMachineCommandsResponse(this.stateMachine.getCommands()));
                break;
            default:
                break;
        }
    }

    private void sendRequestVoteFailResponse(RaftMessage.RequestVote msg) {
        msg.candidateRef().tell(new RaftMessage.RequestVoteResponse(this.currentTerm, false));
    }
}

