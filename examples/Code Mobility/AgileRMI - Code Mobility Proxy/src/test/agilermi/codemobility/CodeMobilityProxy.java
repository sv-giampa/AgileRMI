package test.agilermi.codemobility;

import java.io.IOException;
import java.net.UnknownHostException;

import agilermi.core.RMIRegistry;

public class CodeMobilityProxy implements Processor {
	private Processor server;

	public CodeMobilityProxy(Processor server) {
		this.server = server;
	}

	@Override
	public Task process(Task task) {
		System.out.println("Proxying a task...");
		task = server.process(task);
		System.out.println("Task returned!");
		return task;
	}

	public static void main(String[] args) throws UnknownHostException, IOException, InterruptedException {

		RMIRegistry registry = RMIRegistry.builder().setCodeDownloadingEnabled(true).build();
		// registry.exportInterface(Processor.class);
		Processor server = (Processor) registry.getStub("localhost", 5060, "processor");

		Processor processor = new CodeMobilityProxy(server);
		registry.publish("processor", processor);
		registry.enableListener(5061, false);

		System.out.println("Agile RMI - code mobility example proxy started.");
	}

}
