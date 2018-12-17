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
package agilermi.communication;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Defines an end-point for a custom protocol. An end-point for a protocol is a
 * data structure or an active object that performs the horizontal mediation
 * between the input and the output stream, and the vertical mediation between
 * the client that uses it and the underlying protocols. The concept represented
 * by this class is very similar to or it is a more general abstraction of the
 * concept represented by a TCP socket, that is an end-point for the TCP
 * protocol.
 * 
 * @author Salvatore Giampa'
 *
 */
public interface ProtocolEndpoint {

	/**
	 * Gets the output stream of this end-point
	 * 
	 * @return the output stream
	 */
	OutputStream getOutputStream();

	/**
	 * Gets the input stream of this end-point
	 * 
	 * @return the input stream
	 */
	InputStream getInputStream();
}
