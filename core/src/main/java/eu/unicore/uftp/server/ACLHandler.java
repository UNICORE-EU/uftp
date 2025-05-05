package eu.unicore.uftp.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
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
	private final Set<String>acceptedDNs = new HashSet<>();

	public ACLHandler(File aclFile) throws FileNotFoundException {
		if(!aclFile.exists()){
			throw new FileNotFoundException("ACL file does not exist: "+aclFile);
		}
		this.aclFile = aclFile;
		logger.info("Using ACL file {}", aclFile);
		watchDog=new FileWatcher(aclFile, ()-> readACL());
		watchDog.schedule(3000,TimeUnit.MILLISECONDS);
		watchDog.run();
	}

	private void readACL(){
		synchronized(acceptedDNs){
			try(BufferedReader br = new BufferedReader(new FileReader(aclFile))){
				String theLine;
				acceptedDNs.clear();
				while((theLine=br.readLine())!=null){
					String line=theLine.trim();
					if(line.startsWith("#"))continue;
					if(!line.trim().equals("")){
						try{
							acceptedDNs.add(new X500Principal(line).getName());
							logger.info("Allowing access for <{}>", line);	
						}catch(Exception ex){
							logger.warn("Invalid entry <{}>: {}", line, ex.getMessage());
						}
					}
				}
			}catch(Exception ex){
				logger.error("ACL file read error!",ex);
			}
		}
	}

	public void checkAccess(String userName) throws AuthorizationFailureException{
		logger.debug("Check access from {}", userName);
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
