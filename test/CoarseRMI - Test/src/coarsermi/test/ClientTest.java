package coarsermi.test;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import coarsermi.test.service.TestIF;
import coarsermi.test.service.TestImpl;
import coarsermi.test.service.TestObserver;
import coarsersi.FailureObserver;
import coarsersi.ObjectPeer;
import coarsersi.ObjectRegistry;
import coarsersi.ObjectServer;
import coarsersi.PeerDispositionException;

@TestInstance(Lifecycle.PER_CLASS)
class ClientTest {

	// server objects
	ObjectRegistry serverRegistry;
	ObjectServer objectServer;

	// client objects
	ObjectRegistry clientRegistry;
	ObjectPeer peer;
	TestIF stub;
	
	@BeforeEach
	void setUp() throws Exception {
		serverSetUp();
		clientSetUp();
	}

	@AfterEach
	void unSet() throws Exception {
		objectServer.stop();
	}

	void serverSetUp() throws Exception {
		// object server creation
		objectServer = new ObjectServer();

		// remote objects creation
		TestIF test = new TestImpl();

		// remote objects publishing
		serverRegistry = objectServer.getRegistry();
		serverRegistry.publish("test", test, TestIF.class);

		// server start
		objectServer.start(3031);
	}

	void clientSetUp() throws Exception {
		// create connection, the ObjectPeer, and get the ObjectRegistry
		peer = ObjectPeer.connect("localhost", 3031);
		clientRegistry = peer.getRegistry();

		// attach failure observer to manage connection and I/O errors
		clientRegistry.attachFailureObserver(new FailureObserver() {
			@Override
			public void failure(ObjectPeer objectPeer, Exception exception) {
				System.out.println("The object peer generated an error:\n" + exception);
				assertEquals(peer, objectPeer);
				assertEquals(true, objectPeer.isDisposed());
			}
		});

		/*
		 * - set automatic remote referencing for TestObserver objects. (TestObserver
		 * objects that will be sent over a remote invocation will be automatically
		 * referenced remotely by the server to the local client)
		 */
		clientRegistry.setAutoReferenced(TestObserver.class);

		// - create the stubs for the wanted remote objects
		stub = peer.getStub("test", TestIF.class);
	}

	@Test
	void testDynamicProxy() {
		assertEquals(true, Proxy.isProxyClass(stub.getClass()));
	}

	@Test
	void testInt() {
		int res = stub.test(5);
		assertEquals(15, res);
	}

	@Test
	void testInteger() {
		int res = stub.test(Integer.valueOf(5));
		assertEquals(40, res);
	}

	@Test
	void testAdd() {
		int res = stub.add(20, 13);
		assertEquals(33, res);
	}

	@Test
	void testVoidReturn() {
		stub.voidReturn(5d);
	}

	@Test
	void testRemoteRef() {
		TestIF newStub = stub.remoteRef();
		assertEquals(stub, newStub); // test remote reference and flyweight pattern

		int res = newStub.test(7);
		assertEquals(21, res);
	}

	@Test
	void testThrow() {
		try {
			stub.testThrow();
		} catch (RuntimeException e) {
			assertEquals("test exception", e.getMessage());
			return;
		}
		fail("exception not thown!");
	}

	boolean observerCalled;

	@Test
	void testObserver() {
		observerCalled = false;

		TestObserver observer = new TestObserver() {
			@Override
			public void update(TestIF test) {
				observerCalled = true;
				assertEquals(stub, test); // test remote reference and flyweight pattern
			}
		};

		// observer is automatically published on the client registry, because the
		// TestObserver has been auto referenced in the setup method
		stub.testObserver(observer);

		assertEquals(true, observerCalled);
	}
	


	@Test
	void testDispositionBeforeInvocation() {
		objectServer.stop();
		
		boolean test;
		try {
			stub.test(1);
			test = false;
		} catch (PeerDispositionException e) {
			e.printStackTrace();
			test = true;
		}
		
		assertTrue(test, "No exception thrown!");

	}
	

	@Test
	void testDispositionDuringInvocation() {

		TestObserver observer = new TestObserver() {
			@Override
			public void update(TestIF test) {
				objectServer.stop();
			}
		};
		
		boolean test;
		try {
			stub.testObserver(observer);
			test = false;
		} catch (PeerDispositionException e) {
			e.printStackTrace();
			test = true;
		}
		
		assertTrue(test, "No exception thrown!");
	}
	
	@Test
	void testDispositionAfterInvocation() {

		boolean test;
		try {
			stub.test(1);
			test = false;
		} catch (PeerDispositionException e) {
			test = true;
		}
		
		assertFalse(test, "Exception should not be thrown!");
		
		objectServer.stop();
		
		try {
			stub.test(1);
			test = false;
		} catch (PeerDispositionException e) {
			test = true;
		}
		
		assertTrue(test, "No exception thrown!");

	}

}
