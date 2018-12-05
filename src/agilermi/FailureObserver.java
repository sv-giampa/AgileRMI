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
 * Defines an observer used to collect the connection failures and errors. This
 * interface is used to separate the interests of the users of the stubs from
 * those of the objects that manage the remote communication
 * 
 * @author Salvatore Giampa'
 *
 */
public interface FailureObserver {
	/**
	 * Called when a non reliable exception was generated by the specified, although
	 * disposed, {@link RmiHandler} object.
	 * 
	 * @param rmiHandler the disposed {@link RmiHandler} object
	 * @param exception  the thrown exception
	 * @throws Throwable if the underlying implementation {@link FailureObserver}
	 *                   throws something
	 */
	void failure(RmiHandler rmiHandler, Exception exception) throws Throwable;
}
