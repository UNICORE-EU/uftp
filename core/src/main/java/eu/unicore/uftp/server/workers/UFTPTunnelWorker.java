package eu.unicore.uftp.server.workers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;

import org.apache.logging.log4j.Logger;

import eu.unicore.uftp.dpc.DPCServer.Connection;
import eu.unicore.uftp.dpc.Session;
import eu.unicore.uftp.dpc.Utils;
import eu.unicore.uftp.server.ServerThread;
import eu.unicore.uftp.server.requests.UFTPSessionRequest;
import eu.unicore.uftp.server.requests.UFTPTunnelRequest;

/**
 * @author bjoernh
 */
public class UFTPTunnelWorker extends UFTPWorker {

	private static final Logger logger = Utils.getLogger(Utils.LOG_SERVER, UFTPTunnelWorker.class);

	private final UFTPTunnelRequest job;

	private InputStream clientIn;
	private OutputStream clientOut;

	public UFTPTunnelWorker(ServerThread server, Connection connection, UFTPTunnelRequest job, int bufferSize) {
		super(server,connection,convert(job),1,bufferSize);
		this.job = job;
	}

	private static UFTPSessionRequest convert(UFTPTunnelRequest job){
		UFTPSessionRequest t = new UFTPSessionRequest(job.getClient(),job.getUser(),
				job.getSecret(), ".");
		return t;
	}
	
	Socket serverSocket;

	@Override
	protected void runSession(Session session) {

		logger.info("Created tunnel worker for " + job.getTargetHost() + ":" + job.getTargetPort());

		try {
			Socket dataSocket = connection.getDataSockets().get(0);
			this.clientIn = dataSocket.getInputStream();
			this.clientOut = dataSocket.getOutputStream();
		} catch (IOException e) {
			throw new RuntimeException("Broken client side tunnel connection", e);
		}
		
		Runnable r = new Runnable(){
			public void run(){
				try {
					serverSocket = new Socket(job.getTargetHost(), job.getTargetPort());
				} catch (IOException e) {
					throw new RuntimeException("Error connecting to local ports for forwarding.", e);
				}
			};
		};
		try{
			server.getFileAccess().asUser(r, job.getUser(), null);
		} catch (Exception e) {
			throw new RuntimeException("Error connecting to local ports for forwarding.", e);
		}
		
		try {
			logger.info("Starting client-server forwarding ...");
			new ForwardThread("clientToServer", clientIn, serverSocket.getOutputStream()).start();
			logger.info("Starting server-client forwarding ...");
			new ForwardThread("serverToClient", serverSocket.getInputStream(), clientOut).run();
			logger.info("Exiting tunnel worker");
			
		} catch (UnknownHostException e) {
			logger.error(e); 
		} catch (IOException e) {
			logger.error(e); 
		} finally {
			Utils.closeQuietly(serverSocket);
		}
	}
}
