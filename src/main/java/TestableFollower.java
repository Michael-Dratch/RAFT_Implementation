import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.TimerScheduler;

import java.util.List;

public class TestableFollower extends Follower {


    public static Behavior<RaftMessage> create() {
        return Behaviors.<RaftMessage>supervise(
                Behaviors.setup(context -> {
                    return Behaviors.withTimers(timers -> {
                        return new TestableFollower(context, timers);
                    });
                })).onFailure(SupervisorStrategy.restart());
    }

    private TestableFollower(ActorContext<RaftMessage> context, TimerScheduler<RaftMessage> timers) {
        super(context, timers, new ServerFileWriter());
    }

    public static Behavior<RaftMessage> create(int currentTerm, List<Entry> log) {
        return Behaviors.<RaftMessage>supervise(
            Behaviors.setup(context -> {
                return Behaviors.withTimers(timers -> {
                    return new TestableFollower(context, timers, currentTerm, log);
                });
            })).onFailure(SupervisorStrategy.restart());
    }

   //  constructor without restart supervision strategy for debugging
//        public static Behavior<RaftMessage> create(int currentTerm, List<Entry> log) {
//        return Behaviors.setup(context -> {
//                return Behaviors.withTimers(timers -> {
//                    return new TestableFollower(context, timers, currentTerm, log);
//                });
//            });
//
//    }

    private TestableFollower(ActorContext<RaftMessage> context, TimerScheduler<RaftMessage> timers, int currentTerm, List<Entry> log) {
        super(context, timers, new ServerFileWriter(), currentTerm);
        this.timer = timers;
        this.log = log;
    }

    public static Behavior<RaftMessage> create(int currentTerm, List<Entry> log, int commitIndex) {
        return Behaviors.<RaftMessage>supervise(
            Behaviors.setup(context -> {
                return Behaviors.withTimers(timers -> {
                    return new TestableFollower(context, timers, currentTerm, log, commitIndex);
                });
            })).onFailure(SupervisorStrategy.restart());
    }

//    public static Behavior<RaftMessage> create(int currentTerm, List<Entry> log, int commitIndex) {
//        return Behaviors.setup(context -> {
//                    return Behaviors.withTimers(timers -> {
//                        return new TestableFollower(context, timers, currentTerm, log, commitIndex);
//                    });
//                });
//    }
    private TestableFollower(ActorContext<RaftMessage> context, TimerScheduler<RaftMessage> timers, int currentTerm, List<Entry> log, int commitIndex) {

        super(context, timers, new ServerFileWriter(), currentTerm);
        this.timer = timers;
        this.commitIndex = commitIndex;
        this.log = log;
    }

    public static Behavior<RaftMessage> create(int currentTerm, ActorRef<RaftMessage> votedFor, List<Entry> log, int commitIndex, int lastApplied) {
        return Behaviors.<RaftMessage>supervise(
            Behaviors.setup(context -> {
                return Behaviors.withTimers(timers -> {
                    return new TestableFollower(context, timers, currentTerm, votedFor, log, commitIndex, lastApplied);
                });
        })).onFailure(SupervisorStrategy.restart());
    }


    private TestableFollower(ActorContext<RaftMessage> context,
                             TimerScheduler<RaftMessage> timers,
                             int currentTerm,
                             ActorRef<RaftMessage> votedFor,
                             List<Entry> log,
                             int commitIndex,
                             int lastApplied) {

        super(context, timers, new ServerFileWriter(), currentTerm);
        this.timer = timers;
        this.votedFor = votedFor;
        this.log = log;
        this.commitIndex = commitIndex;
        this.lastApplied = lastApplied;
    }

    protected void handleTestMessage(RaftMessage.TestMessage message) {
        switch (message) {
            case RaftMessage.TestMessage.GetLog msg:
                msg.sender().tell(new RaftMessage.TestMessage.GetLogResponse(this.log));
                break;
            case RaftMessage.TestMessage.GetCommitIndex msg:
                msg.sender().tell(new RaftMessage.TestMessage.GetCommitIndexResponse(this.commitIndex));
                break;
            case RaftMessage.TestMessage.SaveEntries msg:
                this.dataManager.saveLog(msg.entries());
                break;
            case RaftMessage.TestMessage.GetState msg:
                msg.sender().tell(new RaftMessage.TestMessage.GetStateResponse(this.currentTerm,
                                                                                this.votedFor,
                                                                                this.log,
                                                                                this.commitIndex,
                                                                                this.lastApplied));
                break;
            case RaftMessage.TestMessage.testFail msg:
                throw new RuntimeException("Test Failure");
            default:
                break;
        }
    }



//    private void initializeDataFiles(){
//        File logFile = new File(getLogFileName());
//
//        if (logFile.exists()){
//        } else {
//            try {
//                File directory = new File(logFile.getParent());
//                if(!directory.exists()){
//                    directory.mkdirs();
//                }
//                logFile.createNewFile();
//            } catch(IOException e){
//                throw new RuntimeException(e);
//            }
//        }

//    }
//    private void writeEntriesToFile(List<Entry> entries){
//        try {
//            File logFile = getLogFile();
//            initializeFileIfNotExist(logFile);
//            ObjectOutputStream oos = createObjectInputStream(logFile);
//
//            for (Entry e: entries){
//                oos.writeObject(e);
//            }
//
//            oos.close();
//        }catch(IOException e){
//            throw new RuntimeException(e);
//        }
//    }

//    private static ObjectOutputStream createObjectInputStream(File logFile) throws IOException {
//        FileOutputStream fos = new FileOutputStream(logFile);
//        return new ObjectOutputStream(fos);
//    }
//
//    private void initializeFileIfNotExist(File logFile) {
//        if (!logFile.exists()){
//            initializeDataFiles();
//        }
//    }
//
//    private File getLogFile(){
//        String UID = String.valueOf(this.getContext().getSelf().path().uid());
//        String PATH = "./data/" + UID + "/log.ser";
//       return new File(PATH);
//    }

}