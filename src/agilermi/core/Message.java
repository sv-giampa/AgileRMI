/**
 *  Copyright 2017 Salvatore Giampà
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

import java.io.Serializable;
import java.net.URL;
import java.util.Set;
import java.util.concurrent.Semaphore;

/**
 * Represents a generic message sent by a {@link RemoteInvocationHandler} to its
 * associated local {@link RmiHandler} and also it represents a message that can
 * be sent by a {@link RmiHandler} to another {@link RmiHandler} through the
 * network, so it is {@link Serializable}.
 * 
 * @author Salvatore Giampa'
 *
 */
interface Message extends Serializable {
}

/**
 * Message used to update remote list of codebases
 * 
 * @author Salvatore Giampa'
 *
 */
class CodebasesMessage implements Message {
	private static final long serialVersionUID = -7195041483720248013L;

	public Set<URL> codebases;
}

/**
 * This class represents a request for a remote method invocation.
 * 
 * @author Salvatore Giampa'
 *
 */
class InvocationMessage implements Message {
	private static final long serialVersionUID = 992296041709440752L;

	private static long nextId = 0;

	// invocation identifier, re-sent back in the related invocation response,
	// represented by a ReturnHandle instance
	public long id;

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

	// wait condition for the RemoteInvocationHandler that requested this invocation
	public transient boolean returned = false;

	public transient Semaphore semaphone = new Semaphore(0);

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
}

/**
 * This class represents response messages to invocation requests. This class is
 * the counterpart of the {@link InvocationMessage} class
 * 
 * @author Salvatore Giampa'
 *
 */
class ReturnMessage implements Message {
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
class RemoteInterfaceMessage implements Message {

	private static final long serialVersionUID = -4774302373023169775L;

	private static long nextId = 0;

	public final long handleId;

	public final String objectId;

	public Class<?>[] interfaces;

	public transient final Semaphore semaphore = new Semaphore(0);

	public RemoteInterfaceMessage(String objectId) {
		this.handleId = nextId++;
		this.objectId = objectId;
	}

	public RemoteInterfaceMessage(long handleId, String objectId) {
		this.handleId = handleId;
		this.objectId = objectId;
	}

}

/**
 * This class represents a request to add a new remote reference to a skeleton.
 * This is sent over a {@link RmiHandler} when a new stub is constructed or
 * deserialized
 * 
 * @author Salvatore Giampa'
 *
 */
class NewReferenceMessage implements Message {
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
class FinalizeMessage implements Message {
	private static final long serialVersionUID = 6485937225497004801L;

	public String objectId;

	public FinalizeMessage(String objectId) {
		this.objectId = objectId;
	}

}