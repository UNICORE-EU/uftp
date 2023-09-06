package eu.unicore.uftp.standalone.commands;

import org.junit.Before;
import org.junit.Test;

import eu.unicore.services.rest.client.UsernamePassword;
import eu.unicore.uftp.standalone.BaseServiceTest;
import eu.unicore.uftp.standalone.ClientFacade;
import eu.unicore.uftp.standalone.ConnectionInfoManager;
import eu.unicore.uftp.standalone.UFTPClientFactory;

public class TestInfo extends BaseServiceTest {
	
	ClientFacade client ;
	
	@Before
	public void setup() throws Exception {
		client = new ClientFacade(
				new ConnectionInfoManager(new UsernamePassword("demouser", "test123")),
				new UFTPClientFactory());
	}

    @Test
    public void test1() throws Exception {
    	Info info = new Info();
    	info.fileArgs = new String[] {getAuthURL("")};

    	info.raw = true;
    	info.run(client);

    	info.raw = false;
    	info.run(client);
    }

}
