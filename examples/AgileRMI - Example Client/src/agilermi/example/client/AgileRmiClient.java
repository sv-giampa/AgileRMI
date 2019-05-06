package agilermi.example.client;

import java.io.IOException;
import java.net.UnknownHostException;

import agilermi.configuration.FaultHandler;
import agilermi.core.RmiHandler;
import agilermi.core.RmiRegistry;
import agilermi.example.service.Service;
import agilermi.example.service.ServiceObserver;

public class AgileRmiClient {
	private static Service service;

	public static void main(String[] args) throws UnknownHostException, IOException, InterruptedException {
		rmiSetup();
		application();
	}

	private static void rmiSetup() throws UnknownHostException, IOException, InterruptedException {
		// connect the ObjectPeer, and get the ObjectRegistry
		RmiRegistry registry = RmiRegistry.builder().build();

		// attach failure observer to manage connection and I/O errors
		registry.attachFaultHandler(new FaultHandler() {
			@Override
			public void onFault(RmiHandler objectPeer, Exception exception) {
				System.out.println("[FaultHandler] The object peer generated an error:\n" + exception);
			}
		});

		/*
		 * set automatic remote referencing for TestObserver objects. (TestObserver
		 * objects that will be sent over a remote invocation will be automatically
		 * referenced remotely by the server to the local client)
		 */
		registry.exportInterface(ServiceObserver.class);

		// create the stub for the wanted remote object
		service = (Service) registry.getStub("localhost", 3031, "service");
	}

	/**
	 * Application (do not care about connection and other communication details)
	 * 
	 * @throws InterruptedException
	 */
	private static void application() throws InterruptedException {

		Service theSameService = service.getThis();

		System.out.println("equals : " + service.equals(theSameService));

		// create an observer through anonymous class that prints on the client
		// standard output
		ServiceObserver o = new ServiceObserver() {
			int counter = 0;

			@Override
			public void update(Service test) {
				counter++;
				System.out.println("TestObserver callback #" + counter);
			}
		};

		System.out.println("square(5) = " + service.square(5));

		System.out.println("5+8 = " + service.add(5, 8));

		System.out.println("Printing message on the server (in 3 seconds)...");
		Thread.sleep(3000);
		service.printlnOnServer("Hello! I'm the client");
		Thread.sleep(3000);

		// attach observer
		System.out.println("Attaching observer...");
		service.attachObserver(o);

		System.out.println("Starting asynchronous observers calls...");
		service.startObserversCalls();
		Thread.sleep(3000);
		System.out.println("Asynchronous observers calls ended.");

		System.out.println("Detaching observer...");
		service.detachObserver(o);

		System.out.println("Restarting asynchronous observers calls...");
		service.startObserversCalls();
		Thread.sleep(3000);
		System.out.println("Asynchronous observers calls ended.");

		Thread.sleep(3000);

		// wait the asynchronous call of the observers
		System.out.println("Kill the server to see failure observer print!");
		service.printlnOnServer("Kill the server to see failure observer print!");
		Thread.sleep(8000);

		System.out.println("Example terminated.");
		Thread.sleep(2000);

		while (true) {
			Thread.sleep(1000);
		}
	}

}
