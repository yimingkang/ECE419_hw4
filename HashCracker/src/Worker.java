import java.util.List;
import java.util.logging.Logger;

import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.Watcher.Event.EventType;

public class Worker {
    public static String jobPath = "/Jobs";
    public static String workerPath = "/Workers";
    public static String myPath;
    public static List<String> currentWorkers = null;
    public static ZooKeeper zk;
    public static ZkConnector zkc;
    private static Watcher workerWatcher;
    public static boolean MOCK = true;
    
	private static final Logger logger = Logger.getLogger(Worker.class.getName());
	
	public static void main(String[] args){
        if (args.length != 1) {
            System.out.println("Usage: java -classpath lib/zookeeper-3.3.2.jar:lib/log4j-1.2.15.jar:. A zkServer:clientPort");
            return;
        }

        zkc = new ZkConnector();
        
        try {
            zkc.connect(args[0]);
        } catch(Exception e) {
            logger.info("Zookeeper connect "+ e.getMessage());
        }

        zk = zkc.getZooKeeper();
        
        /* 1- Make connection to zookeeper (already done)
         * 2- Set a Watch for /Workers node to handle worker failure
         * 3- On worker failure => use setData() on worker's current task to signal that 
         * 	  we need to re-process this task
         * 4- Register self on zookeeper 
         * 5- Loop through all tasks and work on it if it needs to be done (use setData() with version
         *    number to achieve atomic operation similar to test-and-set)
         */
        
   
        // Set a watcher on WORKERS
        workerWatcher = new Watcher() { // Anonymous Watcher
            @Override
            public void process(WatchedEvent event) {
                workerHandler(event);
        
            }
        };
        watchWorkers();
        
        // Register self on zookeeper
        myPath = zkc.createWorker(workerPath);
        
        // now all we have to do is to actually dictionary attack it
        // task will be something like /Jobs/<md5>_<partitionID>
        String task = getTask();
        String[] taskAttr = task.split("/")[-1].split("_");
        
        if (taskAttr.length != 2){
        	logger.severe("Task name could not be understood: " + task);
        }
        
        String md5Hash = taskAttr[0];
        String partitionID = taskAttr[1];
        
        
        // Fetch dictionary
        logger.info("Attacking MD5 hash: " + md5Hash + " in partionID: " + partitionID);
        
        // HULK SMASH
        
        // Update task
        updateTask(task, "FOUND");
	}
	
	private static void updateTask(String task, String taskStatusUpdate){
		Stat taskStat = new Stat();
		String taskStatus = zkc.getData(task, taskStat).toString();
		int taskVersion = taskStat.getVersion();
		
		if (taskStatus.equals(myPath)){
			if(zkc.setData(task, taskStatusUpdate.getBytes(), taskVersion)){
				logger.info("Successfully updated task " + task + " to " + taskStatusUpdate);
			}else{
				logger.severe("Unable to update task " + task + ", exiting");
				System.exit(-1);
			}
		}else{
			logger.severe("Unexpected task data: " + taskStatus + ", exiting");
			System.exit(-1);
		}
	}
	
	private static String getTask(){
		/* Fetch a single task and set task's data to this worker's name
		 * 
		 */
		while (true){
			List<String> tasks = zkc.getChildren(jobPath, null);
			
			for (String task: tasks){
				// check and assign to self
				Stat taskStat = new Stat();
				String taskStatus = zkc.getData(task, taskStat).toString();
				
				int taskVersion = taskStat.getVersion();
				if (taskStatus.equals("CREATED")){
					// Found a free tasks, go take it!!
					if (zkc.setData(task, myPath.getBytes(), taskVersion)){
						logger.info("Assigning task: " + task + " to self: " + myPath);
						return task;
					}
				}
			}
			logger.info("Unable to find any available task, sleeping for 5s and trying again");
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				System.exit(-1);
			}
		}
	}
	
	private static void watchWorkers(){
        List<String> newWorkers = zkc.getChildren(workerPath, workerWatcher);
        if (currentWorkers == null){
        	// Initialize workers
        	currentWorkers = newWorkers;
        } else {
        	logger.info("Children were added or deleted from " + workerPath);
        	handleWorkerDeath(newWorkers);
        }
	}
	
	private static void clearTaskAndDeleteWorker(String task, String worker){
		// If TASK's data == worker then set it to "CREATED"
		// otherwise don't do anything
		Stat taskStat = new Stat();		
		byte[] taskWorker = zkc.getData(task, taskStat);
		int taskVersion = taskStat.getVersion();
		
		if (taskWorker != null && taskWorker.equals(worker)){
			zkc.setData(task, "CREATED".getBytes(), taskVersion);
		}
		
		// If worker exists, remove it
		Stat workerStat = new Stat();
		zkc.getData(task, taskStat);
		int workerVersion = workerStat.getVersion();
		zkc.delete(worker, workerVersion);
	}
	
	private static void handleWorkerDeath(List<String> newWorkers){
		for (String worker: currentWorkers){
			if (!currentWorkers.contains(worker)){
				// previousWorker died
				byte[] workerData = zkc.getData(worker, null);
				if (workerData != null){
					// Clean up after this dude
					String task = workerData.toString();
					clearTaskAndDeleteWorker(task, worker);
				}else{
					logger.info("Worker " + worker + " has been deleted");
				}
			}
		}
		
		// update our knowledge about current workers
		currentWorkers = newWorkers;
	}
	
	private static void workerHandler(WatchedEvent event){
        String path = event.getPath();
        EventType type = event.getType();
        
        if(path.equalsIgnoreCase(workerPath)) {
            if (type == EventType.NodeChildrenChanged) {
            	logger.warning("Worker node died / created");
                watchWorkers();
            }else{
            	logger.info("Something unexpected happened to node " + workerPath + ", exiting");
            	System.exit(-1);
            }
        }
	}

}
