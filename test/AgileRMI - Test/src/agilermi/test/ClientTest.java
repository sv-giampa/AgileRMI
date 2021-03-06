package agilermi.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import agilermi.authentication.RMIAuthenticator;
import agilermi.communication.DefaultSSLServerSocketFactory;
import agilermi.communication.DefaultSSLSocketFactory;
import agilermi.communication.gzip.GzipEndpointFactory;
import agilermi.configuration.RMIFaultHandler;
import agilermi.core.RMIHandler;
import agilermi.core.RMIRegistry;
import agilermi.test.service.ObserverContainer;
import agilermi.test.service.TestIF;
import agilermi.test.service.TestImpl;
import agilermi.test.service.TestObserver;

@TestInstance(Lifecycle.PER_CLASS)
class ClientTest {

	// server objects
	RMIRegistry serverRegistry;
	RMIRegistry clientRegistry;

	// client objects
	TestIF stub;

	@BeforeAll
	void setUp() throws Exception { serverSetUp(); clientSetUp(); }

	@AfterAll
	void unSet() throws Exception { clientRegistry.finalize(); serverRegistry.finalize(); }

	void serverSetUp() throws Exception {

		RMIAuthenticator rMIAuthenticator = new RMIAuthenticator() {
			@Override
			public boolean authorize(String authId, Object object, Method method) { return true; }

			@Override
			public boolean authenticate(InetSocketAddress remoteAddress, String authId, String passphrase) {
				System.out
						.println("authentication [remoteAddress: " + remoteAddress + "; authId: " + authId
								+ "; passphrase: " + passphrase + "]");
				return "testId".equals(authId) && "testPass".equals(passphrase);
			}
		};

		// object server creation
		serverRegistry = RMIRegistry
				.builder().setSocketFactories(new DefaultSSLSocketFactory(), new DefaultSSLServerSocketFactory())
				.setProtocolEndpointFactory(new GzipEndpointFactory()).setAuthenticator(rMIAuthenticator).build();

		serverRegistry.exportInterface(TestIF.class);

		// remote objects creation
		TestIF test = new TestImpl();

		// remote objects publishing
		serverRegistry.publish("test", test);

		serverRegistry.enableListener(3031, true);
	}

	void clientSetUp() throws Exception {
		// create the registry
		clientRegistry = RMIRegistry
				.builder()
				.setSocketFactories(new DefaultSSLSocketFactory(), new DefaultSSLServerSocketFactory())
				.setProtocolEndpointFactory(new GzipEndpointFactory())
				.build();
		clientRegistry.setAuthentication("localhost", 3031, "testId", "testPass");

		// attach failure observer to manage connection and I/O errors
		clientRegistry.attachFaultHandler(new RMIFaultHandler() {
			@Override
			public void onFault(RMIHandler rMIHandler, Exception exception) {
				assertEquals(rMIHandler, rMIHandler);
				if (rMIHandler.isDisposed())
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
	void testDynamicProxy() { assertEquals(true, Proxy.isProxyClass(stub.getClass())); }

	@Test
	void testInt() { int res = stub.test(5); assertEquals(15, res); }

	@Test
	void testInteger() { int res = stub.test(Integer.valueOf(5)); assertEquals(40, res); }

	@Test
	void testAdd() { int res = stub.add(20, 13); assertEquals(33, res); }

	@Test
	void testVoidReturn() { stub.voidReturn(5d); }

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

	boolean observerCalled;

	@Test
	void testObserver() throws NoSuchFieldException, SecurityException {
		observerCalled = false;

		TestObserver observer = new TestObserver() {
			@Override
			public void update(TestIF test) { observerCalled = true; assertTrue(stub.equals(test)); }
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
