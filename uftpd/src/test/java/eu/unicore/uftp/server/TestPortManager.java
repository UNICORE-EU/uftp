package eu.unicore.uftp.server;

import static org.junit.Assert.*;

import java.io.IOException;
import org.junit.Test;

public class TestPortManager {

	@Test
	public void testManagePortRange() throws Exception {
		int lowerBound = 20000;
		int count = 11;
		PortManager p = new PortManager(lowerBound, lowerBound+count-1);
		assertEquals(count, p.getFreePorts());
		
		for(int i = 0; i<count; i++){
			int port = p.getPort();
			assertEquals(lowerBound+i, port);
		}
		assertEquals(0, p.getFreePorts());
		// this should fail since all ports are taken
		try {
			p.getPort();
		} catch(IOException expected){
		}
		
		// free first port
		p.freePort(lowerBound);
		assertEquals(1, p.getFreePorts());
		assertEquals(lowerBound, p.getPort());
		// this should fail since all ports are taken
		try {
			p.getPort();
		} catch(IOException expected){
		}
		// free port
		p.freePort(lowerBound+5);
		assertEquals(lowerBound+5, p.getPort());
		
		// free all ports
		for(int i = 0; i<count; i++){
			p.freePort(lowerBound+i);
		}
		assertEquals(count, p.getFreePorts());
		int port = p.getPort(); 
		assertEquals(lowerBound+6, port);
	}

}
