package agilermi.example.lease;

import agilermi.configuration.Remote;
import agilermi.exception.RemoteException;

public interface Server extends Remote {
	Test getTest() throws RemoteException;
}
