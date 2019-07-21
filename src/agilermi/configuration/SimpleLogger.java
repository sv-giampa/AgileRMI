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

package agilermi.configuration;

import java.io.PrintStream;

public class SimpleLogger {

	private String name;
	private PrintStream printStream;

	public SimpleLogger(String name, PrintStream printStream) {
		this.name = name;
		this.printStream = printStream;
	}

	public SimpleLogger(Class<?> cls, PrintStream printStream) {
		this.name = cls.getName();
		this.printStream = printStream;
	}

	public SimpleLogger(String name) {
		this(name, System.out);
	}

	public SimpleLogger(Class<?> cls) {
		this(cls, System.out);
	}

	public void log(String value, Object... objects) {
		printStream.printf("%-100s %s\r\n", "[" + name + "] ", String.format(value, objects));
	}

	public void logIf(boolean condition, String value, Object... objects) {
		if (condition)
			log(value, objects);
	}
}
