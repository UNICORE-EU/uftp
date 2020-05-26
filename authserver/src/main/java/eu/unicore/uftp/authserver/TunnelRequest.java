package eu.unicore.uftp.authserver;

import java.util.Random;

import eu.unicore.uftp.authserver.authenticate.UserAttributes;
import eu.unicore.uftp.authserver.messages.CreateTunnelRequest;
import eu.unicore.uftp.dpc.Utils;
import eu.unicore.uftp.server.requests.UFTPTunnelRequest;

/**
 * @author schuller
 */
public final class TunnelRequest extends UFTPTunnelRequest {
    
    static char[] chars = "abcdefghijklmnopqrstuvwxyz".toCharArray();
    
    static String generateSecret() {
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 20; i++) {
            char c = chars[random.nextInt(chars.length)];
            sb.append(c);
        }
        String output = sb.toString();
        return output;
    }

    public TunnelRequest(CreateTunnelRequest tunnelRequest, UserAttributes authData, String clientIp) {
        super(Utils.parseInetAddresses(clientIp, logger), 
        		tunnelRequest.targetPort,
        		tunnelRequest.targetHost,
        		authData.uid, 
        		generateSecret(),
        		tunnelRequest.encryptionKey,
        		authData.rateLimit);
    }

}
