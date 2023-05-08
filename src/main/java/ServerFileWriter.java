import akka.actor.typed.ActorRef;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class ServerFileWriter implements ServerDataManager{

    @Override
    public void saveLog(List<Entry> log) {
        try {
            File logFile = getLogFile();
            initializeDataFiles();
            ObjectOutputStream oos = createObjectOutputStream(logFile);
            oos.writeObject(log);
            oos.flush();
            oos.close();


        }catch(IOException e){
            throw new RuntimeException(e);
        }
    }

    @Override
    public void saveCurrentTerm(int term) {

    }

    @Override
    public void saveVotedFor(ActorRef<RaftMessage> actorRef) {

    }

    @Override
    public List<Entry> getLog() {
        List<Entry> log;
        try {
            initializeDataFiles();
            File logFile = getLogFile();
            ObjectInputStream ois = createObjectInputStream(logFile);
            log = (ArrayList<Entry>) ois.readObject();
            ois.close();
            return log;
        }catch(IOException e){
            throw new RuntimeException(e);
        }catch(ClassNotFoundException e){
            throw new RuntimeException(e);
        }
    }

    @Override
    public int getCurrentTerm() {
        return 0;
    }

    @Override
    public ActorRef<RaftMessage> getVotedFor() {
        return null;
    }

    @Override
    public void setServerID(int ID) {
        this.serverUID = ID;
    }

    private int serverUID;


    private void initializeDataFiles(){
        File actorDirectory = getActorDirectory();
        File logFile = getLogFile();

        try{
            if(!actorDirectory.exists()){
                actorDirectory.mkdirs();
            }
            if (!logFile.exists()) {
                logFile.createNewFile();
                saveLog(new ArrayList<Entry>());
            }
        } catch(IOException e){
                throw new RuntimeException(e);
        }
    }


    private static ObjectOutputStream createObjectOutputStream(File logFile) throws IOException {
        FileOutputStream fos = new FileOutputStream(logFile);
        return new ObjectOutputStream(fos);
    }

    private static ObjectInputStream createObjectInputStream(File logFile) throws IOException {
        FileInputStream fis = new FileInputStream(logFile);
        return new ObjectInputStream(fis);
    }


    private File getActorDirectory(){
        String UID = String.valueOf(this.serverUID);
        String PATH = "./data/" + UID + "/";
        return new File(PATH);
    }
    private File getLogFile(){
        String UID = String.valueOf(this.serverUID);
        String PATH = "./data/" + UID + "/log.ser";
        return new File(PATH);
    }

}
