package eu.unicore.uftp.server.workers;

import eu.unicore.uftp.dpc.DPCServer.Connection;
import eu.unicore.uftp.server.ServerThread;
import eu.unicore.uftp.server.WorkerFactory;
import eu.unicore.uftp.server.requests.UFTPBaseRequest;
import eu.unicore.uftp.server.requests.UFTPTunnelRequest;

/**
 * @author bjoernh
 */
public class UFTPTunnelWorkerFactory implements WorkerFactory {

	@Override
	public Thread createWorker(ServerThread server, Connection connection, UFTPBaseRequest job, int maxStreams,
			int bufferSize) {
		return new UFTPTunnelWorker(server, connection, (UFTPTunnelRequest) job, bufferSize);
	}

	@Override
	public Class<? extends UFTPBaseRequest> getRequestClass() {
		return UFTPTunnelRequest.class;
	}

}
