import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.BehaviorTestKit;
import akka.actor.testkit.typed.javadsl.TestInbox;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import org.junit.After;

import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertTrue;

public class FollowerTests {
    ActorRef<RaftMessage> follower;

//    TestInbox<RaftMessage> inbox;
//    ActorRef<RaftMessage> inboxRef;

    ActorTestKit testKit;

    TestProbe<RaftMessage> probe;

    ActorRef<RaftMessage> probeRef;


    private static void assertCorrectAppendEntriesResponse(RaftMessage response, int expectedTerm, boolean expectedSuccess) {
        if (response instanceof RaftMessage.AppendEntriesResponse){
            RaftMessage.AppendEntriesResponse msg = (RaftMessage.AppendEntriesResponse) response;
            assertEquals(expectedSuccess, msg.success());
            assertEquals(expectedTerm, msg.term());
        }else{
            throw new AssertionError("Incorrect Response Message Type");
        }
    }

    private void assertCorrectRequestVoteResponse(RaftMessage response, int expectedTerm, boolean expectedVoteGranted){
        if (response instanceof RaftMessage.RequestVoteResponse){
            RaftMessage.RequestVoteResponse msg = (RaftMessage.RequestVoteResponse) response;
            assertEquals(expectedTerm, msg.term());
            assertEquals(expectedVoteGranted, msg.voteGranted());
        } else {
            throw new AssertionError("Incorrect Response Message Type");
        }
    }

    private void assertCorrectResponseAndLogAfterAppendEntries(int expectedTerm, boolean expectedSuccess, List<Entry> expectedLog, RaftMessage appendEntries) {
        follower.tell(appendEntries);
        follower.tell(new RaftMessage.TestMessage.GetLog(probe.ref()));
        List<RaftMessage> responses = probe.receiveSeveralMessages(2);
        assertCorrectAppendEntriesResponse(responses.get(0), expectedTerm, expectedSuccess);
        assertCorrectLogResponse(responses.get(1), expectedLog);
    }

    private static void assertCorrectLogResponse(RaftMessage response, List<Entry> expectedLog) {
        if (response instanceof RaftMessage.TestMessage.GetLogResponse){
            RaftMessage.TestMessage.GetLogResponse msg = ( RaftMessage.TestMessage.GetLogResponse) response;
            List<Entry> actualLog = msg.log();
            assertLogsAreEqual(expectedLog, actualLog);
        }else{
            throw new AssertionError("Incorrect Response Message Type");
        }
    }

    private static void assertLogsAreEqual(List<Entry> expectedLog, List<Entry> actualLog) {
        assertEquals(expectedLog.size(), actualLog.size());
        for (int i = 0; i < actualLog.size(); i++){
            assertTrue(actualLog.get(i).equals(expectedLog.get(i)));
        }
    }

    private static void assertCorrectCommitIndex(RaftMessage response, int expectedCommitIndex) {
        if (response instanceof RaftMessage.TestMessage.GetCommitIndexResponse){
            RaftMessage.TestMessage.GetCommitIndexResponse msg = ( RaftMessage.TestMessage.GetCommitIndexResponse) response;
            assertEquals(expectedCommitIndex, msg.commitIndex());
        }else{
            throw new AssertionError("Incorrect Response Message Type");
        }
    }

    private static Entry createEntry(int term) {
        return new Entry(term, new StringCommand(0, 0, ""));
    }

    private void deleteActorDirectory() {
        File actorDirectory = getActorDirectory();
        if (actorDirectory.exists()){
            deleteDirectory(actorDirectory);
        }
    }

    private void clearDataDirectory(){
        File dataDir = new File("./data/");
        File[] contents = dataDir.listFiles();
        if (contents != null) {
            for (File file : contents) {
                deleteDirectory(file);
            }
        }
    }

    private void deleteDirectory(File directory){
        File[] contents = directory.listFiles();
        if (contents != null){
            for (File file : contents){
                deleteDirectory(file);
            }
        }
        directory.delete();
    }

