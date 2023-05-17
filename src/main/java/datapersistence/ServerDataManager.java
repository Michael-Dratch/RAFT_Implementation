package datapersistence;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorRefResolver;
import akka.actor.typed.ActorSystem;

import java.util.List;
import statemachine.Entry;
import messages.RaftMessage;
public interface ServerDataManager {

    public void saveLog(List<Entry> log);
    public void saveCurrentTerm(int term);
    public void saveVotedFor(ActorRef<RaftMessage> actorRef);
    public void saveGroupRefs(List<ActorRef<RaftMessage>> groupRefs);
    public List<Entry> getLog();
    public int getCurrentTerm();
    public ActorRef<RaftMessage> getVotedFor();
    public List<ActorRef<RaftMessage>> getGroupRefs();
    public void setServerID(int ID);
    public void setActorRefResolver(ActorRefResolver refResolver);
}
