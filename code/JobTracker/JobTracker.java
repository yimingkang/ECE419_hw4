import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.NodeExistsException;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;

import java.util.concurrent.CountDownLatch;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs.Ids;

public class JobTracker {
    
    public static String myPath = "/JobTracker";
    public static ZooKeeper zk;
    public static boolean isPrimary;
    public static CountDownLatch nodeDeadSignal = new CountDownLatch(1);
    
    public static void main(String[] args) {
        
        if (args.length != 1) {
            System.out.println("Usage: java -classpath lib/zookeeper-3.3.2.jar:lib/log4j-1.2.15.jar:. A zkServer:clientPort");
            return;
        }

        ZkConnector zkc = new ZkConnector();
        
        try {
            zkc.connect(args[0]);
        } catch(Exception e) {
            System.out.println("Zookeeper connect "+ e.getMessage());
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
			                	isPrimary = true;
			                    System.out.println("Primary JobTracker has died, taking over as Primary");
			                    try {
									zk.create(
									    myPath,         	   // Path of znode
									    null,           	   // Data not needed.
									    Ids.OPEN_ACL_UNSAFE,   // ACL, set to Completely Open.
									    CreateMode.EPHEMERAL   // Znode type, set to EPHEMERAL for failure detection
									);
									nodeDeadSignal.countDown();
								} catch (NodeExistsException e) {
									System.out.println("ERROR: Primary node is not yet dead");
									// TODO Auto-generated catch block
									e.printStackTrace();
									System.exit(-1);
								} catch (KeeperException e) {
									// TODO Auto-generated catch block
						            System.out.println(e.code());
									e.printStackTrace();
								} catch (InterruptedException e) {
									// TODO Auto-generated catch block
						            System.out.println("ERROR: " + e.getMessage());
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
            System.out.println("Make node:" + e.getMessage());
        }
        if (isPrimary){    	
        	System.out.println("Primary JobTracker started -- sleeping for 10 seconds before dying");
	    	try {
				Thread.sleep(10000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} 	
        }else{
        	System.out.println("Backup JobTracker started -- waiting for primary watcher");
            try{
            	nodeDeadSignal.await();
            } catch(Exception e) {
                System.out.println(e.getMessage());
            }
        }


    }
}
