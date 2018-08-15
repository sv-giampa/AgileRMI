package coarsermi.example.client;

import java.io.IOException;
import java.net.UnknownHostException;

import coarsermi.FailureObserver;
import coarsermi.ObjectPeer;
import coarsermi.ObjectRegistry;
import coarsermi.example.service.Service;
import coarsermi.example.service.ServiceObserver;

public class CoarseRmiClient {
	private static Service service;

	public static void main(String[] args) throws UnknownHostException, IOException, InterruptedException {
		rmiSetup();
		application();
	}

	private static void rmiSetup() throws UnknownHostException, IOException {
		// connect the ObjectPeer, and get the ObjectRegistry
		ObjectPeer peer = ObjectPeer.connect("localhost", 3031);
		ObjectRegistry registry = peer.getRegistry();

		// attach failure observer to manage connection and I/O errors
		registry.attachFailureObserver(new FailureObserver() {
			@Override
			public void failure(ObjectPeer objectPeer, Exception exception) {
				System.out.println("[FailureObserver] The object peer generated an error:\n" + exception);
			}
		});

		/*
		 * set automatic remote referencing for TestObserver objects. (TestObserver
		 * objects that will be sent over a remote invocation will be automatically
		 * referenced remotely by the server to the local client)
		 */
		registry.setAutoReferenced(ServiceObserver.class);

		// create the stub for the wanted remote object
		service = peer.getStub("service", Service.class);
	}

	/**
	 * Application (do not care about connection and other communication details)
	 * @throws InterruptedException 
	 */
	private static void application() throws InterruptedException {
		
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

		// attach observer
		service.attachObserver(o);
		
		System.out.println("square(5) = " + service.square(5));
		
		System.out.println("5+8 = " + service.add(5, 8));

		System.out.println("Printing message on the server (in 3 seconds)...");
		Thread.sleep(3000);
		service.printlnOnServer("Hello! I'm the client");
		Thread.sleep(3000);

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
	}

}
