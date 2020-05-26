
package eu.unicore.uftp.authserver.exceptions;


import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

import org.apache.log4j.Logger;
/**
 *
 * @author jrybicki
 */
public class CustomExceptionMapper implements ExceptionMapper<WebApplicationException> {
    private static final Logger LOG = Logger.getLogger(CustomExceptionMapper.class.getName());
    
    @Override
    public Response toResponse(WebApplicationException e) {
        LOG.debug("Error occured", e);
        return e.getResponse();
    }

}
