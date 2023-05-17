package raftstates;// On failure Akka Actors Restart Behavior With arguments that were originally passed in to create method
// however these arguments usually are not the state you would expect after a node failure
// This Failflag class is used to create a more realistic failure behavior (clearing all transient state)
// Because the flag is an object it is past into a behavior by reference. The value can be set to the proper restart value
// on preRestart signal without akka changing the value afterwards on restart

public class FailFlag {
    public boolean failed = false;
}
