package eu.unicore.uftp.authserver;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import eu.unicore.services.Kernel;
import eu.unicore.services.KernelInjectable;
import eu.unicore.services.rest.security.UserPublicKeyCache.UserInfoSource;

public class UserPubKeyLoader implements UserInfoSource, KernelInjectable {

	private Kernel kernel;
	
	public UserPubKeyLoader(Kernel kernel) {
		this.kernel = kernel;
	}
	
	public void setKernel(Kernel kernel) {
		this.kernel = kernel;
	}

	@Override
	public Collection<String> getAcceptedKeys(String userName) {
		AuthServiceConfig p = kernel.getAttribute(AuthServiceConfig.class);
		Set<String> acceptedKeys = new HashSet<>();
		for(UFTPBackend uftpd: p.getServers()) {
			List<String> keys = uftpd.getAcceptedKeys(userName);
			acceptedKeys.addAll(keys);
		}
		return acceptedKeys;
	}

}
