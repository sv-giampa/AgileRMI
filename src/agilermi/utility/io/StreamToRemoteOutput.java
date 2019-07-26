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
package agilermi.utility.io;

import java.io.IOException;
import java.io.OutputStream;

class StreamToRemoteOutput implements RemoteOutput {

	private OutputStream outputStream;

	public StreamToRemoteOutput(OutputStream outputStream) {
		if (outputStream == null)
			throw new NullPointerException("outputStream is null");
		this.outputStream = outputStream;
	}

	@Override
	public void close() throws IOException {
		outputStream.close();
	}

	@Override
	public void flush() throws IOException {
		outputStream.flush();
	}

	@Override
	public void write(int b) throws IOException {
		outputStream.write(b);
	}

	@Override
	public void write(byte[] bytes) throws IOException {
		outputStream.write(bytes);
	}

	@Override
	protected void finalize() throws Throwable {
		outputStream.close();
		super.finalize();
	}

}
