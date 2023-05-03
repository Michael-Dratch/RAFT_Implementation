import akka.actor.typed.ActorRef;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.List;

public class ServerFileWriter implements ServerDataManager{

    public ServerFileWriter(int serverUID){
        this.serverUID = serverUID;
    }
    @Override
    public void saveEntriesToLog(List<Entry> entries) {

    }

    @Override
    public void saveCurrentTerm(int term) {

    }

    @Override
    public void saveVotedFor(ActorRef<RaftMessage> actorRef) {

    }

    @Override
    public List<Entry> getLog() {
        return null;
    }

    @Override
    public int getCurrentTerm() {
        return 0;
    }

    @Override
    public ActorRef<RaftMessage> getVotedFor() {
        return null;
    }

    private int serverUID;


    private void initializeDataFiles(){
        File logFile = getLogFile();

        if (logFile.exists()){
        } else {
            try {
                File directory = new File(logFile.getParent());
                if(!directory.exists()){
                    directory.mkdirs();
                }
                logFile.createNewFile();
            } catch(IOException e){
                throw new RuntimeException(e);
            }
        }

    }

    private void writeEntriesToFile(List<Entry> entries){
        try {
            File logFile = getLogFile();
            initializeFileIfNotExist(logFile);
            ObjectOutputStream oos = createObjectInputStream(logFile);

            for (Entry e: entries){
                oos.writeObject(e);
            }

            oos.close();
        }catch(IOException e){
            throw new RuntimeException(e);
        }
    }

    private static ObjectOutputStream createObjectInputStream(File logFile) throws IOException {
        FileOutputStream fos = new FileOutputStream(logFile);
        return new ObjectOutputStream(fos);
    }

    private void initializeFileIfNotExist(File logFile) {
        if (!logFile.exists()){
            initializeDataFiles();
        }
    }

    private File getLogFile(){
        String UID = String.valueOf(this.serverUID);
        String PATH = "./data/" + UID + "/log.ser";
        return new File(PATH);
    }
}
