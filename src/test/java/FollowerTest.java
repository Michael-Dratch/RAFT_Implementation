import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.BehaviorTestKit;
import akka.actor.testkit.typed.javadsl.TestInbox;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

public class FollowerTest {

    BehaviorTestKit<RaftMessage> follower;

    @Before
    public void setUp(){
        follower = BehaviorTestKit.create(Follower.create());
    }

    @Test
    public void testSpawnBehavior(){

    }
}
