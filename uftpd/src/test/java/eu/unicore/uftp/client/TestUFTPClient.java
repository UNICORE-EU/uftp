package eu.unicore.uftp.client;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import eu.unicore.uftp.dpc.Utils;


public class TestUFTPClient {

	@Test
	public void testMakeCommandLine()throws Exception{
		String secret="123";
		String key=Utils.encodeBase64(Utils.createKey());
		String cmdline=UFTPClient.makeCommandline("localhost", 12345, 
				"src/test/resources/testfile", true, secret, 2, key, true, false, 256);
		System.out.println(cmdline);
		UFTPClient c=UFTPClient.create(cmdline.split(" "));
		assertEquals(c.getSecret(),secret);
		assertEquals(2,c.getNumConnections());
		assertEquals(key, Utils.encodeBase64(c.getKey()));
		assertEquals(1, c.getServerList().length);
	}
	
	@Test
	public void testMakeCommandLine2()throws Exception{
		String secret="123";
		String key=Utils.encodeBase64(Utils.createKey());
		String cmdline=UFTPClient.makeCommandline("localhost,localhost,localhost", 12345, 
				"src/test/resources/testfile", true, secret, 2, key, true, false, 256);
		System.out.println(cmdline);
		UFTPClient c=UFTPClient.create(cmdline.split(" "));
		assertEquals(c.getSecret(),secret);
		assertEquals(2,c.getNumConnections());
		assertEquals(key, Utils.encodeBase64(c.getKey()));
		assertEquals(3, c.getServerList().length);
	}
	
}
