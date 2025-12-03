package eu.unicore.uftp.authserver;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import eu.unicore.services.Kernel;
import eu.unicore.services.rest.security.UserPublicKeyCache.AttributesHolder;
import eu.unicore.services.rest.security.UserPublicKeyCache.UserInfoSource;

public class UserPubKeyLoader implements UserInfoSource {

	private final Kernel kernel;

	public UserPubKeyLoader(Kernel kernel) {
		this.kernel = kernel;
	}

	private Collection<String> getAcceptedKeys(String userName) {
		AuthServiceConfig p = kernel.getAttribute(AuthServiceConfig.class);
		Set<String> acceptedKeys = new HashSet<>();
		for(UFTPBackend uftpd: p.getServers()) {
			List<String> keys = uftpd.getAcceptedKeys(userName);
			acceptedKeys.addAll(keys);
		}
		return acceptedKeys;
	}

	@Override
	public AttributesHolder getAttributes(String userName, String identityAssign) {
		AttributesHolder ah = new AttributesHolder(userName);
		ah.getPublicKeys().addAll(getAcceptedKeys(userName));
		return ah;
	}

}