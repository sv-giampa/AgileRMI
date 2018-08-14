package coarsermi.example.server;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import coarsermi.example.service.Service;
import coarsermi.example.service.ServiceObserver;

public class ServiceImpl implements Service {
	
	// prevents parallel access to the collection, as you should do in the case this object is not remote
	private Set<ServiceObserver> observers = Collections.synchronizedSet(new HashSet<>());

	@Override
	public int square(int x) {
		return x*x;
	}

	@Override
	public double add(double x, double y) {
		return x+y;
	}

	@Override
	public void printlnOnServer(String message) {
		System.out.println("Message by client: '" + message + "'");
	}

	@Override
	public void startObserversCalls() {
		new Thread(()->{
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			observers.forEach(o->o.update(this));
		}).start();;
	}

	@Override
	public void attachObserver(ServiceObserver o) {
		observers.add(o);
	}

	@Override
	public void detachObserver(ServiceObserver o) {
		observers.remove(o);
	}

}