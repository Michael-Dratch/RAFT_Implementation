import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import org.junit.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CandidateTests     {

    ActorRef<RaftMessage> candidate;

    static ActorTestKit testKit;

    TestProbe<RaftMessage> probe;

    ActorRef<RaftMessage> probeRef;

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

    @Test
    public void candidateReceivesNoVotesSendsOutAnotherRoundOfVoteRequestsWithIncrementedTerm(){
        TestProbe<RaftMessage> probe2 = testKit.createTestProbe();
        List<ActorRef<RaftMessage>> groupRefs = new ArrayList<>();
        groupRefs.add(probeRef);
        groupRefs.add(probe2.ref());
        candidate = testKit.spawn(Candidate.create(new ServerFileWriter(), 1, groupRefs, -1, -1));
        probe.expectMessage(new RaftMessage.RequestVote(2, candidate, -1, -1));
        probe2.expectMessage(new RaftMessage.RequestVote(2, candidate, -1, -1));
    }
    }

