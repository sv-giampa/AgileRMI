package agilermi.fullrtconf.rmi;

import java.io.IOException;
import java.io.Serializable;

import agilermi.codemobility.BasicCodeServer;
import agilermi.fullrtconf.app.AppService;
import agilermi.fullrtconf.app.AppServiceGetter;

public class RMIAppExample {
	public static void main(String[] args) throws IOException {
		AppServiceGetter appGetter = new RMIAppServiceGetter("localhost", 1099);
		AppService appService = appGetter.getAppService();

		BasicCodeServer.create().listen(8081, true);

		MyClass obj = new MyClass(5, 9);

		System.out.println("Printing on server...");
		appService.print(obj);
		System.out.println("Server has printed.");
	}

	public static class MyClass implements Serializable {
		private static final long serialVersionUID = 5625757788878234043L;
		int a;
		int b;

		public MyClass(int a, int b) {
			super();
			this.a = a;
			this.b = b;
		}

		@Override
		public String toString() {
			return "[MyClass: a=" + a + "; b=" + b + ";]";
		}
	}
}
