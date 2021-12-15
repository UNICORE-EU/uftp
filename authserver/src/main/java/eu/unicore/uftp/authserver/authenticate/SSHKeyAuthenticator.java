package eu.unicore.uftp.authserver.authenticate;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.io.IOUtils;
import org.apache.cxf.message.Message;
import org.apache.logging.log4j.Logger;

import eu.unicore.security.HTTPAuthNTokens;
import eu.unicore.security.SecurityTokens;
import eu.unicore.security.wsutil.CXFUtils;
import eu.unicore.services.Kernel;
import eu.unicore.services.KernelInjectable;
import eu.unicore.services.rest.security.IAuthenticator;
import eu.unicore.services.rest.security.PAMAttributeSource;
import eu.unicore.services.rest.security.PAMAttributeSource.PAMAttributes;
import eu.unicore.uftp.authserver.AuthServiceProperties;
import eu.unicore.uftp.authserver.LogicalUFTPServer;
import eu.unicore.uftp.authserver.UFTPDInstance;
import eu.unicore.uftp.authserver.authenticate.sshkey.SSHKey;
import eu.unicore.uftp.authserver.authenticate.sshkey.SSHUtils;
import eu.unicore.uftp.authserver.exceptions.AuthenticationFailedException;
import eu.unicore.uftp.server.requests.UFTPGetUserInfoRequest;
import eu.unicore.util.Log;

/**
 * Authenticate by checking tokens using the SSH public key 
 * that is stored for the user. The public keys are read 
 * from '~/.ssh/authorized_keys’, but can be managed "manually" 
 * by the admin, if desired. 
 * 
 * TODO a generic version of this (using JWT instead of homegrown tokens)
 * should be in USE
 * 
 * @author schuller 
 */
public class SSHKeyAuthenticator implements IAuthenticator, KernelInjectable {

	private static final Logger logger = Log.getLogger(Log.SECURITY, SSHKeyAuthenticator.class);

	private final Map<String,AttributeHolders>db = new HashMap<>();

	private File dbFile;
	private String file;
	private long lastUpdated;

	private boolean useAuthorizedKeys = true;

	private String dnTemplate = "CN=%s, OU=ssh-local-users";

	private static long updateInterval = 600;
	
	private final static Collection<String> s = Collections.singletonList("Basic");

	private Kernel kernel;

	@Override
	public void setKernel(Kernel kernel){
		this.kernel = kernel;
	}

	@Override
	public final Collection<String>getAuthSchemes(){
		return s;
	}

	@Override
	public boolean authenticate(Message message, SecurityTokens tokens){
		HTTPAuthNTokens auth = (HTTPAuthNTokens)tokens.getContext().get(SecurityTokens.CTX_LOGIN_HTTP);
		if(auth == null){
			auth = CXFUtils.getHTTPCredentials(message);
			if(auth!=null)tokens.getContext().put(SecurityTokens.CTX_LOGIN_HTTP,auth);
		}
		if(auth == null || auth.getUserName()==null)return false;
		String requestedUserName = auth.getUserName();
		try{
			updateDB();
		}catch(IOException ioe){
			throw new AuthenticationFailedException("Server error: could not update user database.", ioe);
		}
		readKeysFromServer(requestedUserName);
		SSHKey authData = new SSHKey();
		authData.username = requestedUserName;
		HttpServletRequest request = CXFUtils.getServletRequest(message);
		authData.token = request.getHeader(SSHKey.HEADER_PLAINTEXT_TOKEN);
		authData.signature = auth.getPasswd();
		String dn = null;
		try{
			dn=sshKeyAuth(authData);
		}catch(AuthenticationFailedException e){
			logger.debug(Log.createFaultMessage("SSH auth failed", e));
		}
		if(dn !=null){
			logger.info(requestedUserName+" --> <" + dn + ">");
			tokens.setUserName(dn);
			tokens.setConsignorTrusted(true);
			storePAMInfo(requestedUserName, tokens);
		}
		else if(logger.isDebugEnabled()){
			logger.debug("No match found for " + auth.getUserName());
		}
		return true;
	}

	public Map<String,AttributeHolders>getMappings() throws IOException {
		updateDB();
		return Collections.unmodifiableMap(db);
	}

	public void setFile(String fileName) {
		this.file = fileName;
		dbFile = new File(file);
	}

	public String getFile() {
		return file;
	}

	public void setUseAuthorizedKeys(boolean useAuthorizedKeys) {
		this.useAuthorizedKeys = useAuthorizedKeys;
	}

	public void setUpdateInterval(String update) {
		try{
			updateInterval = Long.valueOf(update);
		}
		catch(Exception ex){}
	}

	public void setDnTemplate(String dnTemplate) {
		this.dnTemplate = dnTemplate;
	}

	protected synchronized AttributeHolders getOrCreateAttributes(String user){
		AttributeHolders attr = db.get(user);
		if(attr == null){
			attr = new AttributeHolders();
			db.put(user,attr);
		}
		return attr;
	}

