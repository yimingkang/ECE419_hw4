import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.GenericArrayType;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Logger;

public class FileServer extends PBArchitecture {
	private static int nPartitions = 27;
	private static int partitionSize = 10000;
	public static boolean MOCK = true;	
	public static List<String> dictionary;

    public static void main(String[] args) throws IOException, ClassNotFoundException {
    	
        pbPath = "/FileServer";
        logger = Logger.getLogger(JobTracker.class.getName());
    	
        if (args.length != 2) {
            System.out.println("Usage: java -classpath lib/zookeeper-3.3.2.jar:lib/log4j-1.2.15.jar:. A zkServer:clientPort");
            return;
        }
        
        // Connect to ZooKeeper, load dictionary and affirm leadership
        connectZK(args[0]);
        try{
        	loadDictionary(args[1]);
        }catch(FileNotFoundException e){
        	logger.severe("Unable to load dictionary file " + args[1]);
        	System.exit(-1);
        }
        
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

    	
        affirmPrimary();
        
        while (true){
        	try{
	        	Socket clientSocket = serverSocket.accept();
	        	logger.info("Connected to client!");
	        	
	        	ObjectOutputStream writer = new ObjectOutputStream(clientSocket.getOutputStream());
	        	ObjectInputStream reader = new ObjectInputStream(clientSocket.getInputStream());
	
	        	MPacket offerPacket = (MPacket) reader.readObject();
	        	int partition = offerPacket.partitionID;
	        	logger.info("Worker requesting partitionID: " + partition);
	        	
	        	offerPacket.givePartition(getPartition(partition));
	        	
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
        //TODO: Wait for connection and send over the chunk   
    }
    
    static List<String> getPartition(int partitionID){
    	int minLN = partitionID * partitionSize;
    	int maxLN = Math.min((partitionID + 1) * partitionSize, dictionary.size());
    	return new ArrayList<String>(dictionary.subList(minLN, maxLN));
    }
    
    public static void loadDictionary(String filePath) throws FileNotFoundException{
    	dictionary = new ArrayList<String>();
    	Scanner dictScanner = new Scanner(new File(filePath));
    	while (dictScanner.hasNext()){
    		dictionary.add(dictScanner.next());
    	}
    	dictScanner.close();
    }

}
