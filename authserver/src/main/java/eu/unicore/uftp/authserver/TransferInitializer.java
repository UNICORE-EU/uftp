package eu.unicore.uftp.authserver;

import java.io.IOException;
import java.net.UnknownHostException;

import org.apache.logging.log4j.Logger;

import eu.unicore.uftp.authserver.messages.AuthResponse;
import eu.unicore.uftp.server.requests.UFTPBaseRequest;
import eu.unicore.util.Log;

/**
 * Sends transfer request to UFTPD and handles response
 * 
 * @author mgolik
 * @author jrybicki
 */
public class TransferInitializer {

    private static final Logger logger = Log.getLogger("authservice", TransferInitializer.class);
    
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
        if (response.isEmpty()||split.length<2) {
            logger.error("UFTPD server did not provide the expected response (check certificate?), got: <"+response+">");
            return new AuthResponse(false, "UFTPD Server "+server.getServerName()+" did not respond.");
        }
        AuthResponse ret = null;
        String status = split[0];
        //response = "OK::"+String.valueOf(serverThread.getPort());
        if (status.startsWith("OK")) {
            int port = Integer.parseInt(split[1].trim());
            ret = new AuthResponse(true, "", server.getHost(), port, "");
        }
        else{
        	ret = new AuthResponse(false, split[1]);
        }
        return ret;
    }

}
