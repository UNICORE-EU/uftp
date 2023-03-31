package eu.unicore.uftp.rsync;


/**
 * some stats about execution of the rsync per file
 * 
 * @author schuller
 */
public class RsyncStats {

	private final String fileName;
	
	public long duration;
	
	public long transferred;
	
	public int weakMatches=-1;
	
	public int matches=-1;
	
	public int misses=-1;
	
	public int blocks;
	
	public int blocksize;
	
	public RsyncStats(String fileName){
		this.fileName=fileName;
	}
	
	public String toString(){
		StringBuilder sb=new StringBuilder();
		sb.append("'"+fileName+"' : "+duration+"ms, transferred: "+transferred);
		sb.append("\n blocks: ").append(blocks);
		sb.append(" size: ").append(blocksize);
		if(matches>=0)sb.append("\n matches: "+matches);
		if(weakMatches>=0)sb.append(", weak : "+weakMatches);
		if(misses>=0)sb.append(", misses: "+misses);
		return sb.toString();
	}

}
