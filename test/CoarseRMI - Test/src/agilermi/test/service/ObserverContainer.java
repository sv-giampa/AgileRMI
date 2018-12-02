package agilermi.test.service;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

public class ObserverContainer implements Serializable {
	private static final long serialVersionUID = 2712720536250009508L;
	private TestObserver observer;
	private String testString = "ok";
	private List<String> list = Arrays.asList("test1", "test2", "test3");

	// requested by serialization
	public ObserverContainer() {
	}

	public ObserverContainer(TestObserver observer) {
		this.observer = observer;
	}

	public TestObserver getObserver() {
		return observer;
	}
}
