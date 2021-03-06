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

package agilermi.configuration;

import java.lang.reflect.UndeclaredThrowableException;

import agilermi.configuration.annotation.RMISuppressFaults;
import agilermi.exception.RemoteException;

/**
 * Marker interface for remote interfaces. This interface is used to explicitly
 * and statically mark the application interfaces whose instances must be all
 * remote objects. Each remote method should throw {@link RemoteException} or it
 * should be annotated with {@link RMISuppressFaults}. If not, the remote method
 * can throw {@link UndeclaredThrowableException} whose cause can be
 * {@link RemoteException}.
 * 
 * @author Salvatore Giampa'
 *
 */
public interface Remote {

}
