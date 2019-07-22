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

class Logger implements RMILogger {

	private String name;
	private PrintStream printStream;

	/**
	 * Creates a {@link Logger} using the specified name and the given
	 * {@link PrintStream} on which the logger must print.
	 * 
	 * @param name        the name of this logger that will be printed aside each
	 *                    log string
	 * @param printStream the {@link PrintStream} to log to
	 */
	public Logger(String name, PrintStream printStream) {
		this.name = name;
		this.printStream = printStream;
	}

	@Override
	public void log(String format, Object... objects) {
		printStream.printf("%-100s %s\r\n", "[" + name + "] ", String.format(format, objects));
	}

	@Override
	public void logIf(boolean condition, String format, Object... objects) {
		if (condition)
			log(format, objects);

	}
}
