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

import java.lang.reflect.Method;
import java.net.InetSocketAddress;

public interface Authenticator {

	/**
	 * Authenticate a user that established the connection from the given address.
	 * This method allows or disallows the {@link RmiRegistry} to accept the
	 * incoming connection
	 * 
	 * @param remoteAddress the address of the remote machine
	 * @param authId        the authentication identifier of the user
	 * @param passphrase    the passphrase to use for authentication
	 * @return true if the user has been authenticated successfully, false
	 *         otherwise.
	 */
	boolean authenticate(InetSocketAddress remoteAddress, String authId, String passphrase);

	/**
	 * Authorize a user to invoke a specific method on a specific object. This
	 * method allows to establish fine-grained authorization.
	 * 
	 * @param authId the user authentication identifier, the same that ha been used
	 *               to authenticate the user in the
	 *               {@link #authenticate(InetSocketAddress, String, String)} method
	 * @param object the object to which the user requires the access
	 * @param method the method that the user wants invoke
	 * @return true if the user is authorized, false otherwise
	 */
	boolean authorize(String authId, Object object, Method method);

}
