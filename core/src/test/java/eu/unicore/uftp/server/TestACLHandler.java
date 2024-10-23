package eu.unicore.uftp.server;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Test;

import eu.unicore.uftp.dpc.AuthorizationFailureException;

public class TestACLHandler {

	@Test
	public void testACLHandler() throws IOException {
		ACLHandler h = new ACLHandler(new File("src/test/resources/uftpd.acl"));
		h.checkAccess("CN=server1");
		assertThrows(AuthorizationFailureException.class, ()->{
			h.checkAccess("CN=other");
		});
	}

	@Test
	public void testACLHandlerErr1() {
		assertThrows(IOException.class, ()->{
			new ACLHandler(new File("nosuchfile"));
		});
	}
}
