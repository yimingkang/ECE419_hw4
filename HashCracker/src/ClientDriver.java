import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.logging.Logger;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.Code;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;

public class ClientDriver {
	public static Logger logger;
    public static ZooKeeper zk;
    public static ZkConnector zkc;
    public static String jobTrackerPath = "/JobTracker";
    public static int jobTrackerPort = -1;
    public static Watcher jobTrackerWatcher;
    
    public static void main(String[] args){
        if (args.length != 3) {
            System.out.println("Usage: java -classpath lib/zookeeper-3.3.2.jar:lib/log4j-1.2.15.jar:. A zkServer:clientPort");
            return;
        }
        // connect to zookeeper
        zkConnect(args[0]);
        
        jobTrackerWatcher = new Watcher() { // Anonymous Watcher
            @Override
            public void process(WatchedEvent event) {
                trackerHandler(event);
            }
        };
        
        // get job tracker port
        getJobTrackerPort();
        
        while (true){
        	logger.info("Attempting to submit job " + args[2]);
            try {
				String status = submitJob(args[1], args[2]);
				logger.info("JOBTRACKER RESPONSE: " + status);
				return;
			} catch (ClassNotFoundException | IOException e) {
				logger.info("Unable to send/receive packet -- retrying");
				getJobTrackerPort();
			}
        }
    }
    
    public static String submitJob(String request, String md5Hash) throws UnknownHostException, IOException, ClassNotFoundException{
    	boolean isQueryJob = request.equals("status");
    	Socket connection = new Socket("localhost", jobTrackerPort);
    	logger.info("Connected to server!");
    	
    	ObjectOutputStream writer = new ObjectOutputStream(connection.getOutputStream());
    	ObjectInputStream reader = new ObjectInputStream(connection.getInputStream());
    	
    	writer.writeObject(new MPacket(md5Hash, isQueryJob));
    	MPacket response = (MPacket) reader.readObject();
    	connection.close();
    	
    	return response.status;
    }
    
    public static void trackerHandler(WatchedEvent event){
    	boolean isNodeDeleted = event.getType().equals(EventType.NodeDeleted);
    	boolean isNodeChanged = event.getType().equals(EventType.NodeDataChanged);
    	boolean correctPath = event.getPath().equals(jobTrackerPath);
    	if (correctPath && (isNodeChanged || isNodeDeleted)){
    		logger.info("JobTracker was either removed or changed");
    		getJobTrackerPort();
    	} else{
    		logger.warning("Handler is triggered by an unknown event: " + event.toString());
    	}
    }
    
    public static void getJobTrackerPort(){
    	while (true){
	    	try {
				byte[] trackerData = zk.getData(jobTrackerPath, jobTrackerWatcher, null);
				jobTrackerPort = ByteBuffer.wrap(trackerData).getInt();
				logger.info("JobTracker is at port " + jobTrackerPort);
				return;
			} catch (KeeperException e) {
				if (e.code().equals(Code.NONODE)){
					logger.info("Client is waiting for JobTracker to start...");
					try {
						Thread.sleep(5000);
					} catch (InterruptedException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					continue;
				}
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				System.exit(-1);
			}
    	}
    	
    }
    
    public static void zkConnect(String hostPort){
        logger = Logger.getLogger(JobTracker.class.getName());
        zkc = new ZkConnector();
        
        try {
            zkc.connect(hostPort);
        } catch(Exception e) {
            logger.info("Zookeeper connect "+ e.getMessage());
            System.exit(-1);
        }
        zk = zkc.getZooKeeper();
    }
}
