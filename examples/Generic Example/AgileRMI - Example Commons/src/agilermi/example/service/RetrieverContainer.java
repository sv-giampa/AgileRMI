package agilermi.example.service;

import java.io.Serializable;

import agilermi.configuration.StubRetriever;

public class RetrieverContainer implements Serializable {
	private static final long serialVersionUID = -3119629233163882925L;

	private Service service = null;

	// will be replaced by the local StubRetriever during deserialization
	private StubRetriever stubRetriever;

	public Service getService() {
		return service;
	}

	public void setService(Service service) {
		this.service = service;
	}

	public StubRetriever getStubRetriever() {
		return stubRetriever;
	}

}
