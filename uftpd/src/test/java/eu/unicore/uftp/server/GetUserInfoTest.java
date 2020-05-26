package eu.unicore.uftp.server;

import java.net.InetAddress;

import org.junit.Test;

import eu.unicore.uftp.server.requests.UFTPGetUserInfoRequest;

/**
 * @author schuller
 */
public class GetUserInfoTest extends ClientServerTestBase {
	
	@Test
	public void testGetInfo() throws Exception {
		UFTPGetUserInfoRequest req = new UFTPGetUserInfoRequest("nobody");
		String info = req.sendTo(InetAddress.getByName("localhost"), jobPort);
		System.out.println(info);
	}

}
