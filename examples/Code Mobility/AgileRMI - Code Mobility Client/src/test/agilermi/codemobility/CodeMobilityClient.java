package test.agilermi.codemobility;

import java.io.IOException;
import java.net.URL;
import java.net.UnknownHostException;

import agilermi.core.RMIRegistry;
import agilermi.exception.RemoteException;

public class CodeMobilityClient {
	public static void main(String[] args)
			throws UnknownHostException, IOException, InterruptedException, RemoteException {

		System.out.println("Agile RMI - code mobility example client started.");
		// BasicCodeServer codeServer =
		// BasicCodeServer.create().setCodeDirectory("bin").listen(80);

		URL codebase1 = new URL("http://localhost/");
		URL codebase2 = new URL("http://localhost/codebase/armi-codemobility2.jar");

		RMIRegistry registry = RMIRegistry.builder().addCodebase(codebase1).build();

		Processor processor = (Processor) registry.getStub("localhost", 5061, "processor");
		ClientTask task = new ClientTask(3);

		// task.compute();
		task = (ClientTask) processor.process(task);

		System.out.println("Task result: " + task.y);

		Thread.sleep(11000);

		// switch codebase
		registry.removeCodebase(codebase1);
		registry.addCodebase(codebase2);

		task = (ClientTask) processor.process(task);

		System.out.println("Task result: " + task.y);

	}
}
