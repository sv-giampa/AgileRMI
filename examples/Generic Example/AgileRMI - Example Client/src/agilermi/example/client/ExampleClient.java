package agilermi.example.client;

import java.io.IOException;
import java.net.UnknownHostException;

import agilermi.configuration.RMIFaultHandler;
import agilermi.core.RMIHandler;
import agilermi.core.RMIRegistry;
import agilermi.example.service.RetrieverContainerExt;
import agilermi.example.service.Service;
import agilermi.example.service.ServiceObserver;
import agilermi.exception.RemoteException;

public class ExampleClient {
	private static Service service;

	public static void main(String[] args)
			throws UnknownHostException, IOException, InterruptedException, RemoteException {
		exampleSetup();
		exampleApplication();
	}

	private static void exampleSetup() throws UnknownHostException, IOException, InterruptedException {
		// Builds the registry
		RMIRegistry registry = RMIRegistry.builder().build();

		// attaches a fault handler
		registry.attachFaultHandler(new RMIFaultHandler() {
			private static final long serialVersionUID = 1162842825249205894L;

			@Override
			public void onFault(RMIHandler handler, Exception exception) {
				System.out.println("[RMIFaultHandler] An RMIHandler generated an error:\n" + exception);
			}
		});

		// creates the stub for the wanted remote object
		service = (Service) registry.getStub("localhost", 3031, "service");
	}

	/**
	 * Application (do not care about connection and other communication details)
	 * 
	 * @throws InterruptedException
	 * @throws RemoteException
	 */
	private static void exampleApplication() throws InterruptedException, RemoteException {
		System.out.println("Example client started.");

		// do demonstrations
		demoSimpleInvocations();
		demoStubAndEquals();
		demoStubRetriever();
		demoThreadInterruptionPropagation();
		demoObserverCallback();
		demoAnotherRemoteException();
		demoFaultHandler();

		System.out.println("Example client terminated.");
	}

	private static void demoAnotherRemoteException() {
		try {
			service.anotherRemoteException();
		} catch (Exception e) {
			System.out.println("[demoAnotherRemoteException] thrown exception = " + e);
			e.printStackTrace();
			try {
				Thread.sleep(100);
			} catch (InterruptedException e1) {}
		}
	}

	private static void demoSimpleInvocations() throws RemoteException, InterruptedException {
		System.out.println("[demoSimpleInvocations] square(5) = " + service.square(5));

		System.out.println("[demoSimpleInvocations] 5+8 = " + service.add(5, 8));

		System.out.println("[demoSimpleInvocations] Printing message on the server (in 3 seconds)...");
		Thread.sleep(3000);
		service.printlnOnServer("[demoSimpleInvocations] Hello! I'm the client");
		Thread.sleep(3000);
	}

	private static void demoStubAndEquals() throws RemoteException {
		// gets a reference to the same service
		Service theSameService = service.getThis();

		// shows the output of the equals() method
		System.out.println("[demoStubAndEquals] equals : " + service.equals(theSameService));
	}

	private static void demoStubRetriever() throws RemoteException {
		// shows the automatic replacing of the stub retriever
		RetrieverContainerExt stubRetrieverContainer = new RetrieverContainerExt();

		System.out.println("[demoStubRetriever] stub retriever: " + stubRetrieverContainer.getStubRetriever());

		// sends the stub retirever to the server and receive it
		Service theSameService = service.compute(stubRetrieverContainer).getService();
		System.out.println("[demoStubRetriever] stub retriever: " + stubRetrieverContainer.getStubRetriever());

		System.out.println("[demoStubRetriever] service.equals(theSameService) = " + service.equals(theSameService));
	}

	/**
	 * Demonstrates that the local thread that is invoking a remote method,
	 * propagates the interruption message to the remote thread that is handling the
	 * invocation
	 */
	private static void demoThreadInterruptionPropagation() throws InterruptedException {
		System.out.println("[demoThreadInterruptionPropagation] starting infinite cycle...");
		Thread th = new Thread(() -> {
			try {
				// calls the never-ending infiniteCycle() method
				service.infiniteCycle();
				System.out.println("ERROR: infinite cycle end!");
			} catch (InterruptedException e) {
				// prints the InterruptedException that has been thrown by the remote invocation
				// delegated thread
				e.printStackTrace();
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		});
		th.start();

		System.out.println("[demoThreadInterruptionPropagation] infinite cycle started.");
		Thread.sleep(1000);
		// interrupts the current thread, that propagates interruption to the remote
		// thread which has been delegated to manage the remote invocation to the
		// infinteCycle() method
		th.interrupt();
		System.out.println("[demoThreadInterruptionPropagation] infinite cycle interrupted.");
	}

	private static void demoObserverCallback() throws RemoteException, InterruptedException {
		// create an observer through anonymous class that prints on the client
		// standard output
		ServiceObserver o = new ServiceObserver() {
			int counter = 0;

			@Override
			public void update(Service test) {
				counter++;
				System.out.println("[demoObserverCallback] TestObserver callback #" + counter);
			}
		};
		// attach observer
		System.out.println("[demoObserverCallback] Attaching observer...");

		// observer is automatically remote referenced because its interface is marked
		// as remote
		service.attachObserver(o);

		System.out.println("[demoObserverCallback] Starting asynchronous observers calls...");
		service.startObserversCalls();
		Thread.sleep(3000);
		System.out.println("[demoObserverCallback] Asynchronous observers calls ended.");

		System.out.println("[demoObserverCallback] Detaching observer...");
		service.detachObserver(o);

		System.out.println("[demoObserverCallback] Restarting asynchronous observers calls...");
		service.startObserversCalls();
		Thread.sleep(3000);
		System.out.println("[demoObserverCallback] Asynchronous observers calls ended.");

		Thread.sleep(3000);
	}

	private static void demoFaultHandler() throws RemoteException, InterruptedException {
		String message = "[demoFaultHandler] Kill the server within 8 seconds to see the print of fault handler!";
		System.out.println(message);
		service.printlnOnServer(message);
		Thread.sleep(8000);
	}

}
