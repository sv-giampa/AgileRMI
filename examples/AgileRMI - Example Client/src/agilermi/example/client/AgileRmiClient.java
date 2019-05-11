package agilermi.example.client;

import java.io.IOException;
import java.net.UnknownHostException;

import agilermi.configuration.RMIFaultHandler;
import agilermi.core.RMIHandler;
import agilermi.core.RMIRegistry;
import agilermi.example.service.Service;
import agilermi.example.service.ServiceObserver;
import agilermi.exception.RemoteException;

public class AgileRmiClient {
	private static Service service;

	public static void main(String[] args)
			throws UnknownHostException, IOException, InterruptedException, RemoteException {
		rmiSetup();
		application();
	}

	private static void rmiSetup() throws UnknownHostException, IOException, InterruptedException {
		// connect the ObjectPeer, and get the ObjectRegistry
		RMIRegistry registry = RMIRegistry.builder().build();

		// attach failure observer to manage connection and I/O errors
		registry.attachFaultHandler(new RMIFaultHandler() {
			@Override
			public void onFault(RMIHandler objectPeer, Exception exception) {
				System.out.println("[RMIFaultHandler] The object peer generated an error:\n" + exception);
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
	 * @throws RemoteException
	 */
	private static void application() throws InterruptedException, RemoteException {

		Service theSameService = service.getThis();

		System.out.println("equals : " + service.equals(theSameService));

		System.out.println("starting infinite cycle...");
		Thread th = new Thread(() -> {
			try {
				service.infiniteCycle();
				System.out.println("ERROR: infinite cycle end!");
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		});
		th.start();
		System.out.println("infinite cycle started.");
		Thread.sleep(1000);
		th.interrupt();
		System.out.println("infinite cycle interrupted.");

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
	}

}