	protected synchronized void updateDB() throws IOException {
		if(file==null)return;
		if(lastUpdated == 0 || dbFile.lastModified() > lastUpdated){
			logger.info("(Re)reading user attributes from <"+dbFile.getAbsolutePath()+">");
			lastUpdated = dbFile.lastModified();
			removeFileEntriesFromDB();
			try(BufferedReader bufferedReader = new BufferedReader(new FileReader(dbFile))) {
				String line;
				while((line = bufferedReader.readLine())!=null) {
					if (line.startsWith("#") || line.trim().isEmpty()) {
						continue;
					}
					try{
						AttributesHolder af = new AttributesHolder(line);
						List<AttributesHolder> coll = getOrCreateAttributes(af.user).get();
						coll.add(0,af);
					}
					catch(IllegalArgumentException ex){
						logger.error("Invalid line in user db "+dbFile.getAbsolutePath());
					}
				}
			}
		}
	}

	public synchronized void readKeysFromServer(String requestedUserName) {
		if(!useAuthorizedKeys)return;
		AttributeHolders attrs = getOrCreateAttributes(requestedUserName);
		if(!attrs.wantUpdate())return;
		
		Collection<AttributesHolder> attributes = getOrCreateAttributes(requestedUserName).get();
		String dn = String.format(dnTemplate, requestedUserName);
		boolean ok = true;
		
		for(LogicalUFTPServer lServer: kernel.getAttribute(AuthServiceProperties.class).getServers()){
			try{
				UFTPDInstance uftpd = lServer.getUFTPDInstance();
				UFTPGetUserInfoRequest req = new UFTPGetUserInfoRequest(requestedUserName);
				String response = uftpd.sendRequest(req);
				parseUserInfo(response, requestedUserName, dn, attributes);
			}
			catch(Exception ex){
				Log.logException("Could not get info for user <"+requestedUserName+">", ex, logger);
				ok = false;
			}
		}
		if(ok)attrs.refresh();
		else attrs.invalidate();
	}

	protected void parseUserInfo(String info, String user, String dn, Collection<AttributesHolder> attrs) throws IOException {
		for(String line: IOUtils.readLines(new StringReader(info))){
			if(line.startsWith("Accepted key")){
				String key = line.split(":",2)[1];
				if(!hasEntry(attrs,key)){
					if(isValidKey(key)){
						attrs.add(new AttributesHolder(user,key,dn));
						logger.info("Added SSH pub key for <"+user+">");
					}
				}
			}
		}
	}

	private boolean hasEntry(Collection<AttributesHolder> attrs, String key){
		for(AttributesHolder ah: attrs){
			if(ah.sshkey.equals(key))return true;
		}
		return false;
	}

	public void removeFileEntriesFromDB(){
		for(AttributeHolders entry: db.values()){
			entry.removeFileEntries();
		}
	}

	private String sshKeyAuth(SSHKey authData){
		String username = authData.username;
		AttributeHolders attr = db.get(username);
		if(attr==null)return null;
		
		List<AttributesHolder>coll = attr.get();
		if(coll != null){
			for(AttributesHolder af : coll){
				if(af.sshkey==null || af.sshkey.isEmpty()){
					logger.error("Server config error: No public key stored for "+username);
					continue;
				}
				if(SSHUtils.validateAuthData(authData,af.sshkey)){
					return af.dn;
				}
			}
		}
		return null;
	}
	
	// store attributes for PAMAttributeSource to pick up
	private void storePAMInfo(String requestedUser, SecurityTokens tokens){
		PAMAttributes attr = new PAMAttributes();
		attr.uid = requestedUser;
		tokens.getContext().put(PAMAttributeSource.PAM_ATTRIBUTES, attr);
	}
	
	public String toString(){
		return "SSH Keys ["+(file!=null?"from <"+file+">,":"")
				+(!useAuthorizedKeys?"not ":"")+"retrieved via UFTPD]";
	}
	
	public static class AttributesHolder {

		public final String user;
		public final String sshkey;
		public final String dn;

		public AttributesHolder(String user, String sshkey, String dn){
			this.user = user;
			this.sshkey = sshkey;
			this.dn = dn;
		}

		public AttributesHolder(String line) throws IllegalArgumentException {
			String[] fields = line.split(":");
			//#user:sshkey:dn
			if (fields.length!=3) {
				logger.error("Invalid line:"+line);
				throw new IllegalArgumentException();
			}
			user=fields[0];
			sshkey=fields[1];
			dn=fields[2];
		}

		public boolean fromFile = true;
	}

	public static class AttributeHolders {
		
		private final List<AttributesHolder> coll = new ArrayList<>();
		
		private long lastUpdated;
		
		public boolean wantUpdate(){
			return lastUpdated+1000*updateInterval<System.currentTimeMillis();
		}
		
		public void refresh(){
			lastUpdated = System.currentTimeMillis();
		}
		
		public void invalidate(){
			lastUpdated = 0;
		}
		
		public void removeFileEntries(){
			Iterator<AttributesHolder>attrs = coll.iterator();
			while(attrs.hasNext()){
				AttributesHolder ah = attrs.next();
				if(ah.fromFile)attrs.remove();
			}
		}
		
		public List<AttributesHolder> get(){
			return coll;
		}
	}
	
	public static boolean isValidKey(String text){
		try{
			SSHUtils.readPubkey(text);
			return true;
		}
		catch(Exception ex){
			return false;
		}
	}
}
