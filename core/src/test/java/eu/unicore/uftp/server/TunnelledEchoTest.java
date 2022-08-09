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
package eu.unicore.uftp.server;

import java.net.InetAddress;
import java.net.Socket;

import org.junit.Ignore;
import org.junit.Test;

import eu.unicore.uftp.client.TunnelClient;
import eu.unicore.uftp.server.requests.UFTPTunnelRequest;

/**
 * @author bjoernh
 */
@Ignore
public class TunnelledEchoTest extends ClientServerTestBase {
	
	private static final String ECHO_STRING = "To be echoed.";
	private EchoServer echoServer;
	int echoPort = 62436;
	int tunnelPort = 62437;
	
	@Test
	public void testTunneledEcho() throws Exception {
		// start echo server
		
		echoServer = new EchoServer(InetAddress.getByName("localhost"), echoPort);
		echoServer.start();

		UFTPTunnelRequest tunReq = new UFTPTunnelRequest(host, echoPort, "localhost", "nobody", "12345", null, 0);
		tunReq.sendTo(InetAddress.getByName("localhost"), jobPort);
		
		InetAddress[] servers = new InetAddress[] { InetAddress.getByName("localhost") };
		TunnelClient tc = new TunnelClient(InetAddress.getByName("localhost"), tunnelPort, servers, srvPort);
		tc.setSecret("12345");
		tc.connect();
		
		Thread client = new Thread(tc);
		client.start();
		
		Thread.sleep(2000);
		
		System.out.println("MAIN: connecting to local tunnel endpoint");
		try(Socket socket = new Socket("localhost", tunnelPort)){
			Thread.sleep(2000);
			socket.getOutputStream().write(ECHO_STRING.getBytes());
			socket.getOutputStream().flush();
			System.out.println("MAIN: reading reply...");
			byte[] buf = new byte[ECHO_STRING.length()];
			if(socket.getInputStream().read(buf) == ECHO_STRING.length()) {
				System.out.println(new String(buf));
			} else {
				System.err.println("Echo server is misbehaving. Returned: " + new String(buf));
			}
		}
		echoServer.setActive(false);
		echoServer.interrupt();
		tc.close();
	}

}
