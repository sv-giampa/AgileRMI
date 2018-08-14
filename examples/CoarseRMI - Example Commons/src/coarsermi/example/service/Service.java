package coarsermi.example.service;


public interface Service {

	int square(int x);
	
	double add(double x, double y);
	
	void printlnOnServer(String message);
	
	void startObserversCalls();

	void attachObserver(ServiceObserver o);
	
	void detachObserver(ServiceObserver o);
	
}
