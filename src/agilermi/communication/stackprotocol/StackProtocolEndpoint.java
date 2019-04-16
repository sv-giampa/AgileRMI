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