    private void assertActorLogFileCorrect(List<Entry> expectedLog) {
        File logFile = getLogFile();
        try {
            FileInputStream fos = new FileInputStream(logFile);
            ObjectInputStream ois = new ObjectInputStream(fos);
            List<Entry> log = (List<Entry>)ois.readObject();
            ois.close();
            assertEquals(expectedLog.size(), log.size());

            for (int i = 0; i < expectedLog.size(); i++){
                expectedLog.get(i).equals(log.get(i));
            }

        }catch(IOException e){
            throw new RuntimeException(e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private File getLogFile() {
        String logPath = "./data/" + follower.path().uid() + "/log.ser";
        return new File(logPath);
    }

    private File getActorDirectory() {
        String path = "./data/" + follower.path().uid() + "/";
        return new File(path);
    }

    public void assertCorrectFollowerState(RaftMessage response,
                                           List<Entry> expectedLog,
                                           ActorRef<RaftMessage> expectedVotedFor){
        if (response instanceof RaftMessage.TestMessage.GetStateResponse){
            RaftMessage.TestMessage.GetStateResponse msg = (RaftMessage.TestMessage.GetStateResponse) response;
            assertLogsAreEqual(expectedLog, msg.log());
            assertEquals(expectedVotedFor, msg.votedFor());
        } else{
            throw new AssertionError("Incorrect Response Message Type");
        }
    }


    @Before
    public void setUp(){
        testKit = ActorTestKit.create();
        probe = testKit.createTestProbe();
        probeRef = probe.ref();
//        inbox = TestInbox.create();
//        inboxRef = inbox.getRef();
    }

    @After
    public void tearDown(){
        testKit.shutdownTestKit();
        clearDataDirectory();
    }

    @Test
    public void AppendEntryFromEarlierTermRepliesFalseAndCurrentTerm(){
        int followerTerm = 1;
        follower = testKit.spawn(TestableFollower.create(followerTerm, new ArrayList<Entry>()));
        follower.tell(new RaftMessage.AppendEntries(0, probeRef, 0, 0, new ArrayList<Entry>(), 0));
        RaftMessage response = probe.receiveMessage();
        assertCorrectAppendEntriesResponse(response, followerTerm, false);
    }

    @Test
    public void FollowerHasNoEntryAtPrevLogIndexReturnsFalse(){
        follower = testKit.spawn(Follower.create(new ServerFileWriter()));
        follower.tell(new RaftMessage.AppendEntries(0, probeRef, 1, 0, new ArrayList<Entry>(), 0));
        assertCorrectAppendEntriesResponse(probe.receiveMessage(), 0, false);
    }

    @Test
    public void FollowerEntryAtPrevLogIndexDoesntMatchPrevLogTermReturnsFalseResponse(){
        List<Entry> followerLog = new ArrayList<Entry>();
        followerLog.add(createEntry(1));
        follower = testKit.spawn(TestableFollower.create(3, followerLog));
        follower.tell(new RaftMessage.AppendEntries(3, probeRef, 0, 2, new ArrayList<Entry>(), 0));
        assertCorrectAppendEntriesResponse(probe.receiveMessage(), 3, false);
    }

    @Test
    public void FollowerUpdatesCurrentTermIfFalseAppendEntriesHasLargerTerm(){
        List<Entry> followerLog = new ArrayList<Entry>();
        followerLog.add(createEntry(1));
        follower = testKit.spawn(TestableFollower.create(0, probeRef, followerLog, -1, -1));
        follower.tell(new RaftMessage.AppendEntries(1, probeRef, 0, 0, new ArrayList<Entry>(), 0));
        assertCorrectAppendEntriesResponse(probe.receiveMessage(), 1, false);
    }

    @Test
    public void FollowerUpdatesCurrentTermIfTrueAppendEntriesHasLargerTerm(){
        List<Entry> followerLog = new ArrayList<Entry>();
        followerLog.add(createEntry(1));
        follower = testKit.spawn(TestableFollower.create(0, probeRef, followerLog, -1, -1));
        follower.tell(new RaftMessage.AppendEntries(1, probeRef, 0, 1, new ArrayList<Entry>(), 0));
        assertCorrectAppendEntriesResponse(probe.receiveMessage(), 1, true);
    }



    @Test
    public void testCorrectSuccessMessageReturnedFromSuccessfulAppendEntries(){
        follower = testKit.spawn(TestableFollower.create(1, new ArrayList<Entry>()));
        follower.tell(new RaftMessage.AppendEntries(1, probeRef, -1, 0, new ArrayList<Entry>(), 0));
        assertCorrectAppendEntriesResponse(probe.receiveMessage(), 1, true);
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
        follower = testKit.spawn(TestableFollower.create(1, followerLog));
        RaftMessage appendEntries = new RaftMessage.AppendEntries(1, probeRef, -1, 0, new ArrayList<Entry>(), 0);
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

        follower = testKit.spawn(TestableFollower.create(1, followerLog));
        follower.tell(new RaftMessage.AppendEntries(1, probeRef, 0, 0, new ArrayList<Entry>(), 0));
        assertCorrectAppendEntriesResponse(probe.receiveMessage(), 1, false);
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

        follower = testKit.spawn(TestableFollower.create(1, followerLog));
        assertCorrectResponseAndLogAfterAppendEntries(1, true, expectedLog, new RaftMessage.AppendEntries(1, probeRef, 0, 1, new ArrayList<Entry>(), 0));

    }

    @Test
    public void AppendEntriesSuccessOneExistingEntryConflictsWithLeaderEntryFollowerDeletesEntryAddsNewEntry(){
        List<Entry> followerLog = new ArrayList<>();
        List<Entry> expectedLog = new ArrayList<>();
        List<Entry> messageEntries = new ArrayList<>();
        messageEntries.add(createEntry(1));
        expectedLog.add(createEntry(1));
        followerLog.add(createEntry(2));
        follower = testKit.spawn(TestableFollower.create(3, followerLog));
        RaftMessage appendEntries = new RaftMessage.AppendEntries(3, probeRef, -1, 0, messageEntries, 0);
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
        follower = testKit.spawn(TestableFollower.create(3, followerLog));
        RaftMessage appendEntries = new RaftMessage.AppendEntries(3, probeRef, -1, 0, messageEntries, 0);
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
        follower = testKit.spawn(TestableFollower.create(3, followerLog));
        RaftMessage appendEntries = new RaftMessage.AppendEntries(3, probeRef, -1, 0, messageEntries, 0);
        assertCorrectResponseAndLogAfterAppendEntries(3, true, expectedLog, appendEntries);
    }

    @Test
    public void LeaderHasGreaterCommitIndexFollowerSetsCommitIndexToLeaderValueWhenLogIsGreaterThanCommitIndex(){
        List<Entry> followerLog = new ArrayList<>();
        followerLog.add(createEntry(1));
        followerLog.add(createEntry(1));
        followerLog.add(createEntry(1));
        follower = testKit.spawn(TestableFollower.create(3, followerLog, -1));
        follower.tell(new RaftMessage.AppendEntries(3, probeRef, 2, 1, new ArrayList<>(), 1));
        follower.tell(new RaftMessage.TestMessage.GetCommitIndex(probeRef));
        assertCorrectCommitIndex(probe.receiveSeveralMessages(2).get(1), 1);
    }

    @Test
    public void LeaderHasGreaterCommitIndexFollowerSetsCommitIndexToLastEntryWhenLogIsLessThanCommitIndex(){
        List<Entry> followerLog = new ArrayList<>();
        followerLog.add(createEntry(1));
        followerLog.add(createEntry(1));
        followerLog.add(createEntry(1));
        follower = testKit.spawn(TestableFollower.create(3, followerLog, -1));
        follower.tell(new RaftMessage.AppendEntries(3, probeRef, 2, 1, new ArrayList<>(), 4));
        follower.tell(new RaftMessage.TestMessage.GetCommitIndex(probeRef));
        assertCorrectCommitIndex(probe.receiveSeveralMessages(2).get(1), 2);
    }


    @Test
    public void followerRespondsFalseToRequestVoteWhereTermIsLessThanFollowerTerm(){
        follower = testKit.spawn(TestableFollower.create(2, new ArrayList<>(), -1));
        follower.tell(new RaftMessage.RequestVote(1, probeRef, 0,0));
        RaftMessage response = probe.receiveMessage();
        assertCorrectRequestVoteResponse(response, 2, false);
    }

    @Test
    public void requestVoteHasSmallerLastLogTermFollowerReturnsFalse(){
        List<Entry> followerLog = new ArrayList<>();
        followerLog.add(createEntry(1));
        follower = testKit.spawn(TestableFollower.create(1, followerLog, -1));
        follower.tell(new RaftMessage.RequestVote(1, probeRef, 0,0));
        RaftMessage response = probe.receiveMessage();
        assertCorrectRequestVoteResponse(response, 1, false);
    }

    @Test
    public void requestVoteHasSameLastLogTermButSmallerLastLogIndexFollowerReturnsFalse(){
        List<Entry> followerLog = new ArrayList<>();
        followerLog.add(createEntry(1));
        followerLog.add(createEntry(1));
        follower = testKit.spawn(TestableFollower.create(1, followerLog, -1));
        follower.tell(new RaftMessage.RequestVote(1, probeRef, 0,1));
        RaftMessage response = probe.receiveMessage();
        assertCorrectRequestVoteResponse(response, 1, false);
    }

    @Test
    public void requestVoteValidButFollowerAlreadyGaveVoteFollowerReturnsFalse(){
        follower = testKit.spawn(TestableFollower.create(1, new ArrayList<>(), -1));
        follower.tell(new RaftMessage.RequestVote(1, probeRef, 1, 1));
        follower.tell(new RaftMessage.RequestVote(1, probeRef, 1, 1));
        RaftMessage response = probe.receiveSeveralMessages(2).get(1);
        assertCorrectRequestVoteResponse(response, 1, false);
    }

    @Test
    public void requestVotHasSmallerLastLogIndexButLargerLastLogTermFollowerGrantsVote(){
        List<Entry> followerLog = new ArrayList<>();
        followerLog.add(createEntry(1));
        followerLog.add(createEntry(1));
        follower = testKit.spawn(TestableFollower.create(1, followerLog, -1));
        follower.tell(new RaftMessage.RequestVote(1, probeRef, 0,2));
        RaftMessage response = probe.receiveMessage();
        assertCorrectRequestVoteResponse(response, 1, true);
    }

    @Test
    public void failedRequestVoteButSenderHasLargerTermFollowerIncreasesCurrentTerm(){
        List<Entry> followerLog = new ArrayList<>();
        followerLog.add(createEntry(1));
        follower = testKit.spawn(TestableFollower.create(1, followerLog, -1));
        follower.tell(new RaftMessage.RequestVote(2, probeRef, 0,0));
        RaftMessage response = probe.receiveMessage();
        assertCorrectRequestVoteResponse(response, 2, false);
    }

    @Test
    public void successfulRequestVoteSenderHasLargerTermFollowerIncreasesCurrentTerm(){
        List<Entry> followerLog = new ArrayList<>();
        followerLog.add(createEntry(1));
        followerLog.add(createEntry(1));
        follower = testKit.spawn(TestableFollower.create(1, followerLog, -1));
        follower.tell(new RaftMessage.RequestVote(2, probeRef, 0,2));
        RaftMessage response = probe.receiveMessage();
        assertCorrectRequestVoteResponse(response, 2, true);
    }
    
    @Test
    public void afterFailureFollowerWithNoLogOrVotedForStillHasNoLogVotedFor(){
        follower = testKit.spawn(TestableFollower.create(1, new ArrayList<>(), -1));
        follower.tell(new RaftMessage.TestMessage.testFail());
        follower.tell(new RaftMessage.TestMessage.GetState(probeRef));
        RaftMessage response = probe.receiveMessage();
        List<Entry> expectedLog = new ArrayList<>();
        assertCorrectFollowerState(response, expectedLog, null);
    }

    @Test
    public void afterFailureFollowerWithTwoLogEntriesRecoversLog() {
        follower = testKit.spawn(Follower.create(new ServerFileWriter()));
        List<Entry> newEntries = new ArrayList<>();
        newEntries.add(createEntry(1));
        newEntries.add(createEntry(1));
        follower.tell(new RaftMessage.AppendEntries(1, probeRef, -1, -1, newEntries, -1));
        follower.tell(new RaftMessage.Failure());
        follower.tell(new RaftMessage.AppendEntries(1, probeRef, 1, 1, newEntries, -1));
        List<RaftMessage> responses = probe.receiveSeveralMessages(2);
        List<Entry> expectedLog = newEntries;
        System.out.println("response size");
        System.out.println(responses.size());
        assertCorrectFollowerState(responses.get(1), expectedLog, probeRef);
    }
    @Test
    public void afterFailureFollowerWithVotedForRecoversVotedFor(){
        ActorTestKit testKit = ActorTestKit.create();
        ActorRef<RaftMessage> node = testKit.spawn(Follower.create(new ServerFileWriter()));
        TestProbe<RaftMessage> probe = testKit.createTestProbe();

        node.tell(new RaftMessage.RequestVote(1, probe.ref(), 1, 1));
        probe.expectMessage(new RaftMessage.RequestVoteResponse(1, true));
        node.tell(new RaftMessage.Failure());
        node.tell(new RaftMessage.RequestVote(1, probe.ref(), 1, 1));
        probe.expectMessage(new RaftMessage.RequestVoteResponse(1, false));

        testKit.shutdownTestKit();


    }
}
