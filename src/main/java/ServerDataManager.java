import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorRefResolver;
import akka.actor.typed.ActorSystem;

import java.util.List;

public interface ServerDataManager {

    public void saveLog(List<Entry> log);
    public void saveCurrentTerm(int term);
    public void saveVotedFor(ActorRef<RaftMessage> actorRef);
    public List<Entry> getLog();
    public int getCurrentTerm();
    public ActorRef<RaftMessage> getVotedFor();
    public void setServerID(int ID);
    public void setActorRefResolver(ActorRefResolver refResolver);
}
