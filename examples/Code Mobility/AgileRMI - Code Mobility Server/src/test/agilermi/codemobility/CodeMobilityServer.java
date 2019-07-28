package test.agilermi.codemobility;

import java.io.IOException;

import agilermi.core.RMIRegistry;

public class CodeMobilityServer implements Processor {

	private RMIRegistry registry;

	public CodeMobilityServer(RMIRegistry registry) {
		this.registry = registry;
	}

	@Override
	public Task process(Task task) {
		System.out.println("new task received!");
		System.out.println("current codebases: " + registry.getCodebases());
		task.compute();
		return task;
	}

	public static void main(String[] args) throws IOException {

		RMIRegistry registry = RMIRegistry.builder().setCodeDownloadingEnabled(true).build();
		// registry.exportInterface(Processor.class);
		Processor processor = new CodeMobilityServer(registry);
		registry.publish("processor", processor);
		registry.enableListener(5060, false);

		System.out.println("Agile RMI - code mobility example server started.");
	}

}
