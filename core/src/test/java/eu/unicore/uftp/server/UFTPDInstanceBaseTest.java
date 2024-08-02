package eu.unicore.uftp.server;

import javax.net.ssl.SSLSocketFactory;

import org.junit.jupiter.api.Test;

/**
 * @author schuller
 */
public class UFTPDInstanceBaseTest extends ClientServerTestBase {
	
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
		System.out.println(i.toString());
		System.out.println(i.getConnectionStatusMessage()
				+", v"+i.getVersion()
				+", max. sessions="+i.getSessionLimit());
	}

}
