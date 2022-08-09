package eu.unicore.uftp.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.security.auth.x500.X500Principal;

import org.apache.logging.log4j.Logger;

import eu.unicore.uftp.dpc.AuthorizationFailureException;
import eu.unicore.uftp.dpc.Utils;

/**
 * use an ACL file to limit access
 *
 * @author schuller
 */
public class ACLHandler {

	private static final Logger logger = Utils.getLogger(Utils.LOG_SECURITY, ACLHandler.class);
	
	private final File aclFile;
	private final FileWatcher watchDog;
	private final boolean active;
	private final Set<String>acceptedDNs=new HashSet<String>();

	public ACLHandler()throws IOException{
		this(new File("conf","uftpd.acl"));
	}


	public ACLHandler(File aclFile)throws IOException{
		this.aclFile=aclFile;
		if(!aclFile.exists()){
			logger.warn("ACL not active: file <"+aclFile+"> does not exist");
			active=false;
			watchDog=null;
			return;
		}
		else{
			active=true;
			logger.info("Using ACL file "+aclFile);
			readACL();
			watchDog=new FileWatcher(aclFile, new Runnable(){
				public void run(){
					readACL();
				}
			});
			watchDog.schedule(3000,TimeUnit.MILLISECONDS);
		}
	}

	protected void readACL(){
		synchronized(acceptedDNs){
			BufferedReader br=null;
			try{
				br=new BufferedReader(new FileReader(aclFile));
				String theLine;
				acceptedDNs.clear();
				while(true){
					theLine=br.readLine();
					if(theLine==null)break;
					String line=theLine.trim();
					if(line.startsWith("#"))continue;
					if(!line.trim().equals("")){
						try{
							X500Principal p=new X500Principal(line);
							acceptedDNs.add(p.getName());
							logger.info("Allowing access for <"+line+">");	
						}catch(Exception ex){
							logger.warn("Invalid entry <"+line+">",ex);
						}
					}
				}
			}catch(Exception ex){
				logger.fatal("ACL file read error!",ex);
			}
			finally{
				Utils.closeQuietly(br);
			}
		}
	}

	public void checkAccess(String userName) throws AuthorizationFailureException{
		if(!active)return;
		logger.debug("Check access from "+userName);
		synchronized (acceptedDNs) {
			if(!acceptedDNs.contains(userName)){
				String msg="Access denied!\n\nTo allow access for this " +
						"certificate, the distinguished name: " +userName+
						" needs to be entered into the ACL file."
						+"\nPlease check the UFTPD's ACL file!\n\n" ;
				throw new AuthorizationFailureException(msg);
			}
		}
	}

}
