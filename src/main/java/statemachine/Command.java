package statemachine;

public interface Command {
    int getClientID();
    int getCommandID();
    boolean equals(Command other);
}
