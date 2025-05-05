package eu.unicore.uftp.datashare.db;

import eu.unicore.persist.annotations.Column;
import eu.unicore.persist.annotations.ID;
import eu.unicore.persist.annotations.Table;
import eu.unicore.persist.util.UUID;
import eu.unicore.uftp.datashare.AccessType;

@Table(name="SHARES")
public class ShareDAO {

	private String ID = UUID.newUniqueID();
	
	@Column(name="path")
	private String path;
	
	private boolean isDirectory;
	
	@Column(name="owner")
	private String ownerID;
	
	private String uid;
	private String gid;

	@Column(name="target")
	private String targetID;
	
	private AccessType access;
	
	private boolean oneTime;
	
	private long expires;
	
	private int accessCount;
	
	@ID
	public String getID(){
		return ID;
	}
	
	@Column(name="access")
	public String getAccess(){
		return String.valueOf(access);
	}

	public String getPath() {
		return path;
	}

	public boolean isDirectory() {
		return isDirectory;
	}

	public void setDirectory(boolean isDirectory) {
		this.isDirectory = isDirectory;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getOwnerID() {
		return ownerID;
	}

	public void setOwnerID(String ownerID) {
		this.ownerID = ownerID;
	}

	public String getUid() {
		return uid;
	}

	public void setUid(String uid) {
		this.uid = uid;
	}

	public String getGid() {
		return gid;
	}

	public void setGid(String gid) {
		this.gid = gid;
	}

	public String getTargetID() {
		return targetID;
	}

	public void setTargetID(String targetID) {
		this.targetID = targetID;
	}

	public void setAccess(AccessType access) {
		this.access = access;
	}

	public boolean isOneTime() {
		return oneTime;
	}

	public void setOneTime(boolean oneTime) {
		this.oneTime = oneTime;
	}

	public long getExpires() {
		return expires;
	}

	public void setExpires(long expires) {
		this.expires = expires;
	}

	public int getAccessCount() {
		return accessCount;
	}

	public void incrementAccessCount() {
		this.accessCount+=1;
	}

	public String toString(){
		return path+(isDirectory?" [D]" :"")+": owner="+ownerID+":"+uid+":"+gid+" target="+targetID+" : "+access;
	}
	
	public boolean isExpired() {
		long now = System.currentTimeMillis()/1000;
		return expires>0 && expires<now;
	}
	
}
