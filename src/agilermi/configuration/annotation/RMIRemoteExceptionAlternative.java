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

package agilermi.configuration.annotation;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import agilermi.exception.RemoteException;

/**
 * When a remote interface method is annotated with this annotation, it will
 * replace any thrown {@link RemoteException} with the {@link #value()} of this
 * interface.
 * 
 * @author Salvatore Giampa'
 *
 */
@Documented
@Retention(RUNTIME)
@Target(METHOD)
public @interface RMIRemoteExceptionAlternative {
	/**
	 * The exception class that must replace any {@link RemoteException} thrown on
	 * the stub side.
	 * 
	 * @return the class descriptor of the exception
	 */
	Class<? extends Exception> value() default IllegalStateException.class;

}
