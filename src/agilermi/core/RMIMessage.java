/**
 *  Copyright 2018-2019 Salvatore Giampà
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  
 **/

package agilermi.core;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.net.URL;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Semaphore;

/**
 * Represents a generic message sent by a {@link RemoteInvocationHandler} to its
 * associated local {@link RMIHandler} and also it represents a message that can
 * be sent by a {@link RMIHandler} to another {@link RMIHandler} through the
 * network, so it is {@link Serializable}.
 * 
 * @author Salvatore Giampa'
 *
 */
interface RMIMessage extends Serializable {
}

abstract class SynchronousRMIMessage implements RMIMessage {
	private static final long serialVersionUID = 1326481035766265225L;
	private static long nextId;
	private long id;
	private Object lock = new Object();
	private boolean signaled = false;

	SynchronousRMIMessage() {
		id = nextId++;
	}

	long getId() {
		return id;
	}

	void signalResult(SynchronousRMIMessage message) {
		copyResult(message);
		synchronized (lock) {
			lock.notifyAll();
		}
	}

	void awaitResult() throws InterruptedException {
		synchronized (lock) {
			while (!signaled)
				lock.wait();
		}
	}

	abstract boolean isResponse();

	abstract void copyResult(SynchronousRMIMessage resultMessage);
}

class InterruptionMessage implements RMIMessage {
	private static final long serialVersionUID = 4445481195634515157L;
	public final long invocationId;

	public InterruptionMessage(long invocationId) {
		this.invocationId = invocationId;
	}
}

/**
 * Message used to update remote list of codebases
 * 
 * @author Salvatore Giampa'
 *
 */
class CodebaseUpdateMessage implements RMIMessage {
	private static final long serialVersionUID = -7195041483720248013L;
	public Set<URL> codebases;

	private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
		input.defaultReadObject();
		if (input instanceof RMIObjectInputStream) {
			RMIObjectInputStream rmiInput = (RMIObjectInputStream) input;
			String remoteAddress = rmiInput.getRemoteAddress();
			Set<URL> replaced = new HashSet<>();
			Iterator<URL> it = codebases.iterator();
			while (it.hasNext()) {
				URL url = it.next();
				String strUrl = url.toString();
				strUrl = strUrl.replaceAll("//localhost", "//" + remoteAddress);
				strUrl = strUrl.replaceAll("//127\\.0\\.0\\.1", "//" + remoteAddress);
				it.remove();
				replaced.add(new URL(strUrl));
			}
			codebases.addAll(replaced);
		}
	}
}

/**
 * This class represents a request for a remote method invocation.
 * 
 * @author Salvatore Giampa'
 *
 */
class InvocationMessage implements RMIMessage {
	private static final long serialVersionUID = 992296041709440752L;

	private static long nextId = 0;

	// invocation identifier, re-sent back in the related invocation response,
	// represented by a ReturnHandle instance
	public final long id;

	// identifier of the remote object
	public String objectId;

	// remote method name
	public String method;

	// actual method parameters
	public Object[] parameters;

	// remote method parameter types
	public Class<?>[] parameterTypes;

	// return values are received and never sent in an invocaion handle. They are
	// sent through a ReturnHandle
	public transient Object returnValue;
	public transient Class<?> returnClass;
	public transient Throwable thrownException;

	/**
	 * Builds an invocation handle with the given invocation identifier
	 * 
	 * @param id             invocation identifier
	 * @param objectId       object identifier
	 * @param method         method name
	 * @param parameterTypes parameter types of the remote method
	 * @param parameters     actual parameters of the invocation
	 */
	public InvocationMessage(long id, String objectId, String method, Class<?>[] parameterTypes, Object[] parameters) {
		this.id = id;
		this.objectId = objectId;
		this.method = method;
		this.parameterTypes = parameterTypes;
		this.parameters = parameters;
	}

	/**
	 * Builds an invocation handle with a new generated invocation identifier
	 * 
	 * @param objectId       object identifier
	 * @param method         method name
	 * @param parameterTypes parameter types of the remote method
	 * @param parameters     actual parameters of the invocation
	 */
	public InvocationMessage(String objectId, String method, Class<?>[] parameterTypes, Object[] parameters) {
		id = nextId++;
		this.objectId = objectId;
		this.method = method;
		this.parameterTypes = parameterTypes;
		this.parameters = parameters;
	}

	private transient Semaphore semaphore = new Semaphore(0);

	/**
	 * Waits the result of the remote invocation. After the execution of this
	 * blocking operation, the {@link #returnValue} and the {@link #returnClass}
	 * fields will contain the returned object and its class, respectively.
	 * 
	 * @throws InterruptedException
	 */
	public void awaitResult() throws InterruptedException {
		if (semaphore != null)
			semaphore.acquire();
	}

	/**
	 * Signals the reception of the invocation result. This method should be called
	 * after the set up of the {@link #returnValue} and the {@link #returnClass}
	 * fields.
	 */
	public void signalResult() {
		if (semaphore != null)
			semaphore.release();
	}
}

/**
 * This class represents response messages to invocation requests. This class is
 * the counterpart of the {@link InvocationMessage} class
 * 
 * @author Salvatore Giampa'
 *
 */
class ReturnMessage implements RMIMessage {
	private static final long serialVersionUID = 6674503222830749941L;
	public long invocationId;
	public Class<?> returnClass;
	public Object returnValue;
	public Throwable thrownException;

	public ReturnMessage() {
	}

	public ReturnMessage(long invocationId, Class<?> returnClass, Object returnValue, Throwable thrownException) {
		this.invocationId = invocationId;
		this.returnClass = returnClass;
		this.returnValue = returnValue;
		this.thrownException = thrownException;
	}

}

/**
 * Used to request the remote interfaces exposed by a remote object.
 * 
 * @author Salvatore Giampa'
 *
 */
class RemoteInterfaceMessage implements RMIMessage {

	private static final long serialVersionUID = -4774302373023169775L;

	private static long nextId = 0;

	public final long handleId;

	public final String objectId;

	public Class<?>[] interfaces;

	public RemoteInterfaceMessage(String objectId) {
		this.handleId = nextId++;
		this.objectId = objectId;
	}

	public RemoteInterfaceMessage(long handleId, String objectId) {
		this.handleId = handleId;
		this.objectId = objectId;
	}

	private transient Semaphore semaphore = new Semaphore(0);

	/**
	 * Await for result
	 */
	public void awaitResult() {
		if (semaphore != null)
			try {
				semaphore.acquire();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
	}

	/**
	 * Signal the reception of the result
	 */
	public void signalResult() {
		if (semaphore != null)
			semaphore.release();
	}

}

/**
 * This class represents a request to add a new remote reference to a skeleton.
 * This is sent over a {@link RMIHandler} when a new stub is constructed or
 * deserialized
 * 
 * @author Salvatore Giampa'
 *
 */
class NewReferenceMessage implements RMIMessage {
	private static final long serialVersionUID = 8561515474575531127L;
	public String objectId;

	public NewReferenceMessage(String objectId) {
		this.objectId = objectId;
	}

}

/**
 * This class represents finalization messages sent by
 * {@link RemoteInvocationHandler} instances to their skeleton, to act the
 * distributed garbage collection mechanism
 * 
 * @author Salvatore Giampa'
 *
 */
class FinalizeMessage implements RMIMessage {
	private static final long serialVersionUID = 6485937225497004801L;

	public String objectId;

	public FinalizeMessage(String objectId) {
		this.objectId = objectId;
	}

}