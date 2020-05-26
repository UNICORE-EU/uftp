package eu.unicore.uftp.datashare;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import eu.unicore.uftp.datashare.db.ACLStorage;
import eu.unicore.uftp.datashare.db.ShareDAO;

/**
 * Holds the access levels for one file, owned by one owner
 *
 * @author Martin Lischewski
 * @author schuller
 */
public class ACLEntry {
	
	private ACLStorage storage;
	
	private String path;
	
	private Owner owner;
	
	private Map<Target, AccessType> accessMap = new HashMap<>();
	
	
	/**
	 * Set permissions for the target.
	 * 
	 * Setting to AccessType.NONE will remove any access for the target
	 * 
	 * @param target
	 * @param permissions
	 */
	public void setPermissions(Target target, AccessType permissions){
		accessMap.put(target, permissions);
	}

	/**
	 * Gets all Targets which are allowed to read this path
	 */
	public Set<Target> getAllReadable(){
		Set<Target> ret = new HashSet<>();
		ret.addAll(accessMap.keySet());
		return ret;
	}

	/**
	 * Returns all entries
	 */
	public Map<Target, AccessType> list(){
		return accessMap;
	}

	/**
	 * Returns all entries with the given level of access
	 */
	public Set<Target> list(AccessType accessType){
		if(AccessType.NONE==accessType)throw new IllegalArgumentException("Access type must be higher than NONE.");
		Set<Target> res = new HashSet<>();
		for(Map.Entry<Target, AccessType> e : accessMap.entrySet()){
			if(accessType.compareTo(e.getValue())<=0)res.add(e.getKey());
		}
		return res;
	}

	/**
	 * check the level of access for the given Target
	 */
	public AccessType checkAccess(Target target){
		return accessMap.getOrDefault(target, AccessType.NONE);
	}

	public static ACLEntry read(String path, Owner owner, ACLStorage storage) throws Exception {
		Collection<ShareDAO> shares = storage.readAll(path);
		ACLEntry entry = new ACLEntry();
		entry.path = path;
		entry.owner = owner;
		entry.storage = storage;
		for(ShareDAO share: shares){
			if(owner.getName().equals(share.getOwnerID())){
				AccessType access = AccessType.valueOf(share.getAccess());
				SharingUser t = new SharingUser(share.getTargetID());
				entry.accessMap.put(t, access);
			}
		}
		return entry;
	}
	
	public void write() throws Exception {
		for(Map.Entry<Target, AccessType> e : accessMap.entrySet()){
			storage.grant(e.getValue(), path, e.getKey(), owner);		
		}
	}

}
