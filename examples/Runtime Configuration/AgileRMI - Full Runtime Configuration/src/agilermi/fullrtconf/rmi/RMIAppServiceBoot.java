package agilermi.fullrtconf.rmi;

import java.io.IOException;

import agilermi.core.RMIRegistry;
import agilermi.fullrtconf.app.StandardAppService;

public class RMIAppServiceBoot {
	public static void main(String[] args) throws IOException {
		RMIRegistry registry = RMIRegistry.builder().build();
		registry.setCodeDownloadingEnabled(true);
		registry.publish("app_service", new StandardAppService());
		registry.enableListener(1099, false);
	}
}
