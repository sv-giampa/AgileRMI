package agilermi.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import agilermi.authentication.Authenticator;
import agilermi.communication.DefaultSSLServerSocketFactory;
import agilermi.communication.DefaultSSLSocketFactory;
import agilermi.communication.gzip.GzipEndpointFactory;
import agilermi.configuration.FailureObserver;
import agilermi.core.RmiHandler;
import agilermi.core.RmiRegistry;
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

	@BeforeEach
	void setUp() throws Exception {
		serverSetUp();
		clientSetUp();
	}

	@AfterEach
	void unSet() throws Exception {
		clientRegistry.finalize();
		serverRegistry.finalize();
	}

	void serverSetUp() throws Exception {

		Authenticator authenticator = new Authenticator() {
			@Override
			public boolean authorize(String authId, Object object, Method method) {
				return true;
			}

			@Override
			public boolean authenticate(InetSocketAddress remoteAddress, String authId, String passphrase) {
				System.out.println("authentication [remoteAddress: " + remoteAddress + "; authId: " + authId
						+ "; passphrase: " + passphrase + "]");
				return "testId".equals(authId) && "testPass".equals(passphrase);
			}
		};

		// object server creation
		// serverRegistry = new RmiRegistry(3031, true);
		serverRegistry = RmiRegistry.builder()
				.setSocketFactories(new DefaultSSLSocketFactory(), new DefaultSSLServerSocketFactory())
				.setProtocolEndpointFactory(new GzipEndpointFactory()).setAuthenticator(authenticator).build();
		serverRegistry.exportInterface(TestIF.class);

		// remote objects creation
		TestIF test = new TestImpl();

		// remote objects publishing
		serverRegistry.publish("test", test);

		serverRegistry.enableListener(3031, true);
	}

	void clientSetUp() throws Exception {
		// create the registry
		clientRegistry = RmiRegistry.builder()
				.setSocketFactories(new DefaultSSLSocketFactory(), new DefaultSSLServerSocketFactory())
				.setProtocolEndpointFactory(new GzipEndpointFactory()).build();
		clientRegistry.setAuthentication("localhost", 3031, "testId", "testPass");
		rmiHandler = clientRegistry.getRmiHandler("localhost", 3031);

		// attach failure observer to manage connection and I/O errors
		clientRegistry.attachFailureObserver(new FailureObserver() {
			@Override
			public void failure(RmiHandler rmiHandler, Exception exception) {
				assertEquals(rmiHandler, rmiHandler);
				if (rmiHandler.isDisposed())
					return;
				System.out.println("The RMI handler generated an error:\n" + exception);
			}
		});

		/*
		 * - set automatic remote referencing for TestObserver objects. (TestObserver
		 * objects that will be sent over a remote invocation will be automatically
		 * referenced remotely by the server to the local client)
		 */
		clientRegistry.exportInterface(TestObserver.class);
		stub = (TestIF) clientRegistry.getStub("localhost", 3031, "test");
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
				assertTrue(stub.equals(test));
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
	 * boolean test; try { stub.test(1); test = false; } catch (RemoteException e) {
	 * e.printStackTrace(); test = true; }
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
	 * (RemoteException e) { e.printStackTrace(); test = true; }
	 * 
	 * assertTrue(test, "No exception thrown!"); }
	 * 
	 * @Test void testDispositionAfterInvocation() {
	 * 
	 * boolean test; try { stub.test(1); test = false; } catch (RemoteException e) {
	 * test = true; }
	 * 
	 * assertFalse(test, "Exception should not be thrown!");
	 * 
	 * clientRegistry.stopListener();
	 * 
	 * try { stub.test(1); test = false; } catch (RemoteException e) { test = true;
	 * }
	 * 
	 * assertTrue(test, "No exception thrown!");
	 * 
	 * }
	 */

}
