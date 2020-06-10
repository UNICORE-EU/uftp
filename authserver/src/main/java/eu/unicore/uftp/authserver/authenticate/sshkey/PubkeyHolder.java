package eu.unicore.uftp.authserver.authenticate.sshkey;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PublicKey;

import org.apache.commons.io.FileUtils;

public class PubkeyHolder {

	// key in ssh format (type, base64, comment)
	private final String encoded;
	
	private final PublicKey publicKey;

	public PubkeyHolder(String pubkey) throws IOException, GeneralSecurityException {
		this.encoded = pubkey;
		this.publicKey = SSHUtils.readPubkey(pubkey);
	}
	
	public PubkeyHolder(File file) throws IOException, GeneralSecurityException {
		this.encoded = FileUtils.readFileToString(file, "UTF-8");
		this.publicKey = SSHUtils.readPubkey(encoded);
	}

	/**
	 * get the original SSH format of the public key
	 */
	public String getEncoded() {
		return encoded;
	}

	public PublicKey getPublicKey() {
		return publicKey;
	}
	
}
