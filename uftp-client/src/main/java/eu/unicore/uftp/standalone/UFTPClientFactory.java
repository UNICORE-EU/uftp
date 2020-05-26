
package eu.unicore.uftp.standalone;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;

import eu.unicore.uftp.authserver.messages.AuthResponse;
import eu.unicore.uftp.client.UFTPClient;
import eu.unicore.uftp.client.UFTPSessionClient;
import eu.unicore.uftp.dpc.Utils;

/**
 *
 * @author jj
 */
public class UFTPClientFactory {

	public UFTPSessionClient getUFTPClient(AuthResponse response) throws UnknownHostException {
		UFTPSessionClient sc = new UFTPSessionClient(getServerArray(response),
				response.serverPort);
		sc.setSecret(response.secret);
		return sc;
	}

	public UFTPClient getClient(AuthResponse response, OutputStream target) throws UnknownHostException, IOException {
		UFTPClient uc = new UFTPClient(getServerArray(response), response.serverPort, target);
		uc.setSecret(response.secret);
		return uc;
	}
	
	public UFTPClient getClient(AuthResponse response, InputStream source) throws UnknownHostException, IOException {
		UFTPClient uc = new UFTPClient(getServerArray(response), response.serverPort, source);
		uc.setSecret(response.secret);
		return uc;
	}
	
	InetAddress[] getServerArray(AuthResponse response) throws UnknownHostException {
		return Utils.parseInetAddresses(response.serverHost, null);
	}

}
