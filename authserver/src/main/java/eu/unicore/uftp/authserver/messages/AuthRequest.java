package eu.unicore.uftp.authserver.messages;

/**
 * @author mgolik
 */
public class AuthRequest {
	public String serverPath;
	public int streamCount = 1;
	public boolean append = false;
	public boolean compress = false;
	public String encryptionKey = null;  
	public String group = null;
	public String client = null;
	public boolean persistent = true;
	
	public boolean sessionMode = false;
	public boolean send = true;
}
