package agilermi.example.service;

import agilermi.configuration.Remote;
import agilermi.configuration.annotation.RMISuppressFaults;

public interface ServiceObserver extends Remote {
	@RMISuppressFaults
	void update(Service service);
}
