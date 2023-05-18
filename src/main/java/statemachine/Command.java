package statemachine;

import akka.actor.typed.ActorRef;
import messages.ClientMessage;

public interface Command {
    String getClientRef();
    int getCommandID();
    boolean equals(Command other);
}
