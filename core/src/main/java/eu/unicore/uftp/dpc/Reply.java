package eu.unicore.uftp.dpc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * reply from server, containing the code, status line (includng the code)
 * and any additional result line(s)
 */
public class Reply {

	private final int code;

	private final List<String>results;

	private final String statusLine;

	private Reply(int code, String statusLine, List<String>results){
		this.results = results;
		this.statusLine = statusLine!=null? statusLine: "";
		this.code = code;
	}

	public int getCode(){
		return code;
	}

	public boolean isOK(){
		return code>=200 && code<=299;
	}

	public boolean isError(){
		return code>=500 || code < 100;
	}

	public boolean checkStatus(int expectedCode) {
		return code==expectedCode;
	}

	/**
	 * returns true if the reply code is one of the accepted ones
	 * (if null, it will check for generic error codes)
	 * 
	 * @param acceptableCodes
	 * @return
	 */
	public boolean checkStatus(int...acceptableCodes) {
		if(acceptableCodes==null || acceptableCodes.length==0) {
			return !isError();
		}
		for(int i: acceptableCodes) {
			if(code==i)return true;
		}
		return false;
	}

	public String getStatusLine(){
		return statusLine;
	}

	public List<String>getResults(){
		return results;
	}

	@Override
	public String toString(){
		return "[" + code + " " + statusLine + "]";
	}

	public static Reply read(DPCClient client)throws IOException{
		int code;
		String statusLine;
		List<String>results = new ArrayList<>();
		String reply = client.readControl();
		if(reply==null) {
			code = 1;
			statusLine = "Control connection unexpectedly at EOF.";
		}
		else {
			reply = reply.trim();
			// status code
			String codeS = reply.substring(0, 3);
			code = Integer.parseInt(codeS);
			statusLine = reply;
			boolean multiline = reply.length()>3 && '-' == reply.charAt(3);
			if(multiline){
				// read remaining lines
				while(true){
					reply = client.readControl();
					if(reply==null) {
						code = 1;
						statusLine = "Control connection unexpectedly at EOF.";
						break;
					}
					// e.g. HASH sends repeated "213-" to avoid timeouts
					if((codeS+"-").equals(reply))continue;
					if(reply.startsWith(" ")) {
						results.add(reply.trim());
					}
					if(reply.startsWith(String.valueOf(code))){
						statusLine = reply;
						break;
					}
				}
			}
		}
		return new Reply(code, statusLine, results);
	}
}
