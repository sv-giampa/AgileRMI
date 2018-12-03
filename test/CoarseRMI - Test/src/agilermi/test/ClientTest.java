package agilermi.test;

import static org.junit.Assert.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.UnknownHostException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import agilermi.FailureObserver;
import agilermi.RmiHandler;
import agilermi.RmiRegistry;
import agilermi.filter.LzcFilterFactory;
import agilermi.test.service.ObserverContainer;
import agilermi.test.service.TestIF;
import agilermi.test.service.TestImpl;
import agilermi.test.service.TestObserver;

@TestInstance(Lifecycle.PER_CLASS)
class ClientTest {

	// server objects
	RmiRegistry serverRegistry;
	RmiRegistry clientRegistry;

	// client objects
	RmiHandler rmiHandler;
	TestIF stub;

	@BeforeAll
	void setUp() throws Exception {
		serverSetUp();
		clientSetUp();
	}

	@AfterAll
	void unSet() throws Exception {
		clientRegistry.finalize();
		serverRegistry.finalize();
	}

	void serverSetUp() throws Exception {
		// object server creation
		// serverRegistry = new RmiRegistry(3031, true);
		serverRegistry = new RmiRegistry(3031, true, new LzcFilterFactory());

		// remote objects creation
		TestIF test = new TestImpl();

		// remote objects publishing
		serverRegistry.publish("test", test);
	}

	void clientSetUp() throws Exception {
		// create connection, the ObjectPeer, and get the ObjectRegistry
		// clientRegistry = new RmiRegistry();
		clientRegistry = new RmiRegistry(new LzcFilterFactory());
		rmiHandler = clientRegistry.getRmiHandler("localhost", 3031);

		// attach failure observer to manage connection and I/O errors
		clientRegistry.attachFailureObserver(new FailureObserver() {
			@Override
			public void failure(RmiHandler rmiHandler, Exception exception) {
				System.out.println("The object peer generated an error:\n" + exception);
				assertEquals(rmiHandler, rmiHandler);
				assertEquals(true, rmiHandler.isDisposed());
			}
		});

		/*
		 * - set automatic remote referencing for TestObserver objects. (TestObserver
		 * objects that will be sent over a remote invocation will be automatically
		 * referenced remotely by the server to the local client)
		 */
		clientRegistry.exportInterface(TestObserver.class);
	}

	@BeforeEach
	void getStub() throws UnknownHostException, IOException {
		// - create the stubs for the wanted remote objects
		stub = (TestIF) clientRegistry.getStub("localhost", 3031, "test", TestIF.class);
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
		// assertEquals(stub, newStub); // test remote reference and flyweight pattern

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

//	@Test
//	void testMessageTransmission() {
//		stub.testMessageTransmission(new InvocationMessage(2l, "objectId", "methodName", new Class[] { String.class },
//				new Object[] { "test" }));
//		stub.testMessageTransmission(new ReturnMessage(2l, String.class, "test", null));
//	}

	boolean observerCalled;

	@Test
	void testObserver() throws NoSuchFieldException, SecurityException {
		observerCalled = false;

		TestObserver observer = new TestObserver() {
			@Override
			public void update(TestIF test) {
				observerCalled = true;
				assertEquals(stub, test); // test remote reference and flyweight pattern
			}
		};

		ObserverContainer container = new ObserverContainer(observer);
		assertFalse(Proxy.isProxyClass(container.getObserver().getClass()));

		// observer is automatically published on the client registry, because the
		// TestObserver has been auto referenced in the setup method
		stub.testObserver(container);

		assertEquals(true, observerCalled);
		assertFalse(Proxy.isProxyClass(container.getObserver().getClass()));
	}

	/*
	 * @Test void testDispositionBeforeInvocation() { clientRegistry.stopListener();
	 * 
	 * boolean test; try { stub.test(1); test = false; } catch
	 * (RmiDispositionException e) { e.printStackTrace(); test = true; }
	 * 
	 * assertTrue(test, "No exception thrown!");
	 * 
	 * }
	 * 
	 * 
	 * 
	 * @Test void testDispositionDuringInvocation() {
	 * 
	 * TestObserver observer = new TestObserver() {
	 * 
	 * @Override public void update(TestIF test) { clientRegistry.stopListener(); }
	 * };
	 * 
	 * boolean test; try { stub.testObserver(observer); test = false; } catch
	 * (RmiDispositionException e) { e.printStackTrace(); test = true; }
	 * 
	 * assertTrue(test, "No exception thrown!"); }
	 * 
	 * @Test void testDispositionAfterInvocation() {
	 * 
	 * boolean test; try { stub.test(1); test = false; } catch
	 * (RmiDispositionException e) { test = true; }
	 * 
	 * assertFalse(test, "Exception should not be thrown!");
	 * 
	 * clientRegistry.stopListener();
	 * 
	 * try { stub.test(1); test = false; } catch (RmiDispositionException e) { test
	 * = true; }
	 * 
	 * assertTrue(test, "No exception thrown!");
	 * 
	 * }
	 */

}
