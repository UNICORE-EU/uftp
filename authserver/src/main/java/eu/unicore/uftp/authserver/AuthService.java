package eu.unicore.uftp.authserver;

import java.util.HashSet;
import java.util.Set;

import eu.unicore.services.Kernel;
import eu.unicore.services.rest.USERestApplication;
import eu.unicore.services.security.pdp.DefaultPDP;
import eu.unicore.services.security.pdp.DefaultPDP.Rule;
import eu.unicore.services.security.pdp.PDPResult.Decision;
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
		DefaultPDP pdp = DefaultPDP.get(kernel);
		if(pdp!=null) {
			pdp.setServiceRules("auth",
					DefaultPDP.PERMIT_READ,
					PERMIT_USER);
		}
	}

	public static Rule PERMIT_USER = (c,a,d)-> {
		if(c!=null && c.getRole()!=null && "user".equals(c.getRole().getName())) {
			return Decision.PERMIT;
		}
		return Decision.UNCLEAR;
	};

}
