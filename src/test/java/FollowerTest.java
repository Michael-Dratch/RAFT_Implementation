import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.BehaviorTestKit;
import akka.actor.testkit.typed.javadsl.TestInbox;
import akka.actor.typed.ActorRef;
import org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

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

    private void assertCorrectResponseAndLogAfterAppendEntries(int expectedTerm, boolean expectedSuccess, List<Entry> expectedLog, RaftMessage appendEntries) {
        follower.run(appendEntries);
        follower.run(new RaftMessage.TestMessage.GetLog(inbox.getRef()));
        List<RaftMessage> responses = inbox.getAllReceived();
        assertCorrectAppendEntriesResponse(responses.get(0), expectedTerm, expectedSuccess);
        assertCorrectLogResponse(responses.get(1), expectedLog);
    }

    private static void assertCorrectLogResponse(RaftMessage response, List<Entry> expectedLog) {
        if (response instanceof RaftMessage.TestMessage.GetLogResponse){
            RaftMessage.TestMessage.GetLogResponse msg = ( RaftMessage.TestMessage.GetLogResponse) response;
            assertEquals(expectedLog.size(), msg.log().size());
            for (int i = 0; i < msg.log().size(); i++){
                assertTrue(msg.log().get(i).equals(expectedLog.get(i)));
            }
        }else{
            throw new AssertionError("Incorrect Response Message Type");
        }
    }

    private static Entry createEntry(int term) {
        return new Entry(term, new StringCommand(0, 0, ""));
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
        followerLog.add(createEntry(1));
        follower = BehaviorTestKit.create(TestableFollower.create(3, inboxRef, followerLog));
        follower.run(new RaftMessage.AppendEntries(3, inboxRef, 0, 2, new ArrayList<Entry>(), 0));
        assertCorrectAppendEntriesResponse(inbox.receiveMessage(), 3, false);
    }

    @Test
    public void testCorrectSuccessMessageReturnedFromSuccessfulAppendEntries(){
        follower = BehaviorTestKit.create(TestableFollower.create(1, inboxRef, new ArrayList<Entry>()));
        follower.run(new RaftMessage.AppendEntries(1, inboxRef, -1, 0, new ArrayList<Entry>(), 0));
        assertCorrectAppendEntriesResponse(inbox.receiveMessage(), 1, true);
    }

    @Test
    public void FollowerHasTwoEntriesPrevLogIsConsistentNoNewEntriesReturnsSuccessAndLogStaysTheSame(){
        List<Entry> followerLog = new ArrayList<>();
        List<Entry> expectedLog = new ArrayList<>();
        Entry e1 = createEntry(1);
        followerLog.add(e1);
        followerLog.add(e1);
        expectedLog.add(e1);
        expectedLog.add(e1);
        follower = BehaviorTestKit.create(TestableFollower.create(1, inboxRef, followerLog));
        RaftMessage appendEntries = new RaftMessage.AppendEntries(1, inboxRef, -1, 0, new ArrayList<Entry>(), 0);
        assertCorrectResponseAndLogAfterAppendEntries(1, true, expectedLog, appendEntries);
    }

    @Test
    public void FirstEntryConflictsWithLeaderPrevLogTermFalseReply(){
        List<Entry> followerLog = new ArrayList<Entry>();
        List<Entry> expectedLog = new ArrayList<>();
        Entry e1 = createEntry(1);
        Entry e2 = createEntry(2);
        followerLog.add(e1);
        followerLog.add(e2);
        expectedLog.add(e1);

        follower = BehaviorTestKit.create(TestableFollower.create(1, inboxRef, followerLog));
        follower.run(new RaftMessage.AppendEntries(1, inboxRef, 0, 0, new ArrayList<Entry>(), 0));
        assertCorrectAppendEntriesResponse(inbox.receiveMessage(), 1, false);
    }

    @Test
    public void FollowerHas2Entries1stEntryConsistentWithPrevLogNoNewEntriesLeadsToSuccessAndLogStaysSame(){
        List<Entry> followerLog = new ArrayList<Entry>();
        List<Entry> expectedLog = new ArrayList<>();
        Entry e1 = createEntry(1);
        Entry e2 = createEntry(2);
        followerLog.add(e1);
        followerLog.add(e2);
        expectedLog.add(e1);
        expectedLog.add(e2);

        follower = BehaviorTestKit.create(TestableFollower.create(1, inboxRef, followerLog));
        assertCorrectResponseAndLogAfterAppendEntries(1, true, expectedLog, new RaftMessage.AppendEntries(1, inboxRef, 0, 1, new ArrayList<Entry>(), 0));

    }

    @Test
    public void AppendEntriesSuccessOneExistingEntryConflictsWithLeaderEntryFollowerDeletesEntryAddsNewEntry(){
        List<Entry> followerLog = new ArrayList<>();
        List<Entry> expectedLog = new ArrayList<>();
        List<Entry> messageEntries = new ArrayList<>();
        messageEntries.add(createEntry(1));
        expectedLog.add(createEntry(1));
        followerLog.add(createEntry(2));
        follower = BehaviorTestKit.create(TestableFollower.create(3, inboxRef, followerLog));
        RaftMessage appendEntries = new RaftMessage.AppendEntries(3, inboxRef, -1, 0, messageEntries, 0);
        assertCorrectResponseAndLogAfterAppendEntries(3, true, expectedLog, appendEntries);
    }

    @Test
    public void AppendEntriesSuccessFirstOfTwoExistingEntryConflictsWithLeaderEntryFollowerDeletesBothEntriesAddsNewEntry(){
        List<Entry> followerLog = new ArrayList<>();
        List<Entry> expectedLog = new ArrayList<>();
        List<Entry> messageEntries = new ArrayList<>();
        messageEntries.add(createEntry(1));
        expectedLog.add(createEntry(1));
        followerLog.add(createEntry(2));
        followerLog.add(createEntry(3));
        follower = BehaviorTestKit.create(TestableFollower.create(3, inboxRef, followerLog));
        RaftMessage appendEntries = new RaftMessage.AppendEntries(3, inboxRef, -1, 0, messageEntries, 0);
        assertCorrectResponseAndLogAfterAppendEntries(3, true, expectedLog, appendEntries);
    }

    @Test
    public void AppendEntriesSuccessSecondOfThreeExistingEntryConflictsWithLeaderEntryFollowerDeletes2and3AddsNewEntry(){
        List<Entry> followerLog = new ArrayList<>();
        List<Entry> expectedLog = new ArrayList<>();
        List<Entry> messageEntries = new ArrayList<>();
        messageEntries.add(createEntry(1));
        messageEntries.add(createEntry(3));
        expectedLog.add(createEntry(1));
        expectedLog.add(createEntry(3));
        followerLog.add(createEntry(1));
        followerLog.add(createEntry(2));
        followerLog.add(createEntry(2));
        follower = BehaviorTestKit.create(TestableFollower.create(3, inboxRef, followerLog));
        RaftMessage appendEntries = new RaftMessage.AppendEntries(3, inboxRef, -1, 0, messageEntries, 0);
        assertCorrectResponseAndLogAfterAppendEntries(3, true, expectedLog, appendEntries);
    }

}
