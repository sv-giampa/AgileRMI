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

package agilermi.configuration.annotation;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Setup a remote method to cache its return value for the specified amount of
 * time. If a method is cached for a given timeout, then each call to the remote
 * method will be sent to the remote machine if and only if that timeout expired
 * since the last call, else it returns the last result in the cache, without
 * requesting the remote invocation. This semantics can be useful when the
 * method returns an estimate of a very volatile property or when the method
 * represents an operation whose result can be supposed to be valid for the
 * given timeout.<br>
 * <br>
 * This annotation has effect on methods that accept no parameters only.<br>
 * 
 * @author Salvatore Giampa'
 *
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface RMICached {
	/**
	 * Specifies the amount of time after that the cached result become invalid.
	 * After this timeout, a future call will be shipped to the remote machine.
	 * 
	 * @return a time value in milliseconds
	 */
	int timeout() default 1000;
}
