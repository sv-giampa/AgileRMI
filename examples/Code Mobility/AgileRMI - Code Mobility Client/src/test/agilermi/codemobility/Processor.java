package test.agilermi.codemobility;

import agilermi.exception.RemoteException;

public interface Processor {
	Task process(Task task) throws RemoteException;
}
