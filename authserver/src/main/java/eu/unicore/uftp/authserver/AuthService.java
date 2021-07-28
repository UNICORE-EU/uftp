package eu.unicore.uftp.authserver;

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.Application;

import eu.unicore.services.Kernel;
import eu.unicore.services.rest.USERestApplication;
import eu.unicore.uftp.authserver.exceptions.CustomExceptionMapper;
import eu.unicore.uftp.authserver.messages.AuthResponseBodyProvider;

/**
 * @author mgolik
 */
public class AuthService extends Application implements USERestApplication {

    public static final String DEFAULT_ENCODING = "UTF-8";

    @Override
    public Set<Class<?>> getClasses() {
        Set<Class<?>> classes = new HashSet<Class<?>>();
        //jj: you can mix providers here
        classes.add(AuthServiceImpl.class);
        classes.add(AuthResponseBodyProvider.class);        
        classes.add(CustomExceptionMapper.class);
        return classes;
    }
    
    /**
     * create the configuration object for the service
     * and store it for later use
     */
	@Override
	public void initialize(Kernel kernel)throws Exception {
		AuthServiceProperties props = kernel.getAttribute(AuthServiceProperties.class);
		if(props == null){
			props = new AuthServiceProperties(kernel.getContainerProperties().getRawProperties(), kernel);
			kernel.setAttribute(AuthServiceProperties.class, props);
		}
	}

}
