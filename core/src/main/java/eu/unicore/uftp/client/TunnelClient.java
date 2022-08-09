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
package eu.unicore.uftp.client;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import eu.unicore.uftp.dpc.Utils;
import eu.unicore.uftp.server.workers.ForwardThread;

/**
 * Provides the local endpoint of the connection tunnel 
 * 
 * @author bjoernh
 */
public class TunnelClient extends AbstractUFTPClient {

	private final ServerSocket tunnelSvrSocket;

	/**
	 * @param localSrv - address of the local tunnel endpoint
	 * @param localPort - port of the local tunnel endpoint
	 * @param servers - UFTP server address(es)
	 * @param port - UFTP server port
	 */
	public TunnelClient(InetAddress localSrv, int localPort, InetAddress[] servers, int port) {
		super(servers, port);
		try {
			tunnelSvrSocket = new ServerSocket(localPort, 0, localSrv);
		} catch (IOException e) {
			throw new RuntimeException("Unable to bind to local tunnel endpoint.", e);
		}
	}

	@Override
	public void run() {
		try {
			Socket fwdSocket = tunnelSvrSocket.accept();
			new ForwardThread(fwdSocket.getInputStream(), socket.getOutputStream()).start();
			new ForwardThread(socket.getInputStream(), fwdSocket.getOutputStream()).start();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		finally{
			Utils.closeQuietly(tunnelSvrSocket);
		}
	}

}
