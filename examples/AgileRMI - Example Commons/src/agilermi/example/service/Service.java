package agilermi.example.service;

import agilermi.configuration.Remote;
import agilermi.exception.RemoteException;

public interface Service extends Remote {

	int square(int x) throws RemoteException;

	double add(double x, double y) throws RemoteException;

	void printlnOnServer(String message) throws RemoteException;

	void startObserversCalls() throws RemoteException;

	void attachObserver(ServiceObserver o) throws RemoteException;

	void detachObserver(ServiceObserver o) throws RemoteException;

	Service getThis() throws RemoteException;

	void infiniteCycle() throws RemoteException, InterruptedException;

	ClassB compute(ClassB classB);

}
