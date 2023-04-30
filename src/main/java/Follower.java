import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;

import java.util.ArrayList;
import java.util.List;


public class Follower extends AbstractBehavior<RaftMessage> {

    public static Behavior<RaftMessage> create(){
        return Behaviors.setup(context -> {
            return Behaviors.withTimers(timers -> {
                return new Follower(context, timers);
            });
        });
    }



    @Override
    public Receive<RaftMessage> createReceive() {
        return newReceiveBuilder().
                onMessage(RaftMessage.class, this::dispatch)
                .build();
    }

    protected TimerScheduler<RaftMessage> timer;

    protected int currentTerm;

    protected ActorRef<RaftMessage> votedFor;

    protected List<Entry> log;

    protected int commitIndex;

    protected int lastApplied;

    protected Follower(ActorContext<RaftMessage> context, TimerScheduler<RaftMessage> timers){
        super(context);
        timer = timers;
        commitIndex = -1;
        lastApplied = -1;
        currentTerm = 0;
        votedFor = null;
        log = new ArrayList<Entry>();
    }


    private Behavior<RaftMessage> dispatch(RaftMessage msg){
        switch(msg) {
            case RaftMessage.AppendEntries message:
                handleAppendEntries(message);
                break;
            case RaftMessage.TestMessage message:
                handleTestMessage(message);
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + msg);
        }
        return this;
    }
    
    
    private void handleAppendEntries(RaftMessage.AppendEntries msg){
        if (doesAppendEntriesFail(msg)){
            msg.leaderRef().tell(new RaftMessage.AppendEntriesResponse(this.currentTerm, false));
        }

        this.log.clear();
        msg.leaderRef().tell(new RaftMessage.AppendEntriesResponse(this.currentTerm, true));
    }

    private boolean doesAppendEntriesFail(RaftMessage.AppendEntries msg) {
        if (msg.term() < this.currentTerm){return true;}
        else if (msg.prevLogIndex() == -1){return false;}
        else if (this.log.size() < msg.prevLogIndex()){return true;}
        else if(this.log.get(msg.prevLogIndex()).term() != msg.prevLogTerm()){return true;}
        return false;
    }

    protected void handleTestMessage(RaftMessage.TestMessage msg){
        //implemented in TestableFollower Class
        return;
    }
}
