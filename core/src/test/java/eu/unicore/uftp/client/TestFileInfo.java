package eu.unicore.uftp.client;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class TestFileInfo {

	@Test
	public void testParse(){
		long time=System.currentTimeMillis();
		long size=4096;
		String path="foo";
		String ls="D "+size+" "+time+" "+path+"\r\n";
		FileInfo fi=new FileInfo(ls);
		assertEquals(time,fi.getLastModified());
		assertEquals(size,fi.getSize());
		assertEquals(path,fi.getPath());
		assertTrue(fi.isDirectory());
		System.out.println(fi);
	}

	@Test
	public void testParse2(){
		long time=System.currentTimeMillis();
		long size=4096;
		String path="a file with spaces";
		String ls="- "+size+" "+time+" "+path+"\r\n";
		FileInfo fi=new FileInfo(ls);
		assertEquals(time,fi.getLastModified());
		assertEquals(size,fi.getSize());
		assertEquals(path,fi.getPath());
		assertFalse(fi.isDirectory());
		System.out.println(fi);
	}

	@Test
	public void testOutputFormatting() throws Exception {
		System.out.println("LS style date: "+FileInfo.getUnixDate(System.currentTimeMillis()));
		String ftpDate = FileInfo.getFTPDate(System.currentTimeMillis());
		System.out.println("MLST style date: "+ftpDate);
		System.out.println(FileInfo.parseFTPDate(ftpDate));
		String isoDate = FileInfo.getISO8601Date(System.currentTimeMillis());
		System.out.println("ISO date: "+isoDate);
	}
}
