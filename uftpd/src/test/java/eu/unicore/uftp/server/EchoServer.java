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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * @author bjoernh
 */
public class EchoServer extends Thread {
	
	private boolean active = true;
	private final ServerSocket socket;
	
	public EchoServer(InetAddress _addr, int _port) throws IOException {
		 socket = new ServerSocket(_port, 0, _addr);
	}
	
	@Override
	public void run() {
		while(active) {
			try {
				Socket conn = socket.accept();
				System.out.println("ECHO: New connection from " + conn.getRemoteSocketAddress());
				new EchoConnection(conn).start();
			} catch (IOException e) {
				e.printStackTrace();
				// keep going
			}
			
		}
	}
	
	private class EchoConnection extends Thread {
		private static final int BUFFER_SIZE = 512;
		private final Socket conn;
		private final byte[] buf = new byte[BUFFER_SIZE];

		public EchoConnection(Socket _conn) {
			this.conn = _conn;
		}
		
		@Override
		public void run() {
			InputStream in;
			try {
				in = conn.getInputStream();
			} catch (IOException e1) {
				return;
			}
			OutputStream out;
			try {
				out = conn.getOutputStream();
			} catch (IOException e1) {
				return;
			}
			
			int read;
			try {
				while((read = in.read(buf)) > -1) {
					// echo back
					System.out.println("ECHO: Echoing back: " + new String(buf, 0, read));
					out.write(buf, 0, read);
				}
			} catch (IOException e) {
				// we're done
			}
			System.out.println("ECHO: Closed connection with " + conn.getRemoteSocketAddress());
		}
	}
	
	public static void main(String[] args) throws IOException {
		EchoServer srv = new EchoServer(null, 8007);
		srv.run();
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}
}
