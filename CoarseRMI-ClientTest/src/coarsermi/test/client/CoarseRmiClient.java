package coarsermi.test.client;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

import coarsermi.FailureObserver;
import coarsermi.ObjectPeer;
import coarsermi.ObjectRegistry;
import coarsermi.test.service.TestIF;
import coarsermi.test.service.TestObserver;

public class CoarseRmiClient {

	public static void main(String[] args) throws UnknownHostException, IOException {

		/*
		 * Configuration start
		 */

		// create connection, the ObjectPeer, and get the ObjectRegistry
		ObjectPeer peer = ObjectPeer.connect("localhost", 3031);
		ObjectRegistry registry = peer.getRegistry();

		// attach failure observer to manage connection and I/O errors
		registry.attachFailureObserver(new FailureObserver() {
			@Override
			public void failure(ObjectPeer objectPeer, Exception exception) {
				System.out.println("The object peer generated an error:\n" + exception);
			}
		});

		/*
		 * - set automatic remote referencing for TestObserver objects. (TestObserver
		 * objects that will be sent over a remote invocation will be automatically
		 * referenced remotely by the server to the local client)
		 */
		registry.setAutoReferenced(TestObserver.class);

		// - create the stubs for the wanted remote objects
		TestIF test = peer.getStub("test", TestIF.class);
		
		System.out.println("got stub!");

		/*
		 * Configuration end
		 */

		/*
		 * Application start (do not care about connection and other communication
		 * details)
		 */

		// - create an observer through anonymous class
		TestObserver o = new TestObserver() {
			@Override
			public void update(TestIF test) {
				System.out.println("TestObserver callback");
			}
		};
		
		// - test observer callback (the observer prints on the client standard output)
		test.testObserver(o);

		// - test calling methods with int and Integer parameters (must give different
		// results)
		System.out.println(test.test(5));
		System.out.println(test.test(Integer.valueOf(5)));
		
		System.out.println(test.test(10,23));

		// - test void return method (prints the square of the parameter on the server
		// side)
		test.test2(5d);

		// - test remote object retrieval and calls the first test method on the new
		// stub (on the server side see the print "test returns itself")
		test = test.test3();
		System.out.println(test.test(7));

		// - test the exception throwing
		try {
			test.testThrow();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		// - test a method that is undefined on the server side
		try {
			test.undefinedOnServerSide();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}
