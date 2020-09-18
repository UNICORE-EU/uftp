package eu.unicore.uftp.dpc;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;

import org.junit.Test;

import eu.unicore.uftp.client.FileInfo;

public class TestVarious {

	@Test
	public void testCryptoStuff()throws Exception{
		byte[] key=Utils.createKey();
		Cipher c=Utils.makeEncryptionCipher(key);
		assertNotNull(c);
		ByteArrayOutputStream bos=new ByteArrayOutputStream();
		CipherOutputStream os=new CipherOutputStream(bos,c);
		os.write("Test123 Test123 Test123".getBytes());
		os.close();
		System.out.println("Length: "+bos.size());
		Cipher c_read=Utils.makeDecryptionCipher(key);
		try(CipherInputStream is=new CipherInputStream(new ByteArrayInputStream(bos.toByteArray()),c_read)){
			byte[]data=new byte[128];
			int total=0;
			while(true){
				int l=is.read(data,total,data.length-total);
				if(l<0)break;
				total+=l;
			}
			System.out.println(total);
			String out=new String(data, 0, total);
			System.out.println(out);
		}
	}

	@Test
	public void testCryptoStuff2()throws Exception{
		byte[] key=Utils.createKey();
		
		ByteArrayOutputStream sink=new ByteArrayOutputStream();
		OutputStream os=Utils.getEncryptStream(sink, key);
		os.write("Test123 Test123 Test123".getBytes());
		os.close();
		System.out.println("Length: "+sink.size());
		InputStream source=new ByteArrayInputStream(sink.toByteArray());
		InputStream is=Utils.getDecryptStream(source, key);
		
		byte[]data=new byte[128];
		int total=0;
		while(true){
			int l=is.read(data,total,data.length-total);
			if(l<0)break;
			total+=l;
		}
		System.out.println(total);
		String out=new String(data, 0, total);
		System.out.println(out);
	}
	
	@Test
	public void testTrimFileNames(){
		String s="\"abcd\"";
		assertEquals("abcd", Utils.trim(s));
		s="\"abcd";
		assertEquals("abcd", Utils.trim(s));
		s="abcd";
		assertEquals("abcd", Utils.trim(s));
		s="abcd\"";
		assertEquals("abcd", Utils.trim(s));
	}
	
	@Test
	public void testToMListEntry(){
		FileInfo f = new FileInfo(new File("pom.xml"));
		System.out.println(f.toMListEntry());
	}

	@Test
	public void testFromMListEntry(){
		FileInfo f = new FileInfo(new File("pom.xml"));
		String s = f.toMListEntry();
		System.out.println(s);
		FileInfo f2 = FileInfo.fromMListEntry(s);
		System.out.println(f2);
		assertEquals(s, f2.toMListEntry());
	}
}
