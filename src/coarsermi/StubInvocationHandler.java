package coarsermi;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * Defines the invocation handler for the object stubs
 * 
 * @author Salvatore Giampa'
 *
 */
class StubInvocationHandler implements InvocationHandler {
	
	private static int nextId = 1;
	
	private int id = nextId++;
	private String objectId;
	private ObjectPeer peer;

	public StubInvocationHandler(String objectId, ObjectPeer peer) {
		this.objectId = objectId;
		this.peer = peer;
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

		// create the stub invocation
		StubInvocation invocation = new StubInvocation();
		invocation.objectId = objectId;
		invocation.method = method;
		invocation.params = args;
		
		String methodName = method.getName();
		Class<?>[] parameterTypes = method.getParameterTypes();

		// compute hashCode() method locally
		if(methodName.equals("hashCode") && parameterTypes.length == 0) {
			return hashCode();
		}
		
		// verify alias in the equals() method
		if(methodName.equals("equals") && parameterTypes.length == 1 && parameterTypes[0] == Object.class) {
			return proxy == args[0];
		}

		if (peer.isDisposed()) {
			throw new PeerDispositionException();
		} else {

			peer.putInvocation(invocation);
			
			try {
				synchronized (invocation) {
					while (!invocation.returned) {
						invocation.wait();
					}
					
					if (invocation.thrownException != null) {
						invocation.thrownException.fillInStackTrace();
						throw invocation.thrownException;
					}
				}
			} catch (InterruptedException e) {}

		}

		Class<?> returnType = method.getReturnType();

		if (!returnType.equals(void.class) && invocation.returnValue == null && returnType.isPrimitive()) {
			if (returnType == boolean.class)
				return false;
			if (returnType == byte.class)
				return (byte) 0;
			else if (returnType == char.class)
				return (char) 0;
			else if (returnType == short.class)
				return (short) 0;
			else if (returnType == int.class)
				return (int) 0;
			else if (returnType == long.class)
				return (long) 0;
			else if (returnType == float.class)
				return (float) 0;
			else if (returnType == double.class)
				return (double) 0;
		}

		return invocation.returnValue;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + id;
		result = prime * result + ((objectId == null) ? 0 : objectId.hashCode());
		result = prime * result + ((peer == null) ? 0 : peer.hashCode());
		return result;
	}
	
	

}