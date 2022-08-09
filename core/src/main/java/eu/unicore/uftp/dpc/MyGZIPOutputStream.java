package eu.unicore.uftp.dpc;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

public class MyGZIPOutputStream extends GZIPOutputStream {

	private OutputStream out;
	
	public MyGZIPOutputStream(OutputStream out) throws IOException {
		super(out);
		this.out = out;
	}

	@Override
	public void finish() throws IOException {
		super.finish();
		if(out instanceof MyCipherOutputStream){
			try{
				((MyCipherOutputStream)out).finish();
			}catch(Exception ex){
				throw new IOException(ex);
			}
		}
	}
	
}
