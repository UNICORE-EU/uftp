package eu.unicore.uftp.authserver.authenticate.sshkey;

import eu.emi.security.authn.x509.helpers.PasswordSupplier;

public class Password implements PasswordSupplier {
	
	private final char[] password;
	
	public Password(char[] password){
		this.password = password;
	}
	
	@Override
	public char[] getPassword() {
		return password;
	}

}
