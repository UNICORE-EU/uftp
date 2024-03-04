package eu.unicore.uftp.server.workers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import eu.unicore.uftp.dpc.Utils;

/**
 * @author bjoernh
 */
public class ForwardThread extends Thread {

	private static final int BUFFER_SIZE = 4096;
	
	private final byte[] buf;
	private final InputStream in;
	private final OutputStream out;
	
	public ForwardThread(InputStream in, OutputStream out) {
		this("ForwardThread", in, out);
	}
	
	public ForwardThread(String name, InputStream in, OutputStream out) {
		super(name);
		this.in = in;
		this.out = out;
		this.buf = new byte[BUFFER_SIZE];
	}

	@Override
	public void run() {
		try {
			int read = 0;
			while((read = in.read(buf)) > -1) {
				out.write(buf, 0, read);
				out.flush();
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			Utils.closeQuietly(in);
			Utils.closeQuietly(out);
		}
	}
	
}
