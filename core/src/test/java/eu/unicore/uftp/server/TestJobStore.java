package eu.unicore.uftp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;

import org.junit.jupiter.api.Test;

import eu.unicore.uftp.server.requests.UFTPSessionRequest;

public class TestJobStore {

	@Test
	public void testJobStore() throws Exception {
		JobStore js = new JobStore();
		var host = new InetAddress[]{InetAddress.getByName("localhost")};
		final var r = new UFTPSessionRequest(host, "me", "test123", ".");
		js.addJob(r);
		assertEquals("test123", js.getJob("test123").getSecret());
		assertTrue(js.haveJobs(host[0]));
		assertEquals(1, js.getJobs(host[0]).size());
		assertEquals(JobStore.MAX_JOB_AGE_DEFAULT, js.getJobLifetime());
	}

	@Test
	public void testRejectDuplicateSecrete() throws Exception {
		JobStore js = new JobStore();
		var host = new InetAddress[]{InetAddress.getByName("localhost")};
		final var r = new UFTPSessionRequest(host, "me", "test123", ".");
		js.addJob(r);
		assertThrows(Exception.class, () -> js.addJob(r));
	}

	@Test
	public void testJobExpiry() throws Exception {
		System.setProperty("uftpd.maxJobAge", "1");
		JobStore js = new JobStore();
		System.setProperty("uftpd.maxJobAge", String.valueOf(JobStore.MAX_JOB_AGE_DEFAULT));
		var host = new InetAddress[]{InetAddress.getByName("localhost")};
		var r = new UFTPSessionRequest(host, "me", "test123", ".");
		js.addJob(r);
		assertNotNull(js.getJob("test123"));
		js.checkForExpiredJobs();
		assertNotNull(js.getJob("test123"));
		Thread.sleep(2000);
		js.checkForExpiredJobs();
		assertNull(js.getJob("test123"));
		r = new UFTPSessionRequest(host, "me", "test123", ".");
		r.setPersistent(true);
		js.addJob(r);
		assertNotNull(js.getJob("test123"));
		Thread.sleep(2000);
		js.checkForExpiredJobs();
		assertNull(js.getJob("test123"));
	}
}
