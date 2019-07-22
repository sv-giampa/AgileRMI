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

package agilermi.utility.logging;

import java.io.PrintStream;

import agilermi.annotation.RMIAsynch;
import agilermi.annotation.RMISuppressFaults;
import agilermi.configuration.Remote;
import agilermi.exception.RemoteException;

/**
 * This class defines a simple logger whose references can be sent over RMI
 * streams, when automatic referencing is enabled. A {@link Logger} instance
 * will remain on the machine that created it and its references can be sent to
 * other connected machines. If the original logger is not reachable a log
 * operation does not throw {@link RemoteException} and the logging operation
 * will have no effects.<br>
 * <br>
 * 
 * @author Salvatore Giampa'
 *
 */
public interface RMILogger extends Remote {

	/**
	 * Creates a {@link Logger} using the specified name and the given
	 * {@link PrintStream} on which the logger must print.
	 * 
	 * @param name        the name of this logger that will be printed aside each
	 *                    log string
	 * @param printStream the {@link PrintStream} to log to
	 * 
	 * @return a new instance of {@link RMILogger}
	 */
	public static RMILogger get(String name, PrintStream printStream) {
		return new Logger(name, printStream);
	}

	/**
	 * Creates a {@link Logger} using the name of the specified class and the given
	 * {@link PrintStream} on which the logger must print.
	 * 
	 * @param cls         the class whose name is used as logger name that will be
	 *                    printed aside each log string
	 * @param printStream the {@link PrintStream} to log to
	 * @return a new instance of {@link RMILogger}
	 */
	public static RMILogger get(Class<?> cls, PrintStream printStream) {
		return get(cls.getName(), printStream);
	}

	/**
	 * Creates a {@link Logger} using the specified name
	 * 
	 * @param name the name of this logger that will be printed aside each log
	 *             string
	 * @return a new instance of {@link RMILogger}
	 */
	public static RMILogger get(String name) {
		return get(name, System.out);
	}

	/**
	 * Creates a {@link Logger} using the name of the specified class
	 * 
	 * @param cls the class whose name is used as logger name that will be printed
	 *            aside each log string
	 * @return a new instance of {@link RMILogger}
	 */
	public static RMILogger get(Class<?> cls) {
		return get(cls, System.out);
	}

	/**
	 * Logs a string using the specified format and composed by the specified
	 * objects.
	 * 
	 * @param format  the format of the log string
	 * @param objects the objects that are printed in the formatted string
	 */
	@RMISuppressFaults
	@RMIAsynch
	public void log(String format, Object... objects);

	/**
	 * As {@link #log(String, Object...)} logs a string. The string is logged if and
	 * only if the specified condition is true.
	 * 
	 * @param condition set to true to log the string or set to false to have no
	 *                  effects
	 * @param format    the format of the log string
	 * @param objects   the objects that are printed in the formatted string
	 */
	@RMISuppressFaults
	@RMIAsynch
	public void logIf(boolean condition, String format, Object... objects);
}
