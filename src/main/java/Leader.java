import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.javadsl.TimerScheduler;

import java.util.List;

public class Leader extends RaftServer{

    public static Behavior<RaftMessage> create(ServerDataManager dataManager,
                                               Object timerKey,
                                               int currentTerm,
                                               List<ActorRef<RaftMessage>> groupRefs,
                                               int commitIndex,
                                               int lastApplied){
        return Behaviors.<RaftMessage>supervise(
                Behaviors.setup(context -> Behaviors.withTimers(timers -> new Leader(context, timers, dataManager, timerKey, currentTerm, groupRefs, commitIndex, lastApplied)))
        ).onFailure(SupervisorStrategy.restart());
    }

    protected Leader(ActorContext<RaftMessage> context,
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
        startTimer();
    }

    @Override
    public Receive<RaftMessage> createReceive() {
        return newReceiveBuilder().onMessage(RaftMessage.class, this::dispatch).build();
    }

    private Behavior<RaftMessage> dispatch(RaftMessage message){
        switch(message) {
            case RaftMessage.AppendEntries msg:
                break;
            case RaftMessage.RequestVote msg:
                break;
            case RaftMessage.TimeOut msg:
                handleTimeOut();
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

    @Override
    protected void startTimer(){
        //set leader timer to be less than follower time-outs, is not random

    }

    private void handleTestMessage(RaftMessage.TestMessage message) {
        switch(message) {
            case RaftMessage.TestMessage.GetBehavior msg:
                msg.sender().tell(new RaftMessage.TestMessage.GetBehaviorResponse("LEADER"));
                break;
            default:
                break;
        }
    }
}
