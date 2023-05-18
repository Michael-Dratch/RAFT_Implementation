import akka.actor.typed.ActorSystem;
import messages.OrchMessage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class RaftDemo {
    public static void main(String[] args) throws IOException {
        int serverCount = Integer.valueOf(args[0]);
        int clientCount = Integer.valueOf(args[1]);
        int concurrentFailures = Integer.valueOf(args[2]);
        int numClientRequests = Integer.valueOf(args[3]);

        var orc = ActorSystem.create(Orchestrator.create(), "REST-DEMO");
        var done = false;
        var console = new BufferedReader(new InputStreamReader(System.in));

        orc.tell(new OrchMessage.Start(serverCount, clientCount, concurrentFailures, numClientRequests));

        while (!done) {
            var command = console.readLine();
            if (command.length()==0) {
                done = true;
                terminateSystem(orc);
            }
        }
    }

    private static void terminateSystem(ActorSystem<OrchMessage> orc) {
        orc.tell(new OrchMessage.ShutDown());
    }
}
