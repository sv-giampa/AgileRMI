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

import java.io.IOException;

/**
 * Exception thrown on the skeleton side, when the remote invoker cannot be
 * authenticated on the machine.
 * 
 * @author Salvatore Giampa'
 *
 */
public class LocalAuthenticationException extends IOException {

	private static final long serialVersionUID = 5828990096774080908L;

	/**
	 * Constructs a new {@link LocalAuthenticationException}.
	 */
	public LocalAuthenticationException() { super("the remote invoker cannot be authenticated on this machine"); }
}
