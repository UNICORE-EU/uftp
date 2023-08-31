package eu.unicore.uftp.standalone.authclient;


/**
 * @author mgolik
 *
 */
public class AuthResponse {

    public boolean success = true;
    public String reason = "";
    public String serverHost;
    public Integer serverPort;
    public String secret = "";

    public AuthResponse(boolean success, String reason) {
        this.success = success;
        this.reason = reason;
    }

    public AuthResponse(boolean success, String reason, String server, int dataPort, String secret) {
        this(success, reason);        
        this.secret = secret;
        serverHost = server;
        serverPort = dataPort;
    }
    
    @Override
    public String toString() {
        return "AuthResponse{" + "success=" + success + ", reason=" + reason +
               ", server=" + serverHost + ", port=" + serverPort+"}";
    }   

}
