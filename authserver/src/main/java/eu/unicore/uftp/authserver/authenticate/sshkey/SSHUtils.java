package eu.unicore.uftp.authserver.authenticate.sshkey;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;

import org.apache.commons.codec.binary.Base64;

import com.hierynomus.sshj.key.KeyAlgorithm;

import eu.emi.security.authn.x509.helpers.PasswordSupplier;
import net.schmizz.sshj.ConfigImpl;
import net.schmizz.sshj.DefaultConfig;
import net.schmizz.sshj.common.Buffer;
import net.schmizz.sshj.common.Factory;
import net.schmizz.sshj.common.KeyType;
import net.schmizz.sshj.signature.Signature;

public class SSHUtils extends eu.unicore.services.rest.security.sshkey.SSHUtils {

	private static final ConfigImpl sshConfig = new DefaultConfig();

	public static SSHKeyUC createAuthData(File key, PasswordSupplier pf, String token) throws GeneralSecurityException, IOException {
		SSHKeyUC authData = new SSHKeyUC();
		byte[] hashedToken = hash(token.getBytes());
		authData.token = new String(Base64.encodeBase64(hashedToken));
		PrivateKey pk = readPrivateKey(key, pf);
		
		final String kt = KeyType.fromKey(pk).toString();
		KeyAlgorithm ka = Factory.Named.Util.create(sshConfig.getKeyAlgorithms(), kt);
		Signature signature = ka.newSignature();
		if (signature == null) {
			throw new GeneralSecurityException("Could not create signature instance for " + kt + " key");
		}
		signature.initSign(pk);
		signature.update(hashedToken);
		byte[]signed = signature.sign();
		authData.signature = new String(Base64.encodeBase64(signed)); 
		return authData;
	}
	
	/**
	 * create a new authentication token
	 *  
	 * @param key - file containing the private key
	 * @param token - plaintext token to hash and sign
	 */
	public static SSHKeyUC createAuthData(File key, char[] password, String token) throws GeneralSecurityException, IOException {
		SSHKeyUC authData = new SSHKeyUC();
		byte[] hashedToken = hash(token.getBytes());
		authData.token = new String(Base64.encodeBase64(hashedToken));
		PrivateKey pk = readPrivateKey(key, 
				new PasswordSupplier() {
					@Override
					public char[] getPassword() {
						return	password;
					}
		});
		final String kt = KeyType.fromKey(pk).toString();
		KeyAlgorithm ka = Factory.Named.Util.create(sshConfig.getKeyAlgorithms(), kt);
		Signature signature = ka.newSignature();
		if (signature == null) {
			throw new GeneralSecurityException("Could not create signature instance for " + kt + " key");
		}
		signature.initSign(pk);
		signature.update(hashedToken);
		byte[]signed = signature.sign();
		if(kt.toLowerCase().contains("ecdsa")) {
			signed = encodeECDSA(signed);
		}
		authData.signature = new String(Base64.encodeBase64(signed)); 
		return authData;
	}
	
	/**
	 * cryptographic part of token validation: checks that
	 * the encrypted token can be decrypted correctly with the 
	 * given public key
	 * 
	 * @param authData 
	 * @param pubkey - PEM formatted public key
	 */
	public static boolean validateAuthData(SSHKeyUC authData, String pubkey){
		if(authData == null || authData.token == null || authData.token.isEmpty()
				||  authData.signature == null || authData.signature.isEmpty()){
			return false;
		}
		try{
			PublicKey pub = SSHUtils.readPubkey(pubkey);
			final String kt = KeyType.fromKey(pub).toString();
			KeyAlgorithm ka = Factory.Named.Util.create(sshConfig.getKeyAlgorithms(), kt);
			Signature signature = ka.newSignature();
			if (signature == null) {
				throw new GeneralSecurityException("Could not create signature instance for " + pub + " key");
			}
			signature.initVerify(pub);
			byte[]token = Base64.decodeBase64(authData.token.getBytes());
			signature.update(token);
			byte[]sig = Base64.decodeBase64(authData.signature.getBytes());
			Buffer.PlainBuffer buf = new Buffer.PlainBuffer();
			buf.putString(kt);
			buf.putBytes(sig);
			return signature.verify(buf.getCompactData());
		}
		catch(Exception ex){
			return false;
		}
	}

	public static byte[] hash(byte[]data) throws GeneralSecurityException {
		MessageDigest md = MessageDigest.getInstance("SHA1");
		md.update(data);
		return md.digest();
	}

	private static byte[] encodeECDSA(byte[] sig) {
		int rIndex = 3;
		int rLen = sig[rIndex++] & 0xff;
		byte[] r = new byte[rLen];
		System.arraycopy(sig, rIndex, r, 0, r.length);

		int sIndex = rIndex + rLen + 1;
		int sLen = sig[sIndex++] & 0xff;
		byte[] s = new byte[sLen];
		System.arraycopy(sig, sIndex, s, 0, s.length);

		System.arraycopy(sig, 4, r, 0, rLen);
		System.arraycopy(sig, 6 + rLen, s, 0, sLen);

		Buffer.PlainBuffer buf = new Buffer.PlainBuffer();
		buf.putMPInt(new BigInteger(r));
		buf.putMPInt(new BigInteger(s));

		return buf.getCompactData();
	}

}
