package agilermi.example.server;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import agilermi.example.service.RetrieverContainerExt;
import agilermi.example.service.Service;
import agilermi.example.service.ServiceObserver;
import agilermi.exception.RemoteException;

public class ServiceImpl implements Service {

	// prevents parallel access to the collection, as you should do in the case this
	// object is not remote
	private Set<ServiceObserver> observers = Collections.synchronizedSet(new HashSet<>());

	@Override
	public int square(int x) throws RemoteException {
		return x * x;
	}

	@Override
	public double add(double x, double y) throws RemoteException {
		return x + y;
	}

	@Override
	public void printlnOnServer(String message) throws RemoteException {
		System.out.println("Message by client: '" + message + "'");
	}

	@Override
	public void startObserversCalls() throws RemoteException {
		new Thread(() -> {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			observers.forEach(o -> {
				try {
					o.update(this);
				} catch (RemoteException e) {}
			});
		}).start();
	}

	@Override
	public void attachObserver(ServiceObserver o) throws RemoteException {
		observers.add(o);
	}

	@Override
	public void detachObserver(ServiceObserver o) throws RemoteException {
		observers.remove(o);
	}

	@Override
	public Service getThis() throws RemoteException {
		return this;
	}

	@Override
	public boolean equals(Object obj) {
		System.out.println("equals called!");
		return super.equals(obj);
	}

	@Override
	public void infiniteCycle() throws RemoteException, InterruptedException {
		while (true) { Thread.sleep(1000); }
	}

	@Override
	public RetrieverContainerExt compute(RetrieverContainerExt classB) throws RemoteException {
		System.out.println("stub getter: " + classB.getStubRetriever());
		classB.setService(this);
		return classB;
	}

	@Override
	public void anotherRemoteException() throws IllegalStateException, RemoteException {
		throw new RemoteException();
	}

	@Override
	public List<Service> listOfRemoteObjects() throws RemoteException {
		List<Service> list = new LinkedList<Service>();
		list.add(this);
		list.add(this);
		return list;
	}

}
