package eu.unicore.uftp.server;

import java.io.FileNotFoundException;

import org.junit.Test;

public class TestDefaultFileAccess{

	@Test(expected=FileNotFoundException.class)
	public void testRead()throws Exception{
		new DefaultFileAccess().readFile("/etc/shadow", "root", "root", FileAccess.DEFAULT_BUFFERSIZE);
	}
	
	@Test(expected=FileNotFoundException.class)
	public void testWrite()throws Exception{
		new DefaultFileAccess().writeFile("/etc/please_do_not_write_here", false, "root", "root", FileAccess.DEFAULT_BUFFERSIZE);
	}
}
