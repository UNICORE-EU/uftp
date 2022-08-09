package eu.unicore.uftp.dpc;

import java.io.IOException;

/**
 * thrown when client/server protocol is violated  
 * 
 * @author Tim Pohlmann, 2010, Forschungszentrum Juelich - JSC
 */
public class ProtocolViolationException extends IOException {

	/**
	 * Default serial version ID.
	 */
	private static final long serialVersionUID = 1L;
	
	public ProtocolViolationException(String msg) {
		super(msg);
	}

}
