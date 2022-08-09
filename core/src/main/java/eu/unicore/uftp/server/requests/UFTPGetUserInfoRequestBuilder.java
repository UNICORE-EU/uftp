package eu.unicore.uftp.server.requests;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Properties;

import eu.unicore.uftp.server.UFTPRequestBuilder;

public class UFTPGetUserInfoRequestBuilder implements UFTPRequestBuilder {

	@Override
	public UFTPBaseRequest createInstance(Properties props) throws UnknownHostException, IOException {
		return new UFTPGetUserInfoRequest(props.getProperty("user"));
	}

	@Override
	public String getRequestType() {
		return UFTPGetUserInfoRequest.REQUEST_TYPE;
	}

}
