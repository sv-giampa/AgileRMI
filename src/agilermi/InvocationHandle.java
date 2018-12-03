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

class InvocationHandle implements Handle {
	private static final long serialVersionUID = 992296041709440752L;

	private static long nextInvocationId = 0;

	public long id;
	public String objectId;
	public String method;
	public Object[] parameters;
	public Class<?>[] parameterTypes;

	public transient Object returnValue;
	public transient Class<?> returnClass;
	public transient Throwable thrownException;
	public transient boolean returned = false;

//	public InvocationHandle() {
//		id = nextInvocationId++;
//	}

	public InvocationHandle(long id, String objectId, String method, Class<?>[] parameterTypes, Object[] parameters) {
		this.id = id;
		this.objectId = objectId;
		this.method = method;
		this.parameterTypes = parameterTypes;
		this.parameters = parameters;
	}

	public InvocationHandle(String objectId, String method, Class<?>[] parameterTypes, Object[] parameters) {
		id = nextInvocationId++;
		this.objectId = objectId;
		this.method = method;
		this.parameterTypes = parameterTypes;
		this.parameters = parameters;
	}
}
