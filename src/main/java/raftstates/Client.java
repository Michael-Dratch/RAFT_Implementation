package raftstates;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.*;
import messages.ClientMessage;
import messages.RaftMessage;
import statemachine.StringCommand;

import java.time.Duration;
import java.util.List;
import java.util.Random;

public class Client extends AbstractBehavior<ClientMessage> {
    public static Behavior<ClientMessage> create(List<ActorRef<RaftMessage>> serverRefs, List<String> commandQueue){
        return Behaviors.<ClientMessage>supervise(
                Behaviors.setup(context -> Behaviors.withTimers(timers -> new Client(context, timers, serverRefs, commandQueue)))
        ).onFailure(SupervisorStrategy.restart());
    }

    @Override
    public Receive<ClientMessage> createReceive() {
        return newReceiveBuilder()
                .onMessage(ClientMessage.class, this::dispatch)
                .build();
    }

    protected Client(ActorContext<ClientMessage> context,
                        TimerScheduler<ClientMessage> timers,
                        List<ActorRef<RaftMessage>> serverRefs,
                        List<String> commandQueue){
        super(context);
        this.timer = timers;
        this.serverRefs = serverRefs;
        this.commandQueue = commandQueue;
        this.nextRequest = 0;
        startTimer();
    }

    private List<ActorRef<RaftMessage>> serverRefs;

    private Object TIMER_KEY = new Object();

    protected TimerScheduler<ClientMessage> timer;

    private List<String> commandQueue;

    private int nextRequest;

    private Behavior<ClientMessage> dispatch(ClientMessage message){
        switch (message) {
            case ClientMessage.Start msg:
                start();
                break;
            case ClientMessage.TimeOut msg:
                handleTimeOut();
                break;
            case ClientMessage.ClientResponse msg:
                handleClientResponse(msg);
                break;
            default:
                break;
        }
        return this;


    }

    private void start(){
        sendNextRequestToRandomServer();
    }

    private void sendNextRequestToRandomServer() {
        int randomServer = getRandomServer();
        this.serverRefs.get(randomServer).tell(getRequestMessage(this.nextRequest, this.commandQueue.get(this.nextRequest)));
    }

    private void handleClientResponse(ClientMessage.ClientResponse response){
        if (response.success()){
            this.nextRequest++;
            sendNextRequestToRandomServer();
        } else{
            sendNextRequestToRandomServer();
        }
    }

    private int getRandomServer() {
        Random rand = new Random();
        rand.setSeed(System.currentTimeMillis());
       return rand.nextInt(serverRefs.size());
    }

    private void handleTimeOut(){
        sendNextRequestToRandomServer();
    }

    private void startTimer() {
        this.timer.startSingleTimer(TIMER_KEY, new ClientMessage.TimeOut(), Duration.ofMillis(400));
    }

    private RaftMessage.ClientRequest getRequestMessage(int commandId, String command){
        return new RaftMessage.ClientRequest(getContext().getSelf(),  new StringCommand(getContext().getSelf().path().uid(), commandId,command));
    }
}

