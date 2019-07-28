package test.agilermi.pointer.passing;

import java.io.IOException;

import agilermi.core.RMIRegistry;

public class TunnelingClient {

	public static void main(String[] args) throws IOException, InterruptedException {
		RMIRegistry rMIRegistry = RMIRegistry.builder().build();
		Service remoteService = (Service) rMIRegistry.getStub("localhost", 3333, "service");

		remoteService.service().service();
	}
}
