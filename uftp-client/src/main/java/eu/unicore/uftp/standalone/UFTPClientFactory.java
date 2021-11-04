
package eu.unicore.uftp.standalone;

import java.net.InetAddress;
import java.net.UnknownHostException;

import eu.unicore.uftp.authserver.messages.AuthResponse;
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
	
	InetAddress[] getServerArray(AuthResponse response) throws UnknownHostException {
		return Utils.parseInetAddresses(response.serverHost, null);
	}

}
