package agilermi.example.service;

import java.io.Serializable;

import agilermi.configuration.StubRetriever;

public class ClassA implements Serializable {
	private static final long serialVersionUID = -3119629233163882925L;

	private Service service = null;

	private StubRetriever stubGetter;

	public Service getService() {
		return service;
	}

	public void setService(Service service) {
		this.service = service;
	}

	public StubRetriever getStubGetter() {
		return stubGetter;
	}

}
