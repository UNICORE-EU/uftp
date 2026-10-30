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
		i.setCommandHost("localhost");
		i.setCommandPort(jobPort);
		i.setSsl(false);
		i.setDescription("test");
		i.setHost("localhost");
		i.setPort(srvPort);
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
