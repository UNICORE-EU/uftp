package eu.unicore.uftp.standalone.ssh;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.util.StringTokenizer;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1OutputStream;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.util.Arrays;

import com.jcraft.jsch.agentproxy.AgentProxy;
import com.jcraft.jsch.agentproxy.Buffer;
import com.jcraft.jsch.agentproxy.Identity;
import com.jcraft.jsch.agentproxy.USocketFactory;
import com.jcraft.jsch.agentproxy.connector.SSHAgentConnector;

import eu.unicore.uftp.dpc.Utils;

/**
 * support for SSH-Agent using jsch-agent-proxy<br/>
 * https://github.com/ymnk/jsch-agent-proxy/blob/master/README.md
 *
 * @author schuller
 */
public class SSHAgent {

	private AgentProxy ap ;

	private String keyFile;

	private static boolean verbose = false;

	Identity id = null;
	
	public SSHAgent() throws Exception {
		if(!isAgentAvailable())throw new IOException("SSH-Agent is not available");
		USocketFactory udsf = null;
		String[] attempts = new String[]{
				"com.jcraft.jsch.agentproxy.usocket.JNAUSocketFactory",
		"com.jcraft.jsch.agentproxy.usocket.NCUSocketFactory"};

		// if some smart ass has set the 'jna.nosys' property, we honor it, 
		// otherwise we set it to true, which usually works better
		if(System.getProperty("jna.nosys")==null){
			System.setProperty("jna.nosys","true");
		}
		for(String className: attempts){
			try{
				udsf = (USocketFactory)(Class.forName(className).getConstructor().newInstance());
				ap = new AgentProxy(new SSHAgentConnector(udsf));
				if(!ap.isRunning())throw new IOException("Error communicating with ssh-agent");
			}catch(Exception ex){}
		}
		if(ap==null || !ap.isRunning())throw new IOException("Error communicating with ssh-agent");
	}

	/**
	 * choose the identity - if not available in the agent, an exception is thrown
	 * 
	 * @param keyFile
	 * @throws IOException
	 */
	public void selectIdentity(String keyFile) throws IOException {
		this.keyFile = keyFile;
		Identity[] ids = ap.getIdentities();
		if(ids.length==0)throw new IOException("No identities loaded in SSH agent!");
		id = ids[doSelectIdentity(ids)];
	}


	// get the "intended" identity from the agent
	private int doSelectIdentity(Identity[]ids) throws IOException {
		String pubkey = FileUtils.readFileToString(new File(keyFile+".pub"), "UTF-8");
		StringTokenizer st = new StringTokenizer(pubkey);
		st.nextToken(); // ignored
		String base64 = st.nextToken();
		byte[] bytes = Base64.decodeBase64(base64);
		
		for(int i=0; i<ids.length; i++) {
			Identity id = ids[i];
			if(Arrays.areEqual(bytes, id.getBlob())){
				return i;
			}
		}
		throw new IOException("No matching identity found in agent");
	}
	
	public void setVerbose(boolean verboseS) {
		verbose = verboseS;
	}

	/**
	 * create signature for the given plaintext token
	 * @param data - plaintext token. It will be sha1-hashed and then signed
	 * @return signature (only the actual signature data without any headers)
	 * @throws GeneralSecurityException
	 */
	public byte[] sign(String data) throws GeneralSecurityException, IOException {
		byte[] signature = null;
		if(id==null) {
			Identity[] ids = ap.getIdentities();
			if(ids.length>1 && verbose) {
				System.err.println("NOTE: more than one identity in SSH agent -"
						+ " you might want to use '--identity <path_to_private_key>'");
			}
			if(ids.length==0)throw new GeneralSecurityException("No identities loaded in SSH agent!");
			id = ids[0];
		}
		byte[] blob = id.getBlob();
		byte[] rawSignature = ap.sign(blob, data.getBytes());
		//raw sig from agent contains a few extra bytes
		Buffer buf = new Buffer(rawSignature);
		String description = new String(buf.getString());
		int offset = 8 + description.length();

		signature = new byte[rawSignature.length-offset];
		System.arraycopy(rawSignature, offset, signature, 0, signature.length);
		if(description.contains("ssh-dss")){
			try{
				signature = dsa_convertToDER(signature);
			}
			catch(IOException e){
				throw new GeneralSecurityException(e);
			}
		}
		return signature;
	}

	public AgentProxy getAgent(){
		return ap;
	}

	public static boolean isAgentAvailable(){
		if(Boolean.parseBoolean(Utils.getProperty("UFTP_NO_AGENT", "false"))){
			if(verbose) {
				System.err.println("Agent DISABLED via environment setting 'UFTP_NO_AGENT'");
			}
			return false;
		}
		return SSHAgentConnector.isConnectorAvailable();
	}

	// signature DSA format
	private byte[] dsa_convertToDER(byte[] rawSignature) throws IOException {
		byte[] val = new byte[20];
		System.arraycopy(rawSignature, 0, val, 0, 20);
		BigInteger r = new BigInteger(val);
		System.arraycopy(rawSignature, 20, val, 0, 20);
		BigInteger s = new BigInteger(val);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ASN1OutputStream os = ASN1OutputStream.create(bos, ASN1Encoding.DER);
		DERSequence seq = new DERSequence(new ASN1Integer[]{new ASN1Integer(r),new ASN1Integer(s)});
		os.writeObject(seq);
		os.close();
		return bos.toByteArray();
	}

}
