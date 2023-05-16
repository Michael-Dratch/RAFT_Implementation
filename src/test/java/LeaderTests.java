import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import org.junit.*;
import org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class LeaderTests {
    ActorRef<RaftMessage> leader;

    static ActorTestKit testKit;

    TestProbe<RaftMessage> probe;

    ActorRef<RaftMessage> probeRef;

    private  List<ActorRef<RaftMessage>> getSingleProbeGroupRefs() {
        List<ActorRef<RaftMessage>> groupRefs = new ArrayList<>();
        groupRefs.add(probeRef);
        return groupRefs;
    }

    private List<TestProbe<RaftMessage>> getProbeGroup(int count){
        List<TestProbe<RaftMessage>> group = new ArrayList<>();
        for (int i = 0; i < count; i++){
            TestProbe<RaftMessage> probe = testKit.createTestProbe();
            group.add(probe);
        }
        return group;
    }

    private List<ActorRef<RaftMessage>> getProbeGroupRefs(List<TestProbe<RaftMessage>> probeGroup){
        List<ActorRef<RaftMessage>> groupRefs = new ArrayList<>();
        for (TestProbe<RaftMessage> probe: probeGroup){
            groupRefs.add(probe.ref());
        }
        return groupRefs;
    }

    private static Entry getEntry(int term) {
        return new Entry(term, new StringCommand(1, 1, "Test"));
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

    private static ActorRef<RaftMessage> getLeader(List<ActorRef<RaftMessage>> groupRefs, int term) {
        return testKit.spawn(Leader.create(new ServerFileWriter(), new Object(), term, groupRefs, -1, -1));
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
    private StringCommand getCommand() {
        return new StringCommand(1, 1, "TEST");
    }

    @BeforeClass
    public static void classSetUp(){
        testKit = ActorTestKit.create();
    }

    @AfterClass
    public static void classTearDown(){
        testKit.shutdownTestKit();
    }

    @Before
    public void setUp(){
        probe = testKit.createTestProbe();
        probeRef = probe.ref();
    }

    @After
    public void tearDown(){
        clearDataDirectory();
    }



    @Test
    public void receivesAppendEntriesFromLaterTermBecomesFollower(){
        leader = testKit.spawn(Leader.create(new ServerFileWriter(), new Object(),1, new ArrayList<>(), -1, -1));
        leader.tell(new RaftMessage.AppendEntries(2, probeRef, -1, -1, new ArrayList<>(), -1));
        leader.tell(new RaftMessage.TestMessage.GetBehavior(probeRef));
        probe.expectMessage(new RaftMessage.TestMessage.GetBehaviorResponse("FOLLOWER"));
    }

    @Test
    public void receivesAppendEntriesFromSameTermBecomesFollower(){
        leader = testKit.spawn(Leader.create(new ServerFileWriter(), new Object(),1, new ArrayList<>(), -1, -1));
        leader.tell(new RaftMessage.AppendEntries(2, probeRef, -1, -1, new ArrayList<>(), -1));
        leader.tell(new RaftMessage.TestMessage.GetBehavior(probeRef));
        probe.expectMessage(new RaftMessage.TestMessage.GetBehaviorResponse("FOLLOWER"));
    }

    @Test
    public void receivesAppendEntriesFromEarlierTermStaysLeader(){
        leader = testKit.spawn(Leader.create(new ServerFileWriter(), new Object(),1, new ArrayList<>(), -1, -1));
        leader.tell(new RaftMessage.AppendEntries(0, probeRef, -1, -1, new ArrayList<>(), -1));
        leader.tell(new RaftMessage.TestMessage.GetBehavior(probeRef));
        List<RaftMessage> responses = probe.receiveSeveralMessages(2);
        RaftMessage.TestMessage.GetBehaviorResponse res = (RaftMessage.TestMessage.GetBehaviorResponse) responses.get(1);
        assertEquals("LEADER", res.behavior());
    }

    @Test
    public void receivesRequestVoteFromLaterTermSwitchesToFollower(){
        leader = testKit.spawn(Leader.create(new ServerFileWriter(), new Object(),1, new ArrayList<>(), -1, -1));
        leader.tell(new RaftMessage.RequestVote(2, probeRef, -1, -1));
        leader.tell(new RaftMessage.TestMessage.GetBehavior(probeRef));
        probe.expectMessage(new RaftMessage.TestMessage.GetBehaviorResponse("FOLLOWER"));
    }

    @Test
    public void receivesAppendEntriesReponseFromLaterTermSwitchesToFollower(){
        leader = testKit.spawn(Leader.create(new ServerFileWriter(), new Object(),1, new ArrayList<>(), -1, -1));
        leader.tell(new RaftMessage.AppendEntriesResponse(probeRef, 2, false, 0));
        leader.tell(new RaftMessage.TestMessage.GetBehavior(probeRef));
        probe.expectMessage(new RaftMessage.TestMessage.GetBehaviorResponse("FOLLOWER"));
    }

    @Test
    public void receivesRequestVoteResponseFromLaterTermSwitchesToFollower(){
        leader = getLeader(new ArrayList<>(), 1);
        leader.tell(new RaftMessage.RequestVoteResponse(2, false));
        leader.tell(new RaftMessage.TestMessage.GetBehavior(probeRef));
        probe.expectMessage(new RaftMessage.TestMessage.GetBehaviorResponse("FOLLOWER"));
    }

    @Test
    public void leaderSendsHeartBeatToAllNodesAfterHeartBeatTimeOut(){
        List<TestProbe<RaftMessage>> probes = getProbeGroup(2);
        List<ActorRef<RaftMessage>> groupRefs = getProbeGroupRefs(probes);
        leader = getLeader(groupRefs, 1);
        for (TestProbe<RaftMessage> probe: probes){
            probe.expectMessage(new RaftMessage.AppendEntries(1, leader, -1, -1, new ArrayList<>(), -1));
        }
    }

    @Test
    public void clientRequestSentToLeaderLeaderSendsAppendEntriesToFollowerWithCommand() {
        List<ActorRef<RaftMessage>> groupRefs = getSingleProbeGroupRefs();
        leader = getLeader(groupRefs, 1);
        Command command = getCommand();
        leader.tell(new RaftMessage.ClientRequest(probeRef, command));
        List<Entry> expectedEntries = new ArrayList<>();
        expectedEntries.add(new Entry(1, command));
        probe.expectMessage(new RaftMessage.AppendEntries(1,leader, -1,-1, expectedEntries, -1));
    }

    @Test
    public void leaderSendsCorrectAppendEntriesWhenItHasAnInitialLog() {
        List<ActorRef<RaftMessage>> groupRefs = getSingleProbeGroupRefs();
        List<Entry> leaderLog = new ArrayList<>();
        leaderLog.add(getEntry(1));
        leader = getLeader(groupRefs, 2);
        leader.tell(new RaftMessage.TestMessage.SaveEntries(leaderLog));
        Command command = getCommand();
        leader.tell(new RaftMessage.ClientRequest(probeRef, command));
        List<Entry> expectedEntries = new ArrayList<>();
        expectedEntries.add(new Entry(2, command));
        probe.expectMessage(new RaftMessage.AppendEntries(2, leader, 0, 1, expectedEntries, -1));
    }

    @Test
    public void followerSendsFalseResponseLeaderResendsAppendEntriesWithLowerPrevLogIndex() {
        List<ActorRef<RaftMessage>> groupRefs = getSingleProbeGroupRefs();
        leader = getLeader(groupRefs, 2);

        List<Entry> leaderLog = new ArrayList<>();
        Entry entry1 = getEntry(1);
        leaderLog.add(entry1);
        leader.tell(new RaftMessage.TestMessage.SaveEntries(leaderLog));

        Command command = getCommand();
        leader.tell(new RaftMessage.ClientRequest(probeRef, command));

        List<Entry> expectedEntries = new ArrayList<>();
        expectedEntries.add(new Entry(2, command));

        probe.expectMessage(new RaftMessage.AppendEntries(2, leader, 0, 1, expectedEntries, -1));
        leader.tell(new RaftMessage.AppendEntriesResponse(probeRef, 2, false, 0));
        expectedEntries.add(0, entry1);
        probe.expectMessage(new RaftMessage.AppendEntries(2, leader, -1, -1, expectedEntries, -1));
    }

    @Test
    public void followerSendsFalseResponseLeaderResendsAppendEntriesWithLowerPrevLogIndexLongerLog() {
        List<ActorRef<RaftMessage>> groupRefs = getSingleProbeGroupRefs();
        leader = getLeader(groupRefs, 2);

        List<Entry> leaderLog = new ArrayList<>();
        Entry entry1 = getEntry(1);
        Entry entry2 = getEntry(2);
        Entry entry3 = getEntry(2);
        leaderLog.add(entry1);
        leaderLog.add(entry2);
        leaderLog.add(entry3);
        leader.tell(new RaftMessage.TestMessage.SaveEntries(leaderLog));

        Command command = getCommand();
        leader.tell(new RaftMessage.ClientRequest(probeRef, command));

        List<Entry> expectedEntries = new ArrayList<>();
        expectedEntries.add(new Entry(2, command));

        probe.expectMessage(new RaftMessage.AppendEntries(2, leader, 2, 2, expectedEntries, -1));
        leader.tell(new RaftMessage.AppendEntriesResponse(probeRef, 2, false, 0));
        expectedEntries.add(0, entry3);
        probe.expectMessage(new RaftMessage.AppendEntries(2, leader, 1, 2, expectedEntries, -1));
        leader.tell(new RaftMessage.AppendEntriesResponse(probeRef, 2, false, 0));
        expectedEntries.add(0, entry2);
        probe.expectMessage(new RaftMessage.AppendEntries(2, leader, 0, 1, expectedEntries, -1));
    }


    @Test
    public void receiveMinoritySuccessfulAppendEntriesNoResponseToClient() {
        List<TestProbe<RaftMessage>> probes = getProbeGroup(4);
        List<ActorRef<RaftMessage>> groupRefs = getProbeGroupRefs(probes);
        leader = testKit.spawn(Leader.create(new ServerFileWriter(), new Object(), 1, groupRefs, -1, -1));
        leader.tell(new RaftMessage.ClientRequest(probeRef, getCommand()));
        leader.tell(new RaftMessage.AppendEntriesResponse(groupRefs.get(0), 1, true, 1));
        leader.tell(new RaftMessage.AppendEntriesResponse(groupRefs.get(1), 1, false, 0));
        leader.tell(new RaftMessage.AppendEntriesResponse(groupRefs.get(2), 1, false, 0));
        leader.tell(new RaftMessage.AppendEntriesResponse(groupRefs.get(3), 1, false, 0));
        probe.expectNoMessage();
    }

    @Test
    public void receiveMajoritySuccessfulAppendEntriesLeaderRespondsToClient() {
        List<TestProbe<RaftMessage>> probes = getProbeGroup(4);
        List<ActorRef<RaftMessage>> groupRefs = getProbeGroupRefs(probes);
        leader = testKit.spawn(Leader.create(new ServerFileWriter(), new Object(), 1, groupRefs, -1, -1));
        leader.tell(new RaftMessage.ClientRequest(probeRef, getCommand()));
        leader.tell(new RaftMessage.AppendEntriesResponse(groupRefs.get(0), 1, true, 0));
        leader.tell(new RaftMessage.AppendEntriesResponse(groupRefs.get(1), 1, true, 0));
        probe.expectMessage(new RaftMessage.ClientResponse(true));
    }


//    @Test
//    public void receiveMajoritySuccessfulAppendEntriesSendResponseToClient(){
//
//    }


}
