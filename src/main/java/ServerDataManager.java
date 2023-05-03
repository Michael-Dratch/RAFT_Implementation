import akka.actor.typed.ActorRef;

import java.util.List;

public interface ServerDataManager {

    public void saveEntriesToLog(List<Entry> entries);
    public void saveCurrentTerm(int term);
    public void saveVotedFor(ActorRef<RaftMessage> actorRef);
    public List<Entry> getLog();
    public int getCurrentTerm();
    public ActorRef<RaftMessage> getVotedFor();
}
