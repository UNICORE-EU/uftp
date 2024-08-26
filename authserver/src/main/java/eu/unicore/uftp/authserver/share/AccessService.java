package eu.unicore.uftp.authserver.share;

import java.util.HashSet;
import java.util.Set;

import eu.unicore.services.Kernel;
import eu.unicore.services.rest.USERestApplication;
import jakarta.ws.rs.core.Application;

/**
 * Access to shared files via UFTP, both anonymous and authenticated
 * 
 * @author schuller
 */
public class AccessService extends Application implements USERestApplication {

    @Override
    public Set<Class<?>> getClasses() {
        Set<Class<?>> classes = new HashSet<>();
        classes.add(AccessServiceImpl.class);
        return classes;
    }
    
    /**
     * create the configuration object for the service
     * and store it for later use
     */
	@Override
	public void initialize(Kernel kernel)throws Exception {
		ShareServiceProperties ssp = kernel.getAttribute(ShareServiceProperties.class);
		if(ssp==null){
			ssp = new ShareServiceProperties(kernel.getContainerProperties().getRawProperties());
			kernel.setAttribute(ShareServiceProperties.class, ssp);
		}
	}
	
}
