package agilermi.fullrtconf.rmi;

import agilermi.core.RMIRegistry;
import agilermi.fullrtconf.app.AppService;
import agilermi.fullrtconf.app.AppServiceGetter;

public class RMIAppServiceGetter implements AppServiceGetter {
	private RMIRegistry registry;
	private String host;
	private int port;

	public RMIAppServiceGetter(String host, int port) {
		this(host, port, RMIRegistry.builder().build());
	}

	public RMIAppServiceGetter(String host, int port, RMIRegistry registry) {
		this.host = host;
		this.port = port;
		this.registry = registry;
		setup();
	}

	private void setup() {
		registry.exportInterfaces(AppService.class);
	}

	@Override
	public AppService getAppService() {
		return (AppService) registry.getStub(host, port, "app_service", AppService.class);
	}

}
