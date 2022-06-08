package eu.unicore.uftp.authserver;

import java.io.File;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;

import eu.unicore.services.rest.security.UserPublicKeyCache.UserInfoSource;

public class MockUserInfo implements UserInfoSource{

	@Override
	public List<String> getAcceptedKeys(String userName) {
		try {
			if("demouser-dyn".equals(userName))
			return Collections.singletonList(FileUtils.readFileToString(new File("src/test/resources/ssh/id_ed25519.pub"), "UTF-8"));
		}catch(Exception e ) {}
		return null;
	}

}
