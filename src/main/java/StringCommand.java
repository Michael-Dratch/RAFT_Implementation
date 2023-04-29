public class StringCommand implements Command {
    private int clientID;
    private int commandID;
    private String value;

    public StringCommand(int clientID, int commandID, String value){
        this.clientID = clientID;
        this.commandID = commandID;
        this.value = value;
    }

    @Override
    public boolean equals(Command other){
        return this.clientID == other.getClientID() && this.commandID == other.getCommandID();
    }

    @Override
    public int getClientID() {
        return this.clientID;
    }

    @Override
    public int getCommandID() {
        return this.commandID;
    }

    public String getValue() {
        return this.value;
    }
}
