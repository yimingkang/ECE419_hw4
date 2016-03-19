import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.NodeExistsException;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;
//import java.util.logging.Level;

import org.apache.log4j.Level;
import org.apache.log4j.*;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs.Ids;

public class JobTrackerCore {
    
    public static String myPath = "/JobTracker";
    public static ZooKeeper zk;
    public static boolean isPrimary;
    public static CountDownLatch nodeDeadSignal = new CountDownLatch(1);
	private static final Logger logger = Logger.getLogger(JobTrackerCore.class.getName());

    public static void main(String[] args) throws IOException, ClassNotFoundException {
    	
        if (args.length != 1) {
            System.out.println("Usage: java -classpath lib/zookeeper-3.3.2.jar:lib/log4j-1.2.15.jar:. A zkServer:clientPort");
            return;
        }

        ZkConnector zkc = new ZkConnector();
        
        try {
            zkc.connect(args[0]);
        } catch(Exception e) {
            logger.info("Zookeeper connect "+ e.getMessage());
        }

        zk = zkc.getZooKeeper();

        try {
            zk.create(
                myPath,         	   // Path of znode
                null,           	   // Data not needed.
                Ids.OPEN_ACL_UNSAFE,   // ACL, set to Completely Open.
                CreateMode.EPHEMERAL   // Znode type, set to EPHEMERAL for failure detection
            );
            isPrimary = true;
            logger.info("Primary JobTracker started");
            
        } catch(NodeExistsException e) {
        	// here we create a wacher
        	isPrimary = false;
            try {
				zk.exists(
			        myPath, 
			        new Watcher() {       // Anonymous Watcher
			            @Override
			            public void process(WatchedEvent event) {
			                // check for event type NodeCreated
			                boolean isNodeDeleted = event.getType().equals(EventType.NodeDeleted);
			                
			                // verify if this is the defined znode
			                boolean isMyPath = event.getPath().equals(myPath);
			                if (isNodeDeleted && isMyPath) {
			                	logger.info("Primary JobTracker has died, taking over as Primary");
			                    try {
									zk.create(
									    myPath,         	   // Path of znode
									    null,           	   // Data not needed.
									    Ids.OPEN_ACL_UNSAFE,   // ACL, set to Completely Open.
									    CreateMode.EPHEMERAL   // Znode type, set to EPHEMERAL for failure detection
									);
				                	isPrimary = true;
									nodeDeadSignal.countDown();
								} catch (NodeExistsException e) {
									logger.severe("Primary node is not yet dead");
									// TODO Auto-generated catch block
									e.printStackTrace();
									System.exit(-1);
								} catch (KeeperException e) {
									// TODO Auto-generated catch block
									logger.severe(e.getMessage());
									e.printStackTrace();
								} catch (InterruptedException e) {
									// TODO Auto-generated catch block
									logger.severe(e.getMessage());
						            System.exit(-1);
								}
			                }
			            }
			        });
			} catch (KeeperException | InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
        	
            System.out.println(e.code());
        } catch(Exception e) {
        	logger.severe(e.getMessage());
        }
        
        if (!isPrimary){
        	logger.info("Backup JobTracker started -- watching primary");
            try{
            	nodeDeadSignal.await();
            } catch(Exception e) {
            	logger.severe(e.getMessage());
            }
        }
        
    	ServerSocket serverSocket = new ServerSocket(8888);
        while (true){
        	Socket clientSocket = serverSocket.accept();
        	ObjectInputStream reader = new ObjectInputStream(clientSocket.getInputStream());
        	String md5_job = (String) reader.readObject();
        	logger.info("Connected to a user with MD5 hash: " + md5_job);
        	// here we need to process the job
        	
        	// tell user the job has been submitted
        	clientSocket.close();
        }
        
    }
}
