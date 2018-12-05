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

package agilermi;

/**
 * This class represents a request for a remote method invocation.
 * 
 * @author Salvatore Giampa'
 *
 */
class InvocationHandle implements Handle {
	private static final long serialVersionUID = 992296041709440752L;

	private static long nextInvocationId = 0;

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

	/**
	 * Builds an invocation handle with the given invocation identifier
	 * 
	 * @param id             invocation identifier
	 * @param objectId       object identifier
	 * @param method         method name
	 * @param parameterTypes parameter types of the remote method
	 * @param parameters     actual parameters of the invocation
	 */
	public InvocationHandle(long id, String objectId, String method, Class<?>[] parameterTypes, Object[] parameters) {
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
	public InvocationHandle(String objectId, String method, Class<?>[] parameterTypes, Object[] parameters) {
		id = nextInvocationId++;
		this.objectId = objectId;
		this.method = method;
		this.parameterTypes = parameterTypes;
		this.parameters = parameters;
	}
}
