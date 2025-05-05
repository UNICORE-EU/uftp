package eu.unicore.uftp.dpc;

import java.io.IOException;
import java.net.InetAddress;

import eu.unicore.uftp.dpc.DPCServer.Connection;
import eu.unicore.uftp.server.UFTPCommands;
import eu.unicore.uftp.server.requests.UFTPBaseRequest;

/**
 * implements the server-side of the UFTP connection protocol

 * @author schuller
 */
public class ServerProtocol {

	protected Connection connection;

	protected String secret = null;

	public UFTPBaseRequest establish(Connection connection) throws IOException, AuthorizationFailureException {
		this.connection = connection;
		connection.sendControl( "220 " + DPCServer.VER);
		UFTPBaseRequest authZresult = null;
		// at this stage we support only the USER command
		String cmd = connection.readControl();
		if(cmd.toUpperCase().startsWith("USER ")){
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
		}
		else{
			String err = "'" + cmd + "' does not comply with protocol.";
			connection.sendError(err);                        
			connection.close();
			throw new ProtocolViolationException(err);
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

	/*
	 * Establish pseudo-FTP connection<br/>
	 * Client logs in as "anonymous" with the one-time password
	 */
	private void handleLogin(String userLine) throws IOException {
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
