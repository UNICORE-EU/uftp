package eu.unicore.uftp.dpc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * reply from server, containing a code and the actual result line(s)
 */
public class Reply {

	private int code;
	
	private final List<String>results;
	
	private String statusLine;
	
	private Reply(){
		results=new ArrayList<String>();
	}

	public int getCode(){
		return code;
	}
	
	public boolean isOK(){
		return code==200;
	}

	public boolean isError(){
		return code>=500;
	}
	
	public void assertStatus(int expectedCode) throws IOException {
		if (code!=expectedCode) {
			throw new IOException("Error: server reply " + getStatusLine());
		}
	}
	
	public String getStatusLine(){
		return statusLine;
	}
	
	public List<String>getResults(){
		return results;
	}
	
	public String toString(){
		return statusLine!=null ? statusLine : super.toString();
	}

	public static Reply read(DPCClient client)throws IOException{
		Reply r=new Reply();
		
		String reply=client.readControl();
		if(reply==null)throw new ProtocolViolationException("Got unexpected null reply");
		reply=reply.trim();
		
		//first read status code
		String codeS=reply.substring(0, 3);
		r.code=Integer.parseInt(codeS);
		r.statusLine=reply;
		
		boolean multiline = reply.length()>3 && '-' == reply.charAt(3);
		if(multiline){
			//read remaining lines
			while(true){
				reply=client.readControl();
				if(reply==null)break;
				// e.g. HASH sends repeated "213-" to avoid timeouts
				if((codeS+"-").equals(reply))continue;
				if(reply.startsWith(" ")) {
					r.results.add(reply.trim());
				}
				if(reply.startsWith(String.valueOf(r.code))){
					r.statusLine = reply;
					break;
				}
			}
		}
		return r;
	}
}
