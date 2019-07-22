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
package agilermi.communication.stackprotocol;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;

import agilermi.communication.ProtocolEndpoint;
import agilermi.communication.ProtocolEndpointFactory;

/**
 * A factory for {@link StackProtocolEndpoint}
 * 
 * @author Salvatore Giampa'
 *
 */
public class StackProtocolEndpointFactory implements ProtocolEndpointFactory {

	private LinkedList<ProtocolEndpointFactory> factories = new LinkedList<>();

	/**
	 * Add a protocol to the stack
	 * 
	 * @param protocolEndpointFactory the {@link ProtocolEndpointFactory} of the
	 *                                protocol
	 */
	public void addProtocolEndpointFactory(ProtocolEndpointFactory protocolEndpointFactory) {
		factories.add(protocolEndpointFactory);
	}

	@Override
	public ProtocolEndpoint createEndpoint(OutputStream outputStream, InputStream inputStream) {
		return new StackProtocolEndpoint(factories, outputStream, inputStream);
	}

}
