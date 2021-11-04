/*********************************************************************************
 * Copyright (c) 2016 Forschungszentrum Juelich GmbH 
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * (1) Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the disclaimer at the end. Redistributions in
 * binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other
 * materials provided with the distribution.
 * 
 * (2) Neither the name of Forschungszentrum Juelich GmbH nor the names of its 
 * contributors may be used to endorse or promote products derived from this 
 * software without specific prior written permission.
 * 
 * DISCLAIMER
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 ********************************************************************************/
package eu.unicore.uftp.server.workers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;

import org.apache.logging.log4j.Logger;

import eu.unicore.uftp.dpc.Session;
import eu.unicore.uftp.dpc.Utils;
import eu.unicore.uftp.dpc.DPCServer.Connection;
import eu.unicore.uftp.server.ServerThread;
import eu.unicore.uftp.server.requests.UFTPTransferRequest;
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

	private static UFTPTransferRequest convert(UFTPTunnelRequest job){
		UFTPTransferRequest t = new UFTPTransferRequest(job.getClient(),job.getUser(),job.getSecret(), new File("."), false);
		return t;
	}
	
	Socket serverSocket;

	@Override
	protected void doRunSingle(Session session, File localFile) {

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
