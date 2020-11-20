
package eu.unicore.uftp.authserver.exceptions;


import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

import org.apache.logging.log4j.Logger;

import eu.unicore.util.Log;
/**
 *
 * @author jrybicki
 */
public class CustomExceptionMapper implements ExceptionMapper<WebApplicationException> {

    private static final Logger LOG = Log.getLogger("authservice", CustomExceptionMapper.class);
    
    @Override
    public Response toResponse(WebApplicationException e) {
        LOG.debug("Error occured", e);
        return e.getResponse();
    }

}
