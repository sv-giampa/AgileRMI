package agilermi.example.service;

import agilermi.configuration.Remote;

public interface Service extends Remote {

	int square(int x);

	double add(double x, double y);

	void printlnOnServer(String message);

	void startObserversCalls();

	void attachObserver(ServiceObserver o);

	void detachObserver(ServiceObserver o);

	Service getThis();

}
