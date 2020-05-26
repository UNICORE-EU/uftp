package eu.unicore.uftp.authserver.messages;

/**
 * @author schuller
 */
public class CreateTunnelRequest {

	public String targetHost;
    public int targetPort = -1;  
	public String group = null;
	public String client = null;
	public String encryptionKey = null; 

}
