package agilermi.faulttolerance;

import agilermi.exception.RemoteException;

public class ServerImpl implements Server {
	private double x;

	@Override
	public double nextNumber() throws RemoteException {
		return x++;
	}

}
