package eu.unicore.uftp.standalone.commands;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import eu.unicore.services.rest.client.UsernamePassword;
import eu.unicore.uftp.standalone.BaseServiceTest;
import eu.unicore.uftp.standalone.ClientDispatcher;
import eu.unicore.uftp.standalone.ClientFacade;
import eu.unicore.uftp.standalone.ConnectionInfoManager;

public class TestAuth extends BaseServiceTest {

	ClientFacade client ;

	@Before
	public void setup() throws Exception {
		client = new ClientFacade(
				new ConnectionInfoManager(new UsernamePassword("demouser", "test123")));
	}

    @Test
    public void testCmd() throws Exception {
    	String[] args = new String[]{ new Auth().getName(), "-h" };
    	ClientDispatcher._main(args);
    }

    @Test
    public void testAuth() throws Exception {
    	String[] args = new String[]{ new Auth().getName(),
    			"-u", "demouser:test123", "-v",
    			getAuthURL("")
    	};
    	assertEquals(0, ClientDispatcher._main(args));
    }

}
