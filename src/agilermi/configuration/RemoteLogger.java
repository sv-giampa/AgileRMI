package agilermi.configuration;

import agilermi.exception.RemoteException;

public interface RemoteLogger extends Remote {

	static RemoteLogger getDefault() {
		return DefaultRemoteLogger.getInstance();
	}

	default void log(String value, Object... objects) throws RemoteException {
		StackTraceElement caller = Thread.currentThread().getStackTrace()[2];
		System.out.printf("%-100s %s\r\n", "[" + caller + "] ", String.format(value, objects));
	}
}

class DefaultRemoteLogger implements RemoteLogger {

	private static RemoteLogger instance = new DefaultRemoteLogger();

	public static final RemoteLogger getInstance() {
		return instance;
	}
}
