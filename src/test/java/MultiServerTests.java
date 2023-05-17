import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import akka.routing.AddRoutee;
import datapersistence.ServerFileWriter;
import messages.ClientMessage;
import messages.RaftMessage;
import org.junit.*;
import raftstates.Client;
import raftstates.FailFlag;
import raftstates.Follower;
import statemachine.Command;
import statemachine.CommandList;
import statemachine.StringCommand;
import java.time.Duration;


import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MultiServerTests {

    static ActorTestKit testKit;


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

    private static List<String> getCommandList(int count) {
        List<String> commands = new ArrayList<>();
        for (int i = 0; i < count; i++){
            commands.add("COMMAND_" + i );
        }
        return commands;
    }

    private static List<ActorRef<RaftMessage>> createServerGroup(int serverCount) {
        List<ActorRef<RaftMessage>> groupRefs = new ArrayList<>();

        for (int i = 0; i < serverCount; i++){
            ActorRef<RaftMessage> server = testKit.spawn(Follower.create(new ServerFileWriter(), new CommandList(), new FailFlag()));
            groupRefs.add(server);
        }
        return groupRefs;
    }

    private static void sendGroupRefsToServers(List<ActorRef<RaftMessage>> groupRefs) {
        for (ActorRef<RaftMessage> server : groupRefs){
            List<ActorRef<RaftMessage>> serverRemoved = new ArrayList<>(groupRefs);
            serverRemoved.remove(server);
            server.tell(new RaftMessage.SetGroupRefs(serverRemoved));
        }
    }

    private static void startServerGroup(List<ActorRef<RaftMessage>> groupRefs) {
        for (ActorRef<RaftMessage> server: groupRefs){
            server.tell(new RaftMessage.Start());
        }
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
    }

    @After
    public void tearDown(){
        clearDataDirectory();
    }

    @Test
    public void oneClientNoFailures4ServersSuccessfullyCommitsAllEntries(){
        TestProbe<ClientMessage> probe = testKit.createTestProbe();
        List<String> commands = getCommandList(50);
        List<ActorRef<RaftMessage>> groupRefs = createServerGroup(4);
        sendGroupRefsToServers(groupRefs);
        startServerGroup(groupRefs);
        ActorRef<ClientMessage> client = testKit.spawn(Client.create(groupRefs,commands));
        client.tell(new ClientMessage.AlertWhenFinished(probe.ref()));
        client.tell(new ClientMessage.Start());
        probe.expectMessage(Duration.ofSeconds(5), new ClientMessage.Finished());
    }
}
