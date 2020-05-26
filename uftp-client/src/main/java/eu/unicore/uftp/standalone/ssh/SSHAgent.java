package eu.unicore.uftp.standalone.ssh;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;

import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.DEROutputStream;
import org.bouncycastle.asn1.DERSequence;

import com.jcraft.jsch.agentproxy.AgentProxy;
import com.jcraft.jsch.agentproxy.Buffer;
import com.jcraft.jsch.agentproxy.Identity;
import com.jcraft.jsch.agentproxy.USocketFactory;
import com.jcraft.jsch.agentproxy.connector.SSHAgentConnector;

/**
 * support for SSH-Agent using jsch-agent-proxy<br/>
 * https://github.com/ymnk/jsch-agent-proxy/blob/master/README.md
 *
 * @author schuller
 */
public class SSHAgent {

	private AgentProxy ap ;

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
				udsf = (USocketFactory)(Class.forName(className).newInstance());
				ap = new AgentProxy(new SSHAgentConnector(udsf));
				if(!ap.isRunning())throw new IOException("Error communicating with ssh-agent");
			}catch(Exception ex){}
		}
		if(ap==null || !ap.isRunning())throw new IOException("Error communicating with ssh-agent");
	}

	/**
	 * create signature for the given plaintext token
	 * @param data - plaintext token. It will be sha1-hashed and then signed
	 * @return signature (only the actual signature data without any headers)
	 * @throws GeneralSecurityException
	 */
	public byte[] sign(String data) throws GeneralSecurityException {
		byte[] signature = null;
		if(ap.getIdentities().length==0)throw new GeneralSecurityException("No identities loaded in SSH agent!");
		Identity id = ap.getIdentities()[0];
		byte[] blob = id.getBlob();
		byte[] rawSignature = ap.sign(blob, data.getBytes());
		int offset = 15;
		//raw sig contains a few extra bytes: 4 bytes for the length, "ssh-rsa"
		Buffer buf = new Buffer(rawSignature);
		String description = new String(buf.getString());
		signature = new byte[rawSignature.length-offset];
		System.arraycopy(rawSignature, offset, signature, 0, signature.length);
		
		if(description.contains("ssh-dss")){
			// need to convert to proper format
			try{
				signature = convertToDER(signature);
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
		return SSHAgentConnector.isConnectorAvailable();
	}

	private byte[] convertToDER(byte[] rawSignature) throws IOException {
		byte[] val = new byte[20];
		System.arraycopy(rawSignature, 0, val, 0, 20);
		BigInteger r = new BigInteger(val);
		System.arraycopy(rawSignature, 20, val, 0, 20);
		BigInteger s = new BigInteger(val);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		DEROutputStream dos = new DEROutputStream(bos);
		DERSequence seq = new DERSequence(new ASN1Integer[]{new ASN1Integer(r),new ASN1Integer(s)});
		dos.writeObject(seq);
		dos.close();
		return bos.toByteArray();
	}

}
