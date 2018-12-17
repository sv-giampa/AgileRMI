package agilermi.example.service;

import agilermi.configuration.Remote;

public interface ServiceObserver extends Remote {
	void update(Service service);
}
