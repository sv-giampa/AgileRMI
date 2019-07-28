package agilermi.faulttolerance;

import agilermi.configuration.Remote;
import agilermi.exception.RemoteException;

public interface Server extends Remote {
	double nextNumber() throws RemoteException;
}
