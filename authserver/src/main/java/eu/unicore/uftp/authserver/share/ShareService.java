package eu.unicore.uftp.authserver.share;

import java.util.HashSet;
import java.util.Set;

import eu.unicore.services.Kernel;
import eu.unicore.services.rest.USERestApplication;
import jakarta.ws.rs.core.Application;

/**
 * List, create, update and delete UFTP shares. Also, authenticate (single-file) transfers from shares.
 * 
 * @author schuller
 */
public class ShareService extends Application implements USERestApplication {

    @Override
    public Set<Class<?>> getClasses() {
        Set<Class<?>> classes = new HashSet<>();
        classes.add(ShareServiceImpl.class);
        return classes;
    }
    
    /**
     * create the configuration object for the service
     * and store it for later use
     */
	@Override
	public void initialize(Kernel kernel)throws Exception {
		ShareServiceProperties.get(kernel);
	}
	
}
