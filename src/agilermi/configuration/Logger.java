package agilermi.configuration;

public interface Logger {

	static Logger getDefault() {
		return DefaultLogger.getInstance();
	}

	default void log(String value, Object... objects) {
		StackTraceElement caller = Thread.currentThread().getStackTrace()[2];
		System.out.printf("%-100s %s\r\n", "[" + caller + "] ", String.format(value, objects));
	}
}

class DefaultLogger implements Logger {
	private static DefaultLogger instance = new DefaultLogger();

	public static Logger getInstance() {
		return instance;
	}

	private DefaultLogger() {
	};
}
