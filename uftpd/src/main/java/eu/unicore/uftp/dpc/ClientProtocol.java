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
    public static final String[] requests = {UFTPCommands.USER_ANON, UFTPCommands.USER_ANON, UFTPCommands.SYST};

	protected DPCClient client;
	
	public static final int protocolVersion = 2;
	
	private final List<String> features = new ArrayList<>();
	
	public ClientProtocol(){
		this(2);
	}
	
	public ClientProtocol(int version){
		if(version!=2)throw new IllegalArgumentException("Unsupported protocol version!");
	}

	/**
	 * establish a connection with the server
	 * 
	 * @param client
	 * @param secret
	 * @return the list of server features
	 * @throws IOException
	 * @throws AuthorizationFailureException
	 */
	public List<String> establishConnection(DPCClient client, String secret) throws IOException, AuthorizationFailureException {
		this.client = client;
		connectV2(secret);
		return features;
    }
	
	/**
	 * connect with protocol version 2
	 * 
	 * @throws IOException
	 * @throws AuthorizationFailureException
	 */
	protected void connectV2(String secret) throws IOException, AuthorizationFailureException {
		 initialHandshake();
		 pseudoLogin(secret);
		 readFeatures();
		 checkPassiveSupport(features);
		 // server must acknowledge that the v2 login was OK
		 if(!features.contains(UFTPCommands.PROTOCOL_VER_2_LOGIN_OK)){
			 throw new IOException("Unexpected server features: missing protocol v2 support.");
		 }
	}
	
    protected void initialHandshake() throws IOException {
        // read initial line from server containing code 220 and version
        String response;
        response = client.readControl();
        if (response == null) {
            throw new IOException("The connection was refused by the UFTPD server. "
                    + "Please check the client IP and other parameters.");
        }
        if (!response.startsWith("220")) {
            throw new ProtocolViolationException("Code '220' expected, got " + response);
        }
    }


    protected void pseudoLogin(String password) throws IOException {
        String response="";
        for (int i = 0; i < requests.length; i++) {
            if(response.startsWith("331")){
        		client.sendControl("USER "+password);
            }
            else{
            	client.sendControl(requests[i]);
            }
            response = client.readControl();
            if (!DPCServer.responses[i + 1].equals(response)) {
                String err = "'" + response + "' does not comply with protocol.";
                client.sendControl(UFTPCommands.ERROR + " " + err);
                throw new ProtocolViolationException(err);
            }
        }
    }

    protected void readFeatures() throws IOException {
        String response;
        client.sendControl(UFTPCommands.FEATURES_REQUEST);
        response = client.readControl();
        if (!response.startsWith("211")) {
            throw new ProtocolViolationException("Expected 211 reply.");
        }
        while (true) {
            response = client.readControl();
            if (response == null || features.size() > 21) {
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
