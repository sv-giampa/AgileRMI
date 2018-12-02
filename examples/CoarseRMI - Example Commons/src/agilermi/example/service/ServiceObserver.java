package agilermi.example.service;

import agilermi.Remote;

public interface ServiceObserver extends Remote {
	void update(Service service);
}
