package eu.unicore.uftp.server;

import java.net.InetAddress;
import java.net.Socket;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import eu.unicore.uftp.client.TunnelClient;
import eu.unicore.uftp.server.requests.UFTPTunnelRequest;

/**
 * @author bjoernh
 */
@Disabled
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
