package eu.unicore.uftp.dpc;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;

import org.junit.jupiter.api.Test;

import eu.unicore.uftp.client.FileInfo;
import eu.unicore.uftp.dpc.Utils.EncryptionAlgorithm;
import eu.unicore.uftp.jparss.PSocket;

public class TestVarious {

	@Test
	public void testCrypto()throws Exception{
		for(EncryptionAlgorithm algo: new EncryptionAlgorithm[]
		   { EncryptionAlgorithm.BLOWFISH, EncryptionAlgorithm.AES })
		{
			runCryptoTest(algo);
		}
	}

	private void runCryptoTest(EncryptionAlgorithm algo)throws Exception{
		String msg = "Test123 Test123 Test123";
		byte[] key = Utils.createKey(algo);
		System.out.println("Key length: "+key.length);
		Cipher c = Utils.makeEncryptionCipher(key, algo);
		assertNotNull(c);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		CipherOutputStream os = new CipherOutputStream(bos, c);
		os.write(msg.getBytes());
		os.close();
		System.out.println("Message length: "+bos.size());
		Cipher c_read = Utils.makeDecryptionCipher(key, algo);
		try(CipherInputStream is=new CipherInputStream(new ByteArrayInputStream(bos.toByteArray()),c_read)){
			byte[]data=new byte[128];
			int total=0;
			while(true){
				int l=is.read(data,total,data.length-total);
				if(l<0)break;
				total+=l;
			}
			String out=new String(data, 0, total);
			System.out.println(out);
			assertEquals(msg, out);
		}
	}

	@Test
	public void testCryptoStream() throws Exception{
		for(EncryptionAlgorithm algo: new EncryptionAlgorithm[]
		   { EncryptionAlgorithm.BLOWFISH, EncryptionAlgorithm.AES })
		{
			runCryptoStreamTest(algo);
		}
	}

	private void runCryptoStreamTest(EncryptionAlgorithm algo) throws Exception{
		String msg = "Test123 Test123 Test123";
		byte[] key = Utils.createKey(algo);
		ByteArrayOutputStream sink = new ByteArrayOutputStream();
		OutputStream os=Utils.getEncryptStream(sink, key, algo);
		os.write(msg.getBytes());
		os.close();
		InputStream source=new ByteArrayInputStream(sink.toByteArray());
		InputStream is=Utils.getDecryptStream(source, key, algo);

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
		assertEquals(msg, out);
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
	
	@Test
	public void testJparsSocket() throws Exception {
		try(PSocket p = new PSocket(null, false, null)){
			p.init(123, 1);
			p.addSocketStream(new Socket());
			p.setKeepAlive(true);
			p.getKeepAlive();
			p.setTcpNoDelay(true);
			p.getTcpNoDelay();
			p.setSoTimeout(2000);
			p.getSoTimeout();
			p.setSoLinger(true, 200);
			p.getSoLinger();
			p.setSendBufferSize(2000);
			p.getSendBufferSize();
			p.setReceiveBufferSize(2000);
			p.getReceiveBufferSize();
			assertThrows(IllegalStateException.class, ()->p.getInetAddress());
			assertThrows(IllegalStateException.class, ()->p.getPort());
			assertThrows(IllegalStateException.class, ()->p.getLocalAddress());
			assertThrows(IllegalStateException.class, ()->p.getLocalPort());
		}
	}
}
