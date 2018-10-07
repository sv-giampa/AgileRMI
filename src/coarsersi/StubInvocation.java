package coarsersi;

import java.lang.reflect.Method;

/**
 * Defines an invocation
 * @author Salvatore Giampa'
 *
 */
class StubInvocation {
	private static long nextId = 0; // incremental generation of invocation identifiers

	// invocation header
	boolean isRequest = true;   // invocation is a request, by default
	long id = nextId++;
	
	// request fields
	String objectId;
	Method method;
	Object[] params;

	// response fields
	Class<?> returnClass = null;
	Object returnValue = null;
	boolean returned = false;	// invocation is not returned, by default
	Throwable thrownException = null;

}