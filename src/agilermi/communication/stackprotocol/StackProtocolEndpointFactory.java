package agilermi.communication.stackprotocol;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;

import agilermi.communication.ProtocolEndpoint;
import agilermi.communication.ProtocolEndpointFactory;

public class StackProtocolEndpointFactory implements ProtocolEndpointFactory {

	private LinkedList<ProtocolEndpointFactory> factories = new LinkedList<>();

	public void addProtocolEndpointFactory(ProtocolEndpointFactory protocolEndpointFactory) {
		factories.add(protocolEndpointFactory);
	}

	@Override
	public ProtocolEndpoint createEndpoint(OutputStream outputStream, InputStream inputStream) {
		return new StackProtocolEndpoint(factories, outputStream, inputStream);
	}

}
