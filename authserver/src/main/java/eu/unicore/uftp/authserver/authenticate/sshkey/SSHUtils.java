package eu.unicore.uftp.authserver.authenticate.sshkey;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.DSAPublicKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

import eu.emi.security.authn.x509.helpers.PasswordSupplier;
import eu.emi.security.authn.x509.impl.CertificateUtils;

public class SSHUtils {

	private static final Logger logger = Logger.getLogger(SSHUtils.class);
	
	public static PrivateKey readPrivateKey(File priv, PasswordSupplier password) throws IOException {
		return CertificateUtils.loadPEMPrivateKey(new FileInputStream(priv), password);
	}

	public static PublicKey readPubkey(File file) throws IOException, GeneralSecurityException {
		String pubkey = FileUtils.readFileToString(file);
		return readPubkey(pubkey);
	}
	
	public static PublicKey readPubkey(String pubkey) throws IOException, GeneralSecurityException {
		String base64, format = null;
		StringTokenizer st = new StringTokenizer(pubkey);
		try {
			format = st.nextToken();
			base64 = st.nextToken();
			try{
				st.nextToken();
			}catch(NoSuchElementException e){/*ignored since comment is not important*/}
		} catch (NoSuchElementException e) {
			throw new IllegalArgumentException("Cannot read public key, expect SSH format");
		}
		if(format.contains("rsa")){
			return readRSA(base64);
		}
		else if (format.contains("dsa") || format.contains("dss")){
			return readDSA(base64);
		}
		else{
			throw new IOException("Format "+format+" not known");
		}
	}
	
	private static PublicKey readRSA(String base64) throws IOException, GeneralSecurityException {
		byte[] decoded = Base64.decodeBase64(base64.getBytes());
		DataInputStream data = new DataInputStream(new ByteArrayInputStream(decoded));
		String type = new String(readString(data));
		if(!type.contains("rsa")){
			throw new IllegalArgumentException("Expected RSA public key, got: "+type);
		}
		BigInteger expo = new BigInteger(readString(data));
		BigInteger modulus = new BigInteger(readString(data));
		RSAPublicKeySpec keySpec = new RSAPublicKeySpec(modulus, expo);
		return KeyFactory.getInstance("RSA").generatePublic(keySpec);
	}
	
	private static PublicKey readDSA(String base64) throws IOException, GeneralSecurityException {
		byte[] decoded = Base64.decodeBase64(base64.getBytes());
		DataInputStream data = new DataInputStream(new ByteArrayInputStream(decoded));
		String type = new String(readString(data));
		if(!type.contains("dsa") && !type.contains("dss")){
			throw new IllegalArgumentException("Expected DSA public key, got: "+type);
		}
		BigInteger p = new BigInteger(readString(data));
		BigInteger q = new BigInteger(readString(data));
		BigInteger g = new BigInteger(readString(data));
		BigInteger y = new BigInteger(readString(data));
		DSAPublicKeySpec keySpec = new DSAPublicKeySpec (y,p,q,g);
		
		return KeyFactory.getInstance("DSA").generatePublic(keySpec);
	}
	
	private static byte[] readString(DataInputStream data) throws IOException {  
	    int len = data.readInt();  
	    byte[] str = new byte[len];  
	    for(int i=0; i<len;i++){
	    	str[i]=data.readByte();
	    }  
	    return str;  
	}   
	
	/**
	 * create a new authentication token
	 *  
	 * @param key - file containing the private key
	 * @param token - plaintext token to hash and sign
	 */
	public static SSHKey createAuthData(File key, PasswordSupplier password, String token) throws GeneralSecurityException, IOException {
		SSHKey authData = new SSHKey();
		byte[] hashedToken = hash(token.getBytes());
		authData.token = new String(Base64.encodeBase64(hashedToken));
		PrivateKey pk = readPrivateKey(key, password);
		String alg = "RSA".equalsIgnoreCase(pk.getAlgorithm())? "SHA1withRSA" : "SHA1withDSA";
		Signature signature = Signature.getInstance(alg);
		signature.initSign(pk);
		signature.update(hashedToken);
		byte[]signed = signature.sign();
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
	public static boolean validateAuthData(SSHKey authData, String pubkey){
		if(authData == null || authData.token == null || authData.token.isEmpty()
				||  authData.signature == null || authData.signature.isEmpty()){
			return false;
		}
		try{
			PublicKey pub = SSHUtils.readPubkey(pubkey);
			String alg = "RSA".equalsIgnoreCase(pub.getAlgorithm())? "SHA1withRSA" : "SHA1withDSA";
			Signature signature = Signature.getInstance(alg);
			signature.initVerify(pub);
			byte[]token = Base64.decodeBase64(authData.token.getBytes());
			signature.update(token);
			byte[]sig = Base64.decodeBase64(authData.signature.getBytes());
			boolean sigOK = signature.verify(sig);
			return sigOK; // TODO also, check that token is OK
		}
		catch(Exception ex){
			if(logger.isDebugEnabled()){
				logger.debug("Error verifying signature",ex);
			}
			return false;
		}
	}

	public static byte[] hash(byte[]data) throws GeneralSecurityException {
		MessageDigest md = MessageDigest.getInstance("SHA1");
		md.update(data);
		return md.digest();
	}
}
