package eu.unicore.uftp.standalone.oidc;

import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.nio.CharBuffer;
import java.nio.channels.Channels;

import org.json.JSONObject;

import jnr.constants.platform.ProtocolFamily;
import jnr.constants.platform.Sock;
import jnr.unixsocket.UnixSocketAddress;
import jnr.unixsocket.UnixSocketChannel;

/**
 * Connector to the 'oidc-agent' via UNIX domain socket.
 * 
 * Modeled after the JSch ssh-agent proxy
 *  
 * @author schuller
 */
public class OIDCAgentProxy {

	private static final String OIDC_SOCK = "OIDC_SOCK";
	
	public OIDCAgentProxy() {}

	public static boolean isConnectorAvailable(){
		return isConnectorAvailable(null);
	}

	public static boolean isConnectorAvailable(String usocketPath){
		return System.getenv(OIDC_SOCK)!=null || usocketPath!=null;
	}
	
	public String send(String data) throws Exception {
		return send(data,System.getenv(OIDC_SOCK));
	}
	
	public String send(String data, String path) throws Exception {
		UnixSocketAddress address = new UnixSocketAddress(path);
		UnixSocketChannel channel = createChannel();
        channel.connect(address);
        try(PrintWriter w = new PrintWriter(Channels.newOutputStream(channel));
        	InputStreamReader r = new InputStreamReader(Channels.newInputStream(channel)))
        {
        	w.print(data);
        	w.flush();
        	CharBuffer result = CharBuffer.allocate(4096);
        	r.read(result);
        	result.flip();
        	return result.toString();
        }
	}
	
	// testing...
	public static void main(String[] args) throws Exception {
		OIDCAgentProxy ap = new OIDCAgentProxy();
		String path = "/tmp/oidc-OHVSC9/oidc-agent.24569";
		JSONObject j = new JSONObject();
		j.put("request", "xxaccount_list");
		System.out.println("reply: "+ap.send(j.toString(), path));
		
//		j = new JSONObject();
//		j.put("request", "access_token");
//		j.put("account", "hbp");
//		System.out.println("reply: "+ap.send(j.toString(), path));
		
	}
	
	
	protected UnixSocketChannel createChannel() throws Exception {
		// must use reflection because most things in JNR are package private 
		Class<?> c = Class.forName("jnr.unixsocket.Native");
		Method m = null;
		for (Method x: c.getDeclaredMethods()){
			if("socket".equals(x.getName())){
				m = x;
				break;
			}
		}
		m.setAccessible(true);
		int fd = (int)(m.invoke(null, ProtocolFamily.PF_UNIX, Sock.SOCK_STREAM, 0));
		return UnixSocketChannel.fromFD(fd);
	}
	
	protected void foo(){
	}

}
