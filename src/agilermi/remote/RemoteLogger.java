/**
 *  Copyright 2018-2019 Salvatore Giampà
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  
 **/

package agilermi.remote;

import agilermi.configuration.Remote;
import agilermi.exception.RemoteException;

public interface RemoteLogger extends Remote {

	static RemoteLogger getDefault() {
		return DefaultRemoteLogger.getInstance();
	}

	default void log(String tag, String value, Object... objects) throws RemoteException {
		tag = "[" + tag + "]";
		System.out.printf("%-100s %s\r\n", tag, String.format(value, objects));
	}
}

class DefaultRemoteLogger implements RemoteLogger {

	private static RemoteLogger instance = new DefaultRemoteLogger();

	public static final RemoteLogger getInstance() {
		return instance;
	}
}

//
//public class RemoteSimpleLogger {
//
//	private String name;
//	private PrintStream printStream;
//
//	public RemoteSimpleLogger(RemoteLogger logger) {
//		this(name, System.out);
//	}
//
//	private SimpleLogger(String name, PrintStream printStream) {
//		this.name = name;
//		this.printStream = printStream;
//	}
//
//	public void log(String value, Object... objects) {
//		printStream.printf("%-100s %s\r\n", "[" + name + "] ", String.format(value, objects));
//	}
//
//	public void logIf(boolean condition, String value, Object... objects) {
//		if (condition)
//			log(value, objects);
//	}
//}
