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

import java.io.Serializable;

/**
 * Represents a generic message sent by a {@link RemoteInvocationHandler} to its
 * associated local {@link RmiHandler} and also it represents a message that can
 * be sent by a {@link RmiHandler} to another {@link RmiHandler} through the
 * network, so it is {@link Serializable}.
 * 
 * @author Salvatore Giampa'
 *
 */
interface Handle extends Serializable {
}
