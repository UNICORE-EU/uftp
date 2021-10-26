package eu.unicore.uftp.datashare.db;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

import de.fzj.unicore.persist.Persist;
import de.fzj.unicore.persist.PersistenceException;
import de.fzj.unicore.persist.PersistenceFactory;
import de.fzj.unicore.persist.PersistenceProperties;
import de.fzj.unicore.persist.impl.PersistenceDescriptor;
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

	public ACLStorage(String name, Properties p) throws PersistenceException {
		init(name, p);
	}

	private synchronized void init(String name, Properties p) throws PersistenceException{
		if(storage!=null)return;
		PersistenceProperties pp = new PersistenceProperties(p);
		PersistenceFactory pf = PersistenceFactory.get(pp);
		PersistenceDescriptor pd = PersistenceDescriptor.get(ShareDAO.class);
		pd.setTableName("SHARES_"+name);
		storage = pf.getPersist(ShareDAO.class, pd);
	}
	
	public void deleteAccess(String path, Target target, Owner owner) throws PersistenceException {
		Collection<ShareDAO> grants = readAll(path, false, owner);
		for(ShareDAO grant: grants) {
			if(target==null || target.getID().equals(target.getID())) {
				storage.remove(grant.getID());
			}
		}
	}
	
	public String grant(AccessType accessType, String path, Target target, Owner owner) throws PersistenceException, InterruptedException {
		Collection<ShareDAO> grants = readAll(path, false, owner);
		ShareDAO grant = null;
		if(grants.size()>0){
			grant = filter(grants, target);
		}
		if(grant==null){
			grant = new ShareDAO();
			grant.setPath(path);
			grant.setOwnerID(owner.getName());
			grant.setUid(owner.getUftpUser());
			grant.setGid(owner.getUftpGroup());
			grant.setTargetID(target.getID());
			grant.setDirectory(path.endsWith("/"));
		}
		else{
			if(grant!=null && AccessType.NONE==accessType){
				storage.remove(grant.getID());
				return null;
			}
			// update to write permission
			grant = storage.getForUpdate(grant.getID());
		}
		grant.setAccess(accessType);
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
	
	public ShareDAO read(String uniqueID) throws PersistenceException {
		return getPersist().read(uniqueID);
	}


	public Collection<ShareDAO>readAll(String path) throws PersistenceException {
		return readAll(path, true);
	}

	public Collection<ShareDAO>readAll(String path, boolean recurse, Owner owner) throws PersistenceException {
		Collection<ShareDAO> result = new ArrayList<>();
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
				if(owner==null || owner.getName().equals(d.getOwnerID())) {
					result.add(d);
				}
			}
		}
		return result;
	}
	
	public Collection<ShareDAO>readAll(String path, boolean recurse) throws PersistenceException {
		return readAll(path, recurse, null);
	}
	
	public List<ShareDAO>readAll(Target target) throws PersistenceException {
		List<ShareDAO> result = new ArrayList<>();
		Collection<String> ids = storage.getIDs("target", target.getID());
		for(String id: ids){
			ShareDAO d = storage.read(id);
			if(d!=null)result.add(d);
		}
		return result;
	}
	
	public Collection<ShareDAO>readAll(Owner owner) throws PersistenceException {
		Collection<ShareDAO> result = new ArrayList<>();
		Collection<String> ids = storage.getIDs("owner", owner.getName());
		for(String id: ids){
			ShareDAO d = storage.read(id);
			if(d!=null)result.add(d);
		}
		return result;
	}
	
	public void delete(String id) throws PersistenceException {
		getPersist().remove(id);
	}
	
	/**
	 * Deletes the DB content. Be careful. Be very careful.
	 * @throws PersistenceException
	 */
	public void deleteAllData()throws PersistenceException {
		getPersist().removeAll();
	}
	
	protected Persist<ShareDAO>getPersist(){
		return storage;
	}

}
