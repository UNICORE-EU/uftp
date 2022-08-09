package eu.unicore.uftp.dpc;

import java.io.IOException;
import java.io.OutputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;

/**
 * modified version of a {@link CipherOutputStream} that allows proper flushing of the
 * stream without closing the underlying output stream
 *
 * @author schuller
 */
public class MyCipherOutputStream extends CipherOutputStream {

	private Cipher cipher;
	private OutputStream os;
	
	public MyCipherOutputStream(OutputStream os, Cipher c) {
		super(os, c);
		this.cipher = c;
		this.os = os;
	}	

	public void finish() throws IOException {
		flush();
		byte[] buf;
		try{ 
			buf = cipher.doFinal();
		}catch(Exception ex){
			throw new IOException(ex);
		}
		os.write(buf);
		os.flush();
	}

}
