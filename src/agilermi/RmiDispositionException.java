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

/**
 * Thrown when an {@link RmiHandler} instance has been disposed before or during
 * the invocation. This can be sent to the attached failure observers when the
 * {@link RmiHandler#dispose()} method has been called
 * 
 * @author Salvatore Giampa'
 *
 */
public class RmiDispositionException extends RuntimeException {
	private static final long serialVersionUID = 3064594603835597427L;

	public RmiDispositionException() {
		super("The ObjectPeer has been disposed");
	}
}
