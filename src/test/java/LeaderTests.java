import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class LeaderTests {
    ActorRef<RaftMessage> leader;

    static ActorTestKit testKit;

    TestProbe<RaftMessage> probe;

    ActorRef<RaftMessage> probeRef;

    private List<ActorRef<RaftMessage>> getGroupRefs(int count){
        List<ActorRef<RaftMessage>> groupRefs = new ArrayList<>();
        for (int i = 0; i < count; i++){
            TestProbe<RaftMessage> probe = testKit.createTestProbe();
            groupRefs.add(probe.ref());
        }
        return groupRefs;
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

}
