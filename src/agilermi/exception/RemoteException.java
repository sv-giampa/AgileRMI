/**
 *  Copyright 2018-2019 Salvatore Giamp�
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

import agilermi.core.RMIHandler;

/**
 * This exception is thrown when a {@link RMIHandler} instance has been disposed
 * before or during an invocation. This exception is received by the attached
 * failure observers when the {@link RMIHandler#dispose(boolean)} method has
 * been called. Subclasses of this exception represents more specific errors.
 * 
 * @author Salvatore Giampa'
 *
 */
public class RemoteException extends Exception {
	private static final long serialVersionUID = 3064594603835597427L;

	/**
	 * Construct a new {@link RemoteException}
	 */
	public RemoteException() {}

	/**
	 * Constructs a new {@link RemoteException} with the specified detail
	 * message.The cause is not initialized, and may subsequently be initialized by
	 * a call to Throwable.initCause(java.lang.Throwable).
	 * 
	 * @param message - the detail message. The detail message is saved forlater
	 *                retrieval by the Throwable.getMessage() method.
	 */
	public RemoteException(String message) { super(message); }

	/**
	 * Constructs a new {@link RemoteException} with the specified cause and a
	 * detail message of (cause==null ? null : cause.toString())(which typically
	 * contains the class and detail message of cause). This constructor is useful
	 * for runtime exceptions that are little more than wrappers for other
	 * throwables.
	 * 
	 * @param cause - the cause (which is saved for later retrieval by the
	 *              Throwable.getCause() method). (A null value is permitted, and
	 *              indicates that the cause is nonexistent or unknown.)
	 * 
	 */
	public RemoteException(Throwable cause) { super(cause); }

	/**
	 * Constructs a new {@link RemoteException} with the specified detail message
	 * and cause. Note that the detail message associated with cause is not
	 * automatically incorporated in this runtime exception's detail message.
	 * 
	 * @param message - the detail message. The detail message is saved for later
	 *                retrieval by the Throwable.getMessage() method.
	 * @param cause   - the cause (which is saved for later retrieval by the
	 *                Throwable.getCause() method). (A null value is permitted, and
	 *                indicates that the cause is nonexistent or unknown.)
	 */
	public RemoteException(String message, Throwable cause) { super(message, cause); }
}
