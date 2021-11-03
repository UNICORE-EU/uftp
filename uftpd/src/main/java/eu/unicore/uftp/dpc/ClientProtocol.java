package eu.unicore.uftp.dpc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import eu.unicore.uftp.server.UFTPCommands;

/**
 * Implements the client-side of the UFTP connection protocol.
 * 
 * @author schuller
 */
public class ClientProtocol {

    /**
     * String array containing request messages used to establish the pseudo-FTP
     * connection
     */
   // public static final String[] requests = {UFTPCommands.USER_ANON, UFTPCommands.USER_ANON, UFTPCommands.SYST};

	private final DPCClient client;
	
	private final List<String> features = new ArrayList<>();
	
	public ClientProtocol(DPCClient client){
		this.client = client;
	}

	/**
	 * initial connect to the server - reads 220 message and
	 * returns the server reply
	 * @throws IOException
	 */
    public String initialHandshake() throws IOException {
        // read initial line(s) from server containing code 220 and version
        Reply reply = null;
        try {
        	reply = Reply.read(client);
        }catch(ProtocolViolationException pve) {
            throw new IOException("The connection was refused by the UFTPD server. "
                    + "Please check the client IP and other parameters.");
        }
        if (reply.getCode()!=220) {
            throw new ProtocolViolationException("Code '220' expected, got " + reply.getCode());
        }
        return reply.getStatusLine();
    }

    /**
	 * login to the (U)FTP server
	 * 
	 * @param user (usually 'anonymous')
	 * @param password
	 * @throws IOException
	 */
	public void login(String user, String password) throws IOException {
        client.sendControl(user);
        String response = client.readControl(true);
        if(response.startsWith("331")){
        	client.sendControl("PASS "+password);
        }
        else{
            throw new ProtocolViolationException("'" + response + "' does not comply with protocol.");
        }
        response = client.readControl(true);
        if(!response.startsWith("230")){
            throw new ProtocolViolationException( "Login failed: "+response);
        }
	}

	public  List<String> checkFeatures() throws IOException {
		 readFeatures();
		 checkPassiveSupport(features);
		 // server must acknowledge that the v2 login was OK
		 if(!features.contains(UFTPCommands.PROTOCOL_VER_2_LOGIN_OK)){
			 throw new IOException("Unexpected server features: missing protocol v2 support.");
		 }
		 return features;
	}
	
    protected void readFeatures() throws IOException {
        String response;
        client.sendControl(UFTPCommands.SYST);
        response = client.readControl(true);
        client.sendControl(UFTPCommands.FEATURES_REQUEST);
        response = client.readControl();
        if (!response.startsWith("211")) {
            throw new ProtocolViolationException("Expected 211 reply.");
        }
        while (true) {
            response = client.readControl();
            if (response == null || features.size() > 64) {
                throw new ProtocolViolationException("Illegal server reply: too many features");
            }
            if (UFTPCommands.ENDCODE.equals(response)) {
                break;
            } else {
                features.add(response.trim());
            }
        }
    }


    protected void checkPassiveSupport(List<String> features) throws ProtocolViolationException {
        if (features == null || features.isEmpty() || !features.contains(UFTPCommands.PASV)) {
            throw new ProtocolViolationException("Illegal server reply, missing PASV feature");
        }
    }

}
