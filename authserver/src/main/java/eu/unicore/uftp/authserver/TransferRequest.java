package eu.unicore.uftp.authserver;

import java.io.File;
import java.util.Random;

import eu.unicore.uftp.authserver.authenticate.UserAttributes;
import eu.unicore.uftp.authserver.messages.AuthRequest;
import eu.unicore.uftp.dpc.Utils;
import eu.unicore.uftp.server.requests.UFTPTransferRequest;
import eu.unicore.uftp.server.workers.UFTPWorker;

/**
 * @author mgolik
 */
public final class TransferRequest extends UFTPTransferRequest {
    
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

    public TransferRequest(AuthRequest authRequest, UserAttributes authData, String clientIp) {
        super(Utils.parseInetAddresses(clientIp, logger), authData.uid, generateSecret(), 
        		authRequest.sessionMode? new File(UFTPWorker.sessionModeTag): new File(authRequest.serverPath),
        		authRequest.send);
        
        setStreams(authRequest.streamCount);
        setGroup(authData.gid);
        setAppend(authRequest.append);
        setKey(Utils.decodeBase64(authRequest.encryptionKey));
        setCompress(authRequest.compress);
        setIncludes(authData.includes);
        setExcludes(authData.excludes);
        setRateLimit(authData.rateLimit);
        setPersistent(authRequest.persistent);
    }

}
