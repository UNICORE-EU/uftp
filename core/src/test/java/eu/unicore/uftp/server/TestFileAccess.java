package eu.unicore.uftp.server;

import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.InputStream;

import org.junit.Test;

import eu.unicore.uftp.server.unix.UnixGroup;
import eu.unicore.uftp.server.unix.UnixUser;

public class TestFileAccess{

	File lib=new File("src/main/package/distributions/Default/src/usr/share/unicore/uftpd/lib/","libuftp-unix.so");
	
	@Test
	public void testUnixUser()throws Exception{
		if(!lib.exists()){
			System.out.println("Native lib not available, skipping test.");
			return;
		}
		else{
			System.load(lib.getAbsolutePath());
		}
		UnixUser user=new UnixUser(System.getProperty("user.name"));
		assertNotNull(user);
		System.out.println(user);
	}

	@Test
	public void testUnixGroup()throws Exception{
		if(!lib.exists()){
			System.out.println("Native lib not available, skipping test.");
			return;
		}
		else{
			System.load(lib.getAbsolutePath());
		}
		UnixGroup grp=new UnixGroup("root");
		assertNotNull(grp);
		System.out.println(grp);
		System.out.println("GID: "+grp.getGid());
	}

	@Test
	public void testUID()throws Exception{
		if(!lib.exists()){
			System.out.println("Native lib not available, skipping test.");
			return;
		}
		else{
			System.load(lib.getAbsolutePath());
		}
		System.setProperty("uftp-unit-test","true");
		SetUIDFileAccess fa=new SetUIDFileAccess();
		assertNotNull(fa);
		String user=System.getProperty("user.name");
		InputStream fis=fa.readFile("src/test/resources/testfile", user, user, FileAccess.DEFAULT_BUFFERSIZE);
		assertNotNull(fis);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testUIDForbiddenRoot()throws Exception{
		if(!lib.exists()){
			System.out.println("Native lib not available, skipping test.");
			throw new IllegalArgumentException(); //to match test expectation
		}
		else{
			System.load(lib.getAbsolutePath());
		}
		System.setProperty("uftp-unit-test","true");
		SetUIDFileAccess fa=new SetUIDFileAccess();
		assertNotNull(fa);
		String user="root";
		fa.readFile("src/test/resources/testfile", user, user, FileAccess.DEFAULT_BUFFERSIZE);
	}
}
