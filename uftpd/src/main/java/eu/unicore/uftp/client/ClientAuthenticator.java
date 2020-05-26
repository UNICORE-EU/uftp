package eu.unicore.uftp.client;

import eu.unicore.uftp.dpc.AuthorizationFailureException;
import java.net.Socket;

/**
 * @deprecated
 * @author jj
 */
public interface ClientAuthenticator {

    public abstract void authenticate(Socket authSocket) throws AuthorizationFailureException;
}
