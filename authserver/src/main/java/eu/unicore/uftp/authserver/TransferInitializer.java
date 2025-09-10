package eu.unicore.uftp.authserver;

import java.io.IOException;
import java.net.UnknownHostException;

import eu.unicore.uftp.authserver.messages.AuthResponse;
import eu.unicore.uftp.server.requests.UFTPBaseRequest;

/**
 * Sends transfer request to UFTPD and handles response
 * 
 * @author mgolik
 * @author jrybicki
 */
public class TransferInitializer {

    /**
     * Method for initializing UFTP transfer on command port.
     *     
     * @param request transfer request
     * @return response of UFTPD server, with port number added
     * @throws UnknownHostException
     * @throws IOException
     */
    public AuthResponse initTransfer(UFTPBaseRequest request, UFTPDInstance server) throws UnknownHostException, IOException {
    	String response = server.sendRequest(request);
        String[] split = response.split("::");
        if (response.isEmpty()||split.length<2 || !split[0].startsWith("OK")) {
        	return new AuthResponse(false, server.getExternalSystemName()+": <"+response+">");
        }
        // response is "OK::<serverPort>;
        int port = Integer.parseInt(split[1].trim());
        return new AuthResponse(true, "", server.getHost(), port, "");
    }

}
