package eu.unicore.uftp.datashare.db;

import java.util.UUID;

import de.fzj.unicore.persist.annotations.Column;
import de.fzj.unicore.persist.annotations.ID;
import de.fzj.unicore.persist.annotations.Table;
import eu.unicore.uftp.datashare.AccessType;

@Table(name="SHARES")
public class ShareDAO {

	private String ID = UUID.randomUUID().toString();
	
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

	public void setID(String ID) {
		this.ID = ID;

	}

	public void setAccess(AccessType access) {
		this.access = access;
	}

	public String toString(){
		return path+(isDirectory?" [D]" :"")+": owner="+ownerID+":"+uid+":"+gid+" target="+targetID+" : "+access;
	}
}
