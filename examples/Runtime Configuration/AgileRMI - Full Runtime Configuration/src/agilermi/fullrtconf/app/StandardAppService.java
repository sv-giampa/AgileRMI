package agilermi.fullrtconf.app;

public class StandardAppService implements AppService {

	@Override
	public <T> void print(T object) {
		System.out.println("Received object to print: " + object);
	}

}
