package eu.unicore.uftp.dpc;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;

import eu.unicore.uftp.dpc.DPCServer.Connection;
import eu.unicore.uftp.server.UFTPCommands;
import eu.unicore.uftp.server.requests.UFTPBaseRequest;
import eu.unicore.uftp.server.requests.UFTPSessionRequest;

/**
 * implements the server-side of the UFTP connection protocol

 * @author schuller
 */
public class ServerProtocol {

	protected Connection connection;

	protected String secret = null;

	public UFTPBaseRequest establish(Connection connection) throws IOException, AuthorizationFailureException {
		this.connection = connection;
		UFTPBaseRequest authZresult = null;
		sendVersion();

		// at this stage we support the USER, SYST and FEAT commands 
		// which a client can do at most once before the connection is considered
		// established or failed
		Collection<String> cmds = new ArrayList<>();
		cmds.add("USER");cmds.add("FEAT");cmds.add("SYST");
		while(cmds.size()>0){
			String cmd = connection.readControl();
			String chk = cmd.toUpperCase();
			if(chk.startsWith("USER ")){
				cmds.remove("USER");
				handleLogin(cmd);
				authZresult = connection.getJob(secret);
				boolean ipCheck = true;
				if(authZresult!=null && connection.isCheckClientIP()){
					ipCheck = establishWithClientIPCheck(authZresult);
				}
				if(authZresult!=null && ipCheck) {
					connection.sendControl("230 Login successful");
				}
				else {
					connection.sendError(530, "Not logged in");                        
					connection.close();
					throw new AuthorizationFailureException("Authorization failed");
				}
				if(authZresult instanceof UFTPSessionRequest) {
					break;
				}
			}
			else if (chk.startsWith("SYST")){
				cmds.remove("SYST");
				connection.sendControl("215 Unix Type: L8");
			}
			else if (chk.startsWith("FEAT")){
				cmds.remove("FEAT");
				sendFeatures();
			}
			else{
				String err = "'" + cmd + "' does not comply with protocol.";
				connection.sendError(err);                        
				connection.close();
				throw new ProtocolViolationException(err);
			}
		}
		return authZresult;
	}

	private boolean establishWithClientIPCheck(UFTPBaseRequest job) throws IOException, AuthorizationFailureException {
		InetAddress clientIP = connection.getAddress();
		for(InetAddress allowed: job.getClient()) {
			if(clientIP.equals(allowed))return true;
		}
		return false;
	}

	protected UFTPSessionRequest sendFeatures() throws IOException {
		UFTPSessionRequest authZresult = null;
		String featureReply = UFTPCommands.FEATURES_REPLY_LONG;
		Collection<String>features = connection.getFeatures();
		connection.sendControl(featureReply);
		for (String s : features) {
			connection.sendControl(" " + s );
		}
		connection.sendControl(UFTPCommands.ENDCODE);
		return authZresult;
	}

	protected void sendVersion() throws IOException {
		connection.sendControl( "220 " + DPCServer.VER);
	}

	/*
	 * Establish pseudo-FTP connection<br/>
	 * Client logs in as "anonymous" with the one-time password
	 */
	protected void handleLogin(String userLine) throws IOException {
		boolean OK = false;
		if(UFTPCommands.USER_ANON.equals(userLine)){
			connection.sendControl(UFTPCommands.REQUEST_PASSWORD);
			String passwordLine = connection.readControl();
			if(passwordLine.startsWith("USER ") || passwordLine.startsWith("PASS ")){
				secret = passwordLine.split(" ",2)[1];
				OK = true;
			}
		}
		if (!OK) {
			String err = "Client login does not comply with protocol.";
			connection.sendError(err);                        
			connection.close();
			throw new ProtocolViolationException(err);
		}
	}

}
