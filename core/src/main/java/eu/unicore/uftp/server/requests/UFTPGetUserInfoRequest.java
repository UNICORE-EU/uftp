package eu.unicore.uftp.server.requests;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * requests info about a user
 *
 * @author schuller
 */
public class UFTPGetUserInfoRequest extends UFTPBaseRequest {

	public static final String REQUEST_TYPE = "uftp-get-user-info-request";

	public UFTPGetUserInfoRequest(String user) throws UnknownHostException, IOException {
		super(new InetAddress[]{},user,null);
	}


	public void writeEncoded(OutputStream os) throws IOException {
		super.writeEncoded(os);
		os.write("END\n".getBytes());
		os.flush();
	}

	@Override
	public String toString() {
		return "UFTPGetUserInfoRequest for user=" + getUser();
	}

	@Override
	protected String getRequestType(){
		return REQUEST_TYPE;
	}

}

