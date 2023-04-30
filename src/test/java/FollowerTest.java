import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.BehaviorTestKit;
import akka.actor.testkit.typed.javadsl.TestInbox;
import akka.actor.typed.ActorRef;
import org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.events.Event;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FollowerTest {

    BehaviorTestKit<RaftMessage> follower;
    TestInbox<RaftMessage> inbox;
    ActorRef<RaftMessage> inboxRef;

    private static void assertCorrectAppendEntriesResponse(RaftMessage response, int expectedTerm, boolean expectedSuccess) {
        if (response instanceof RaftMessage.AppendEntriesResponse){
            RaftMessage.AppendEntriesResponse msg = (RaftMessage.AppendEntriesResponse) response;
            assertEquals(expectedSuccess, msg.success());
            assertEquals(expectedTerm, msg.term());
        }else{
            throw new AssertionError("Incorrect Response Message Type");
        }
    }

    @Before
    public void setUp(){
        inbox = TestInbox.create();
        inboxRef = inbox.getRef();
    }

    @Test
    public void AppendEntryFromEarlierTermRepliesFalseAndCurrentTerm(){
        int followerTerm = 1;
        follower = BehaviorTestKit.create(TestableFollower.create(followerTerm, inboxRef, new ArrayList<Entry>()));
        follower.run(new RaftMessage.AppendEntries(0, inboxRef, 0, 0, new ArrayList<Entry>(), 0));
        RaftMessage response = inbox.receiveMessage();
        assertCorrectAppendEntriesResponse(response, followerTerm, false);
    }

    @Test
    public void FollowerHasNoEntryAtPrevLogIndexReturnsFalse(){
        follower = BehaviorTestKit.create(Follower.create());
        follower.run(new RaftMessage.AppendEntries(0, inboxRef, 1, 0, new ArrayList<Entry>(), 0));
        assertCorrectAppendEntriesResponse(inbox.receiveMessage(), 0, false);
    }

    @Test
    public void FollowerEntryAtPrevLogIndexDoesntMatchPrevLogTermReturnsFalseResponse(){
        List<Entry> followerLog = new ArrayList<Entry>();
        followerLog.add(new Entry(1, new StringCommand(0,0,"")));
        follower = BehaviorTestKit.create(TestableFollower.create(0, inboxRef, followerLog));
        follower.run(new RaftMessage.AppendEntries(0, inboxRef, 1, 2, new ArrayList<Entry>(), 0));
        assertCorrectAppendEntriesResponse(inbox.receiveMessage(), 0, false);
    }
}
