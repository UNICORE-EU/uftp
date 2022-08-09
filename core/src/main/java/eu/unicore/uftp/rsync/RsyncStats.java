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
	
	public int blocksize;
	
	public RsyncStats(String fileName){
		this.fileName=fileName;
	}
	
	public String toString(){
		StringBuilder sb=new StringBuilder();
		sb.append("'"+fileName+"' : "+duration+"ms, transferred: "+transferred);
		sb.append(" block size: "+blocksize);
		if(matches>=0)sb.append(" block matches: "+matches);
		if(weakMatches>=0)sb.append(" weak matches: "+weakMatches);
		return sb.toString();
	}

}
