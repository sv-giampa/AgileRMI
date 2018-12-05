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

/**
 * This class represents a request to add a new remote reference to a skeleton.
 * This is sent over a {@link RmiHandler} when a new stub is constructed or
 * deserialized
 * 
 * @author Salvatore Giampa'
 *
 */
class NewReferenceHandle implements Handle {
	private static final long serialVersionUID = 8561515474575531127L;
	public String objectId;

	public NewReferenceHandle(String objectId) {
		this.objectId = objectId;
	}

}
