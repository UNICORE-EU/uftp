package eu.unicore.uftp.datashare;

import java.util.HashSet;
import java.util.Set;

public class Share {

	/**
	 * normalized path string. Never null
	 */
	private String path;
	
	/**
	 * is this a directory
	 */
	private boolean isDirectory;
	
	/**
	 * the owner of this share. Never null
	 */
	private Owner owner;
	
	/**
	 * the ACL entries for this shared path
	 */
	private Set<ACLEntry> aclEntries = new HashSet<>();

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}
	
	public boolean isDirectory() {
		return isDirectory;
	}

	public void setDirectory(boolean isDirectory) {
		this.isDirectory = isDirectory;
	}

	public Owner getOwner() {
		return owner;
	}

	public void setOwner(Owner owner) {
		this.owner = owner;
	}

	public Set<ACLEntry> getAclEntries() {
		return aclEntries;
	}

	public void setAclEntries(Set<ACLEntry> aclEntries) {
		this.aclEntries = aclEntries;
	}

}
