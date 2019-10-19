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

package agilermi.exception;

/**
 * Exception thrown when the invoker is not authorized to invoke a remote
 * method.
 * 
 * @author Salvatore Giampa'
 *
 */
public class AuthorizationException extends RemoteException {

	private static final long serialVersionUID = 8881356199230680956L;

	/**
	 * Constructs a new {@link AuthorizationException}
	 */
	public AuthorizationException() {
		super("the requested invocation has not been authorized for the currently authenticated user");
	}

}
