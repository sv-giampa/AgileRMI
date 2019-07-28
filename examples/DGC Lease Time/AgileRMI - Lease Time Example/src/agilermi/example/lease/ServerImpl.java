package agilermi.example.lease;

import agilermi.exception.RemoteException;

public class ServerImpl implements Server {

	private Test test;

	public ServerImpl() {
		this(new TestImpl());
	}

	public ServerImpl(Test test) {
		this.test = test;
	}

	@Override
	public Test getTest() throws RemoteException {
		return test;
	}

}
