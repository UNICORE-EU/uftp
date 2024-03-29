package eu.unicore.uftp.authserver.exceptions;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 *
 * @author jrybicki
 */
public class AuthenticationFailedException extends WebApplicationException {

	private static final long serialVersionUID = 1L;
	
    public AuthenticationFailedException(String message) {
        super(Response.status(Response.Status.UNAUTHORIZED).entity(message).type(MediaType.TEXT_PLAIN).build());
    }

    public AuthenticationFailedException(String message, Throwable cause) {
        super(cause, Response.status(Response.Status.UNAUTHORIZED).entity(message).type(MediaType.TEXT_PLAIN).build());
    }
    
}
