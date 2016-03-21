import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.NodeExistsException;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;
//import java.util.logging.Level;
//import org.apache.log4j.*;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs.Ids;

public class JobTracker extends PBArchitecture {
    public static String jobBasePath = "/Jobs";
	private static int nPartitions = 27;
	public static boolean MOCK = false;	

    public static void main(String[] args) throws ClassNotFoundException, IOException {
    	
        pbPath = "/JobTracker";
        logger = Logger.getLogger(JobTracker.class.getName());
    	
        if (args.length != 1) {
            System.out.println("Usage: java -classpath lib/zookeeper-3.3.2.jar:lib/log4j-1.2.15.jar:. A zkServer:clientPort");
            return;
        }
        // Connect ZooKeeper
        connectZK(args[0]);
        
        
        // Create base node
        createBaseZNode();
        
        // Create server socket
    	ServerSocket serverSocket = null;
    	
    	while (serverSocket == null){
    		try {
				serverSocket = new ServerSocket(serverPort);
			} catch (IOException e) {
				// keep incrementing serverPort until one is available
				serverPort++;
			}
    	}

        
        // Affirm leadership (serverPort MUST be confirmed by this point)
        affirmPrimary();
            	
    	if (MOCK){
    		// this is the MD5 hash for ABC
    		String mockHash = "902fbdd2b1df0c4f70b4a5d23525e932";
    		
    		createJob(mockHash);
    		
    		try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    		
    		checkProgress(mockHash);
    	}
    	
        while (true){
        	try{
	        	Socket clientSocket = serverSocket.accept();
	        	logger.info("Connected to client!");
	        	
	        	ObjectOutputStream writer = new ObjectOutputStream(clientSocket.getOutputStream());
	        	ObjectInputStream reader = new ObjectInputStream(clientSocket.getInputStream());
	
	        	MPacket offerPacket = (MPacket) reader.readObject();
	        	logger.info("User: " + offerPacket.md5Hash + " query: " + offerPacket.queryStatus);
	        	
	        	// here we need to process the job
	        	if(offerPacket.queryStatus){
	        		offerPacket.status = checkProgress(offerPacket.md5Hash);
	        	}else{
	            	createJob(offerPacket.md5Hash);
	            	offerPacket.status = "OK";
	        	}
	        	
	        	// send user response
	        	writer.writeObject(offerPacket);
	        	
	        	// TODO: tell user the job has been submitted
	        	// TODO: check status!!
	        	clientSocket.close();
        	} catch(IOException e){
        		logger.info("Something happend to socket...continue");
        		continue;
        	}
        }
        
    }
    
    public static String checkProgress(String md5Hash){
    	List<String> allJobs = zkc.getChildren(jobBasePath, null);
    	int processedJobCount = 0;
    	int totalCount = 0;
    	for (String job: allJobs){
    		if (!job.startsWith(md5Hash)){
    			continue;
    		}
    		totalCount++;
    		String jobFullPath = jobBasePath + "/" + job;
    		String jobStatus = new String(zkc.getData(jobFullPath, null));
    		if (jobStatus.startsWith("FOUND")){
    			logger.info("Hash " + md5Hash + " has been cracked with status: " + jobStatus);
    			return jobStatus;
    		}else if (jobStatus.equals("NOT_FOUND")){
    			processedJobCount ++;
    		}
    	}
    	logger.info("Job " + md5Hash + " progress: " + processedJobCount + "/" + totalCount);
    	
    	if(totalCount == 0){
    		return "Job not found";
    	}else if(totalCount != nPartitions){
    		return "Failed to complete job";
    	}else if(totalCount == processedJobCount){
    		return "Password not found";
    	}else{
    		return "In Progress";
    	}
    }

    
    public static void createBaseZNode(){
    	// we would like to first create /Jobs node
		try {
			zk.create(
			        jobBasePath,         	    // Path of znode
			        null,   // Status is CREATED
			        Ids.OPEN_ACL_UNSAFE,    // ACL, set to Completely Open.
			        CreateMode.PERSISTENT   // Znode type, set to EPHEMERAL for failure detection
			);
			logger.info("Created node " + jobBasePath);
		} catch (KeeperException | InterruptedException e) {
			logger.info("Node " + jobBasePath + " already exists");
		}
    }
    
    public static void createJob(String md5Hash){
		for (int partitionID = 0; partitionID < nPartitions; partitionID++){
	        try {
	        	
	        	// create N jobs under /<jobPath>/<md5_hash>_<partitionID>
	        	String md5Path = jobBasePath + "/" + md5Hash + "_" + partitionID;
	        	logger.info("Creating node at " + md5Path);
				zk.create(
				        md5Path,         	    // Path of znode
				        "CREATED".getBytes(),   // Status is CREATED
				        Ids.OPEN_ACL_UNSAFE,    // ACL, set to Completely Open.
				        CreateMode.PERSISTENT   // Znode type, set to EPHEMERAL for failure detection
				);
			} catch (NodeExistsException e) {
				logger.info("Job " + md5Hash + " already exists! -- aborting");
				break;
			} catch(KeeperException | InterruptedException e){
				logger.severe("ZooKeeper error: " + e.getMessage());
				System.exit(-1);
			}
		}
    }
    
    
}
