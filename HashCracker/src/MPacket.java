import java.util.List;
import java.io.Serializable;

public class MPacket implements Serializable {
	// ClientDriver <=> JobTracker
	public String md5Hash;
	public boolean queryStatus;
	public String status;
	
	// FileServer <=> Worker
	public int partitionID;
	public List<String> partition;
	
	public MPacket(String md5Hash, boolean queryStatus){
		this.md5Hash = md5Hash;
		this.queryStatus = queryStatus;
	}
	
	public MPacket(int partitionID){
		this.partitionID = partitionID;
	}

	public void givePartition(List<String> partition){
		this.partition = partition;
	}
}
