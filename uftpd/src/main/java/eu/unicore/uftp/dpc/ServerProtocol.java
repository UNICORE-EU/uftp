package eu.unicore.uftp.dpc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.log4j.Logger;

import eu.unicore.uftp.dpc.DPCServer.Connection;
import eu.unicore.uftp.server.UFTPCommands;
import eu.unicore.uftp.server.requests.UFTPBaseRequest;
import eu.unicore.uftp.server.requests.UFTPTransferRequest;

/**
 * implements the server-side of the UFTP connection protocol

 * @author schuller
 */
public class ServerProtocol {

	private static final Logger logger = Utils.getLogger(Utils.LOG_SERVER,ServerProtocol.class);

	public static final String VER = "DPCServer 1.0";

	protected Connection connection;

	protected String secret = null;

	private int protocolVersion = 1;

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
				pseudoLogin(cmd);
				if(connection.isCheckClientIP() || protocolVersion==1){
					authZresult = establishWithClientIPCheck();
					if(protocolVersion==1)break;
				}
				else{
					authZresult = establishWithoutClientIPCheck();
					// we can handle SYST & FEAT as part of the session
					if(authZresult!=null && authZresult instanceof UFTPTransferRequest) {
						if ( ((UFTPTransferRequest)authZresult).isSession() && protocolVersion==2){
							break;
						}
					}
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

	private UFTPBaseRequest establishWithoutClientIPCheck() throws IOException, AuthorizationFailureException {
		UFTPBaseRequest job = null;
		if(secret!=null){
			job = connection.getJob(secret);
		}
		return job;
	}

	private UFTPBaseRequest establishWithClientIPCheck() throws IOException, AuthorizationFailureException {
		UFTPBaseRequest job = null;
		if(protocolVersion==1){
			syst();
			feat();
			job = authenticateV1(connection);
		}
		else{
			job = connection.getJob(secret);
		}
		return job;
	}

	protected UFTPTransferRequest sendFeatures() throws IOException {
		UFTPTransferRequest authZresult = null;

		String featureReply = UFTPCommands.FEATURES_REPLY_SHORT;
		String indent = "";
		Collection<String>features = connection.getFeatures();

		if (features.size()> 1) {
			featureReply = UFTPCommands.FEATURES_REPLY_LONG;
			indent = " ";
		}
		connection.sendControl(featureReply);
		for (String s : features) {
			connection.sendControl(indent + s );
		}
		if(features.size()> 1){
			connection.sendControl(UFTPCommands.ENDCODE);
		}
		return authZresult;
	}

	protected void sendVersion() throws IOException {
		connection.sendControl( "220 (" + VER + ")");
	}

	/*
	 * Establish pseudo-FTP connection<br/>
	 * 
	 * If the client sends the secret as password, store it.
	 * This will also choose protocol version 2.
	 */
	protected void pseudoLogin(String userLine) throws IOException {
		boolean OK = false;
		if(UFTPCommands.USER_ANON.equals(userLine)){
			connection.sendControl(UFTPCommands.REQUEST_PASSWORD);
			String passwordLine = connection.readControl();
			String password = null;
			if(passwordLine.startsWith("USER ") || passwordLine.startsWith("PASS ")){
				password = passwordLine.split(" ",2)[1];
				if(!"anonymous".equalsIgnoreCase(password)){
					secret = password;
					connection.getFeatures().add(UFTPCommands.PROTOCOL_VER_2_LOGIN_OK);
					protocolVersion = 2;
				}
				connection.sendControl("230 Login successful");
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

	private void feat()throws IOException, ProtocolViolationException {
		String request = connection.readControl();
		if (!request.equals(UFTPCommands.FEATURES_REQUEST)) {
			throw new ProtocolViolationException("Expected features request.");
		}
		sendFeatures();
	}

	private void syst()throws IOException, ProtocolViolationException {
		String request = connection.readControl();
		if(!UFTPCommands.SYST.equals(request)){
			String err = "'" + request + "' does not comply with protocol.";
			connection.sendError(err);                        
			connection.close();
			throw new ProtocolViolationException(err);
		}
		connection.sendControl("215 Unix Type: L8");
	}

	/**
	 * Authorize the client by creating a data connection and reading the 
	 * secret from the new connection. This is the original way of authorizing and
	 * the only one supported by pre-2.4.0 clients
	 * 
	 * @param connection
	 * @return UFTPTransferRequest matching the client IP address and secret
	 * @throws IOException
	 * @throws AuthorizationFailureException
	 */
	protected UFTPBaseRequest authenticateV1(Connection connection) throws IOException, AuthorizationFailureException {
		UFTPBaseRequest authZresult = null;
		Socket authSocket = connection.createNewDataConnection();
		if (authSocket == null) {
			String err = "Error opening authorisation connection";
			connection.sendError(err);
			connection.close();
			throw new ProtocolViolationException(err);
		}
		try{
			authZresult = isAuthorized(authSocket);
			if (authZresult == null) {
				connection.close();
				throw new AuthorizationFailureException("Authorization failed (wrong secret?)");
			}
		}finally{
			Utils.closeQuietly(authSocket);
		}
		return authZresult;
	}

	private UFTPBaseRequest isAuthorized(Socket authSocket) {
		List<UFTPBaseRequest> jobs = connection.getJobsForClientAddress(authSocket.getInetAddress());
		if (jobs == null || jobs.isEmpty()) {
			return null;
		}
		try {
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter((authSocket.getOutputStream())));
			secret = new BufferedReader(new InputStreamReader((authSocket.getInputStream()))).readLine();
			if ("null".equals(secret)) {
				bw.write(UFTPCommands.AUTHFAIL + ": Null secret is not allowed.\n");
				bw.flush();
				return null;
			}

			UFTPBaseRequest job = findJob(jobs, secret);
			if (job != null) {
				bw.write(UFTPCommands.AUTHOK+"\n");
				bw.flush();
				return job;
			} else {
				bw.write(UFTPCommands.AUTHFAIL + ": No matching transfer request found for " +
						"the client's IP address or secret.\n");
				bw.flush();
				return null;
			}
		} catch (IOException e) {
			logger.warn("IOException: " + e.getMessage() + " when authorising " + authSocket.getInetAddress());
			return null;
		}
	}

	private UFTPBaseRequest findJob(List<UFTPBaseRequest> jobs, String id) {
		synchronized (jobs) {
			for (UFTPBaseRequest j : jobs) {
				if (id.equals(j.getSecret())) {
					return j;
				}
			}
		}
		return null;
	}

	public int getProtocolVersion(){
		return protocolVersion;
	}

}
