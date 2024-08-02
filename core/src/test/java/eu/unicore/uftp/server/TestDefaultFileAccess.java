package eu.unicore.uftp.server;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.FileNotFoundException;

import org.junit.jupiter.api.Test;

public class TestDefaultFileAccess{

	@Test
	public void testRead()throws Exception{
		assertThrows(FileNotFoundException.class, ()->{
			new DefaultFileAccess().readFile("/etc/shadow", "root", "root", FileAccess.DEFAULT_BUFFERSIZE);
		});
	}

	@Test
	public void testWrite()throws Exception{
		assertThrows(FileNotFoundException.class, ()->{
			new DefaultFileAccess().writeFile("/etc/please_do_not_write_here", false, "root", "root", FileAccess.DEFAULT_BUFFERSIZE);
		});
	}
}
