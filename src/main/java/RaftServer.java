import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorRefResolver;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.TimerScheduler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

abstract class RaftServer extends AbstractBehavior<RaftMessage> {

    protected TimerScheduler<RaftMessage> timer;

    protected Object TIMER_KEY = new Object();

    protected ServerDataManager dataManager;

    protected List<ActorRef<RaftMessage>> groupRefs;

    protected int currentTerm;

    protected ActorRef<RaftMessage> votedFor;

    protected List<Entry> log;

    protected int commitIndex;

    protected int lastApplied;


    protected RaftServer(ActorContext<RaftMessage> context,
                        TimerScheduler<RaftMessage> timers,
                        ServerDataManager dataManager,
                         int commitIndex,
                         int lastApplied){
        super(context);
        this.timer = timers;
        this.dataManager = dataManager;
        this.commitIndex = commitIndex;
        this.lastApplied = lastApplied;

        initializeDataManager(context, dataManager);
        initializeState(dataManager);
    }

    protected RaftServer(ActorContext<RaftMessage> context,
                         TimerScheduler<RaftMessage> timers,
                         ServerDataManager dataManager,
                         Object timerKey,
                         int commitIndex,
                         int lastApplied){
        super(context);
        this.timer = timers;
        this.TIMER_KEY = timerKey;
        this.dataManager = dataManager;
        this.commitIndex = commitIndex;
        this.lastApplied = lastApplied;

        initializeDataManager(context, dataManager);
        initializeState(dataManager);
    }

    protected void initializeDataManager(ActorContext<RaftMessage> context, ServerDataManager dataManager) {
        dataManager.setActorRefResolver(ActorRefResolver.get(context.getSystem()));
        dataManager.setServerID(context.getSelf().path().uid());
    }

    protected void initializeState(ServerDataManager dataManager) {
        this.currentTerm = this.dataManager.getCurrentTerm();
        this.votedFor = dataManager.getVotedFor();
        if (this.votedFor.equals(getContext().getSystem().deadLetters())){
            this.votedFor = null;
        }
        this.log = dataManager.getLog();
        this.groupRefs = dataManager.getGroupRefs();
    }

    protected void startTimer() {
        Random rand = new Random();
        rand.setSeed(System.currentTimeMillis());
        int randomNum = rand.nextInt(200);
        this.timer.startSingleTimer(TIMER_KEY, new RaftMessage.TimeOut(), Duration.ofMillis(200 + randomNum));
    }

    protected void handleTimeOut() {
        this.currentTerm++;
        this.dataManager.saveCurrentTerm(this.currentTerm);
        this.votedFor = getContext().getSelf();
        this.dataManager.saveVotedFor(this.votedFor);
        sendRequestVotesToAllNodes();
    }

    protected void updateCurrentTerm(int senderTerm) {
        if (senderTerm > this.currentTerm){this.currentTerm = senderTerm;}
        this.dataManager.saveCurrentTerm(this.currentTerm);
    }

    private void sendRequestVotesToAllNodes() {
        for (ActorRef<RaftMessage> ref: this.groupRefs){
            ref.tell(new RaftMessage.RequestVote(this.currentTerm,
                    getContext().getSelf(),
                    this.log.size()-1,
                    getLastLogTerm()));
        }
    }

    private int getLastLogTerm() {
        if (this.log.size() == 0) return -1;
        else return this.log.get(this.log.size() - 1).term();
    }
}
