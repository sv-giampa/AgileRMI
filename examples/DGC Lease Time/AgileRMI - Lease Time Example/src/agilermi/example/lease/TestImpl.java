package agilermi.example.lease;

import agilermi.exception.RemoteException;

public class TestImpl implements Test {
	private static int nextId = 0;

	private int id = ++nextId;

	@Override
	public void test() throws RemoteException {
		System.out.println("test #" + id);
	}

}
