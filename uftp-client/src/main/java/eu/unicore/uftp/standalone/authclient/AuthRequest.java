package eu.unicore.uftp.standalone.authclient;

/**
 * @author mgolik
 */
public class AuthRequest {
	public String serverPath;
	public int streamCount = 1;
	public boolean compress = false;
	public String encryptionKey = null;  
	public String group = null;
	public String client = null;
	public boolean persistent = true;
}
