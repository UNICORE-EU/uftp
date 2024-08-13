package eu.unicore.uftp.authserver;

import java.util.HashSet;
import java.util.Set;

import eu.unicore.services.Kernel;
import eu.unicore.services.rest.USERestApplication;
import eu.unicore.uftp.authserver.messages.AuthResponseBodyProvider;
import jakarta.ws.rs.core.Application;

/**
 * @author mgolik
 */
public class AuthService extends Application implements USERestApplication {

    @Override
    public Set<Class<?>> getClasses() {
        Set<Class<?>> classes = new HashSet<>();
        classes.add(AuthServiceImpl.class);
        classes.add(AuthResponseBodyProvider.class);        
        return classes;
    }

	@Override
	public void initialize(Kernel kernel)throws Exception {
		AuthServiceConfig.get(kernel);
	}

}
