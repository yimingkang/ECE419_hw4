import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.logging.Logger;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.KeeperException.Code;
import org.apache.zookeeper.Watcher.Event.EventType;

public class PBArchitectureAdaptor {
	public static Logger logger;
    public static ZooKeeper zk;
    public static ZkConnector zkc;
    public static String pbPath;
    public static int pbPort = -1;
    public static Watcher pbWatcher;
    
    public static void connectZKAndSetupPB(String zkPort){
        // connect to zookeeper
        zkConnect(zkPort);
        
        pbWatcher = new Watcher() { // Anonymous Watcher
            @Override
            public void process(WatchedEvent event) {
                pbHandler(event);
            }
        };
        
        // get PB port
        getPBPort();
    }
    
    public static MPacket exchangeMPacket(MPacket outBound) throws UnknownHostException, IOException, ClassNotFoundException{
    	Socket connection = new Socket("localhost", pbPort);
    	logger.info("Connected to server!");

    	ObjectOutputStream writer = new ObjectOutputStream(connection.getOutputStream());
    	ObjectInputStream reader = new ObjectInputStream(connection.getInputStream());
    	
    	writer.writeObject(outBound);
    	MPacket response = (MPacket) reader.readObject();
    	connection.close();
    	
    	return response;
    }
    
    public static void pbHandler(WatchedEvent event){
    	boolean isNodeDeleted = event.getType().equals(EventType.NodeDeleted);
    	boolean isNodeChanged = event.getType().equals(EventType.NodeDataChanged);
    	boolean correctPath = event.getPath().equals(pbPath);
    	if (correctPath && (isNodeChanged || isNodeDeleted)){
    		logger.info("JobTracker was either removed or changed");
    		getPBPort();
    	} else{
    		logger.warning("Handler is triggered by an unknown event: " + event.toString());
    	}
    }
    
    public static void getPBPort(){
    	while (true){
	    	try {
				byte[] trackerData = zk.getData(pbPath, pbWatcher, null);
				pbPort = ByteBuffer.wrap(trackerData).getInt();
				logger.info("PB is at port " + pbPort);
				return;
			} catch (KeeperException e) {
				if (e.code().equals(Code.NONODE)){
					logger.info("Client is waiting for PB to start...");
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
