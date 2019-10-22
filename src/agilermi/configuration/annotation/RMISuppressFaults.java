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

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import agilermi.exception.RemoteException;

/**
 * This annotation can be applied to a remote method of a remote interface to
 * suppress RMI faults and exceptions. If a remote method is annotated with this
 * annotation, it should not be declared to throw {@link RemoteException}. Be
 * careful using this annotation, it changes the semantics of remote method and
 * it does not allow the application to notice any stub fault, such as the
 * expiration of a remote pointer. <br>
 * <br>
 * Each method which encounter an RMI fault and suppress it will return the
 * default value for its return type. The default values are <code>0</code> for
 * all numerical primitive (such as int) and non-primitive (such as
 * {@link Integer}) types, <code>false</code> for the boolean type and
 * <code>null</code> for object types.
 * 
 * @author Salvatore Giampa'
 *
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface RMISuppressFaults {

}
