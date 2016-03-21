import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.KeeperException.NodeExistsException;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.ZooDefs.Ids;

public class PBArchitecture {
	// This is the ZNode used for leader election
    public static String pbPath;
    public static boolean isPrimary;
    protected static ZooKeeper zk;
    protected static ZkConnector zkc;
	protected static Logger logger;
	protected static int serverPort = 8000;

	public static void connectZK(String hostAndPort){
        zkc = new ZkConnector();
        
        try {
            zkc.connect(hostAndPort);
        } catch(Exception e) {
            logger.info("Zookeeper connect "+ e.getMessage());
            System.exit(-1);
        }
        zk = zkc.getZooKeeper();
	}
	
    public static void affirmPrimary(){
        final CountDownLatch nodeDeadSignal = new CountDownLatch(1);

    	// this function will NOT return until we become the alpha
        try {
        	byte[] serverPortByteArray = ByteBuffer.allocate(4).putInt(serverPort).array();
            zk.create(
                pbPath,         	   // Path of znode
                serverPortByteArray,           	   // Data not needed.
                Ids.OPEN_ACL_UNSAFE,   // ACL, set to Completely Open.
                CreateMode.EPHEMERAL   // Znode type, set to EPHEMERAL for failure detection
            );
            isPrimary = true;
            logger.info("Primary started at port " + serverPort);
            
        } catch(NodeExistsException e) {
        	// here we create a wacher
        	isPrimary = false;
            try {
				zk.exists(
			        pbPath, 
			        new Watcher() {       // Anonymous Watcher
			            @Override
			            public void process(WatchedEvent event) {
			                // check for event type NodeCreated
			                boolean isNodeDeleted = event.getType().equals(EventType.NodeDeleted);
			                
			                // verify if this is the defined znode
			                boolean isMyPath = event.getPath().equals(pbPath);
			                if (isNodeDeleted && isMyPath) {
			                	logger.info("Primary has died, taking over as Primary at " + serverPort);
			                	byte[] serverPortByteArray = ByteBuffer.allocate(4).putInt(serverPort).array();
			                    try {
									zk.create(
									    pbPath,         	   // Path of znode
									    serverPortByteArray,           	   // Data not needed.
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
        	logger.info("Backup started -- watching primary");
            try{
            	nodeDeadSignal.await();
            } catch(Exception e) {
            	logger.severe(e.getMessage());
            }
        }
    }
}
