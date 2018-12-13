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

package agilermi.handle;

import java.util.concurrent.Semaphore;

/**
 * Used to request the remote interfaces exposed by a remote object.
 * 
 * @author Salvatore Giampa'
 *
 */
public class RemoteInterfaceHandle implements Handle {

	private static final long serialVersionUID = -4774302373023169775L;

	private static long nextId = 0;

	public final long handleId;

	public final String objectId;

	public Class<?>[] interfaces;

	public transient final Semaphore semaphore = new Semaphore(0);

	public RemoteInterfaceHandle(String objectId) {
		this.handleId = nextId++;
		this.objectId = objectId;
	}

	public RemoteInterfaceHandle(long handleId, String objectId) {
		this.handleId = handleId;
		this.objectId = objectId;
	}

}
