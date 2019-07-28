package agilermi.example.lease;

import agilermi.configuration.Remote;
import agilermi.exception.RemoteException;

public interface Test extends Remote {

	void test() throws RemoteException;
}
