package eu.unicore.uftp.authserver;

import java.io.File;

import org.apache.commons.io.FileUtils;

import eu.unicore.services.rest.security.UserPublicKeyCache.AttributesHolder;
import eu.unicore.services.rest.security.UserPublicKeyCache.UserInfoSource;

public class MockUserInfo implements UserInfoSource{

	@Override
	public AttributesHolder getAttributes(String userName, String identityAssign) {
		if("demouser-dyn".equals(userName)) {
			AttributesHolder ah = new AttributesHolder(userName);
			try {
				ah.getPublicKeys().add(FileUtils.readFileToString(new File("src/test/resources/ssh/id_ed25519.pub"), "UTF-8"));
			}catch(Exception e ) {return null;}
			return ah;
		}
		else return null;
	}

}