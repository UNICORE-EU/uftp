package eu.unicore.uftp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.net.ssl.SSLSocketFactory;

import org.junit.jupiter.api.Test;

import eu.unicore.uftp.server.requests.UFTPPingRequest;

/**
 * @author schuller
 */
public class TestUFTPDInstanceBase extends ClientServerTestBase {

	@Test
	public void test1() throws Exception {
		UFTPDInstanceBase i = new UFTPDInstanceBase() {
			protected SSLSocketFactory getSSLSocketFactory() {return null;}
		};
		i.commandHost = "localhost";
		i.commandPort = jobPort;
		i.ssl = false;
		i.description = "test";
		i.host = "localhost";
		i.port = srvPort;
		assertEquals("localhost", i.getCommandHost());
		assertEquals(jobPort, i.getCommandPort());
		assertEquals("localhost", i.getHost());
		assertEquals(srvPort, i.getPort());
		var connStatus = i.checkConnection();
		assertTrue(connStatus.getM1());
		assertEquals("OK", connStatus.getM2());
		System.out.println(i.toString());
		System.out.println("OK="+i.isOK()
				+", v"+i.getVersion()
				+", max. sessions="+i.getSessionLimit());
		i.sendRequest(new UFTPPingRequest());
		i.notOK("Some error");
		i.setOK();
	}

}
