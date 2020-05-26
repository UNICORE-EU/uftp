package eu.unicore.uftp.dpc;

import java.io.IOException;

/**
 * thrown when authorisation fails in DPCServer.accept() or DPCClient.connect()
 * 
 * @author Tim Pohlmann, 2010, Forschungszentrum Juelich - JSC
 */
public class AuthorizationFailureException extends IOException {

	private static final long serialVersionUID = 1L;
	
	public AuthorizationFailureException(String msg) {
		super(msg);
	}

}
