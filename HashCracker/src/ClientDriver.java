import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.logging.Logger;

import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;

public class ClientDriver extends PBArchitectureAdaptor{
    
    public static void main(String[] args){
        if (args.length != 3) {
            System.out.println("Usage: java -classpath lib/zookeeper-3.3.2.jar:lib/log4j-1.2.15.jar:. A zkServer:clientPort");
            return;
        }
        
        pbPath = "/JobTracker";
        logger = Logger.getLogger(JobTracker.class.getName());

        connectZKAndSetupPB(args[0]);
        
        logger.info("Attempting to submit job " + args[2]);
		String status = submitJob(args[1], args[2]);
		logger.info("JOBTRACKER RESPONSE: " + status);
		return;
    }
    
    public static String submitJob(String request, String md5Hash){
    	boolean isQueryJob = request.equals("status");
    	MPacket response = exchangeMPacket(new MPacket(md5Hash, isQueryJob));
    	return response.status;
    }
}
