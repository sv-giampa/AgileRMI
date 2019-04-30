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

public class StackProtocolEndpoint implements ProtocolEndpoint {
	private LinkedList<ProtocolEndpoint> endpoints = new LinkedList<>();

	private OutputStream output;
	private InputStream input;

	public StackProtocolEndpoint(LinkedList<ProtocolEndpointFactory> factories, OutputStream outputStream,
			InputStream inputStream) {
		ProtocolEndpoint endpoint = null;
		output = outputStream;
		input = inputStream;
		for (ProtocolEndpointFactory factory : factories) {
			endpoint = factory.createEndpoint(output, input);
			endpoints.add(endpoint);
			output = endpoint.getOutputStream();
			input = endpoint.getInputStream();
		}
	}

	@Override
	public OutputStream getOutputStream() {
		return output;
	}

	@Override
	public InputStream getInputStream() {
		return input;
	}

	@Override
	public void connectionEnd() {
		for (ProtocolEndpoint endpoint : endpoints)
			endpoint.connectionEnd();
	}

}
