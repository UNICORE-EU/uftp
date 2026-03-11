package eu.unicore.uftp.authserver.share;

import java.util.HashSet;
import java.util.Set;

import eu.unicore.services.Kernel;
import eu.unicore.services.rest.USERestApplication;
import eu.unicore.services.security.pdp.DefaultPDP;
import eu.unicore.services.security.pdp.DefaultPDP.Rule;
import eu.unicore.services.security.pdp.PDPResult.Decision;
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
		DefaultPDP pdp = DefaultPDP.get(kernel);
		if(pdp!=null) {
			pdp.setServiceRules("share",
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
