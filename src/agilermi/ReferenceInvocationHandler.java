/**
 *  Copyright 2017 Salvatore Giamp�
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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import javax.naming.OperationNotSupportedException;

/**
 * Defines the invocation handler for the object stubs
 * 
 * @author Salvatore Giampa'
 *
 */
class ReferenceInvocationHandler implements InvocationHandler, Serializable {
	private static final long serialVersionUID = -3573558299307908872L;

	private String objectId;

	public ReferenceInvocationHandler(String objectId) {
		this.objectId = objectId;
	}

	private void writeObject(ObjectOutputStream out) throws IOException {
		out.writeUTF(objectId);
	}

	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		objectId = in.readUTF();
	}

	public String getObjectId() {
		return objectId;
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		return new OperationNotSupportedException();
	}

}