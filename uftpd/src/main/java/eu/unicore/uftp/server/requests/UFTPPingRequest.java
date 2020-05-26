package eu.unicore.uftp.server.requests;

import java.io.IOException;
import java.io.OutputStream;

/**
 * used to "ping" the UFTPD server
 *
 * @author schuller
 */
public class UFTPPingRequest extends UFTPBaseRequest {

	public UFTPPingRequest() {
		super(null, null, null);
	}

	public static final String REQUEST_TYPE = "uftp-ping-request";
    
    @Override
    protected String getRequestType(){
    	return REQUEST_TYPE;
    }
    
    /**
     * writes out the job in properties format 'client-ip=...' 'send=...' etc
     *
     * @param os output stream where the options are written to
     */
    public void writeEncoded(OutputStream os) throws IOException {
        os.write(("request-type="+ REQUEST_TYPE + "\n").getBytes());
        os.write("END\n".getBytes());
        os.flush();
    }

}
