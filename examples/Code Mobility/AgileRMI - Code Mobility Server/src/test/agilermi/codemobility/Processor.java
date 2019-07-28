package test.agilermi.codemobility;

import agilermi.configuration.Remote;

public interface Processor extends Remote {
	Task process(Task task);
}
