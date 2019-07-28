package test.agilermi.pointer.routing;

import agilermi.configuration.Remote;
import agilermi.configuration.annotation.RMIAsynch;

public interface Service extends Remote {
	Service getService();

	@RMIAsynch
	void setService(Service service);

	@RMIAsynch
	void useThisService();
}
