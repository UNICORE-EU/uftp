package eu.unicore.uftp.datashare.db;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

import eu.unicore.persist.Persist;
import eu.unicore.persist.PersistenceException;
import eu.unicore.persist.PersistenceFactory;
import eu.unicore.persist.PersistenceProperties;
import eu.unicore.persist.impl.PersistenceDescriptor;
import eu.unicore.uftp.datashare.AccessType;
import eu.unicore.uftp.datashare.Owner;
import eu.unicore.uftp.datashare.Target;

/**
 * API to the ACL storage
 * 
 * @author schuller
 */
public class ACLStorage {

	// this is shared by all the users!
	private static Persist<ShareDAO> storage;

	public ACLStorage(String name, Properties p) throws Exception {
		init(name, p);
	}

	private synchronized void init(String name, Properties p) throws Exception{
		if(storage!=null)return;
		PersistenceProperties pp = new PersistenceProperties(p);
		PersistenceFactory pf = PersistenceFactory.get(pp);
		PersistenceDescriptor pd = PersistenceDescriptor.get(ShareDAO.class);
		pd.setTableName("SHARES_"+name);
		storage = pf.getPersist(ShareDAO.class, pd);
	}
	
	public void deleteAccess(String path, Target target, Owner owner) throws Exception {
		Collection<ShareDAO> grants = readAll(path, false, owner);
		for(ShareDAO grant: grants) {
			if(target==null || target.getID().equals(target.getID())) {
				storage.remove(grant.getID());
			}
		}
	}
	
	/**
	 * @param accessType
	 * @param path
	 * @param target
	 * @param owner
	 * @param expiry - expiry time (System date in seconds!)
	 * @param onetime
	 * @return
	 * @throws Exception
	 */
	public String grant(AccessType accessType, String path, Target target, Owner owner, long expiry, boolean onetime) throws Exception {
		Collection<ShareDAO> grants = readAll(path, false, owner);
		ShareDAO grant = null;
		if(grants.size()>0){
			grant = filter(grants, target);
		}
		if(grant==null){
			if(AccessType.NONE==accessType) {
				return null;
			}
			grant = new ShareDAO();
			grant.setPath(path);
			grant.setOwnerID(owner.getName());
			grant.setUid(owner.getUftpUser());
			grant.setGid(owner.getUftpGroup());
			grant.setTargetID(target.getID());
			grant.setDirectory(path.endsWith("/"));
		}
		else{
			if(AccessType.NONE==accessType){
				storage.remove(grant.getID());
				return null;
			}
			// update to write permission
			grant = storage.getForUpdate(grant.getID());
		}
		grant.setAccess(accessType);
		grant.setExpires(expiry);
		grant.setOneTime(onetime);
		storage.write(grant);
		return grant.getID();
	}

	public ShareDAO filter(Collection<ShareDAO> grants, Target target){
		for(ShareDAO share : grants){
			if(target.getID().equals(share.getTargetID()) ||
					Target.ANONYMOUS.equalsIgnoreCase(share.getTargetID())){
				return share;
			}
		}
		return null;
	}
	
	public ShareDAO read(String uniqueID) throws Exception {
		ShareDAO d = getPersist().read(uniqueID);
		if(d!=null && d.isExpired()) {
			delete(uniqueID);
			d = null;
		}
		return d;
	}

	public int incrementAccessCount(String uniqueID) {
		try{
			ShareDAO dao = getPersist().getForUpdate(uniqueID);
			dao.incrementAccessCount();
			getPersist().write(dao);
			return dao.getAccessCount();
		}catch(Exception ex) {}
		return 0;
	}

	public Collection<ShareDAO>readAll(String path) throws Exception {
		return readAll(path, true);
	}

	public List<ShareDAO>readAll(String path, boolean recurse, Owner owner) throws Exception {
		List<ShareDAO> result = new ArrayList<>();
		File f = new File(path);
		Collection<String> ids = storage.getIDs("path", path);
		if(recurse){
			while(f.getParentFile()!=null){
				f = f.getParentFile();
				ids.addAll(storage.getIDs("path", f.getPath()+"/"));
			}
		}
		for(String id: ids){
			ShareDAO d = storage.read(id);
			if(d!=null) {
				if(d.isExpired())delete(id);
				else if(owner==null || owner.getName().equals(d.getOwnerID())) {
					result.add(d);
				}
			}
		}
		Collections.sort(result, MostSpecificPath);
		return result;
	}
	
	public List<ShareDAO>readAll(String path, boolean recurse) throws Exception {
		return readAll(path, recurse, null);
	}
	
	public List<ShareDAO>readAll(Target target) throws Exception {
		List<ShareDAO> result = new ArrayList<>();
		Collection<String> ids = storage.getIDs("target", target.getID());
		for(String id: ids){
			ShareDAO d = storage.read(id);
			if(d!=null) {
				if(d.isExpired()) {
					delete(id);
				}
				else {
					result.add(d);
				}
			}
		}
		return result;
	}
	
	public Collection<ShareDAO>readAll(Owner owner) throws Exception {
		Collection<ShareDAO> result = new ArrayList<>();
		Collection<String> ids = storage.getIDs("owner", owner.getName());
		for(String id: ids){
			ShareDAO d = storage.read(id);
			if(d!=null) {
				if(d.isExpired()) {
					delete(id);
				}
				else {
					result.add(d);
				}
			}
		}
		return result;
	}
	
	public void delete(String id) throws Exception {
		getPersist().remove(id);
	}
	
	/**
	 * Deletes the DB content. Be careful. Be very careful.
	 * @throws PersistenceException
	 */
	public void deleteAllData()throws Exception {
		getPersist().removeAll();
	}
	
	protected Persist<ShareDAO>getPersist(){
		return storage;
	}

	public static Comparator<ShareDAO> MostSpecificPath = new Comparator<>(){
		@Override
		public int compare(ShareDAO o1, ShareDAO o2) {
			return o2.getPath().length()-o1.getPath().length();
		}	
	};
	
}
