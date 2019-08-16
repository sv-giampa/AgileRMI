package agilermi.example.service;

import agilermi.configuration.Remote;
import agilermi.exception.RemoteException;

public interface ServiceObserver extends Remote {
	void update(Service service) throws RemoteException;
}
