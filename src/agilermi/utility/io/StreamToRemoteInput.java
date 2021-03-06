/**
 *  Copyright 2018-2019 Salvatore Giamp�
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

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

class StreamToRemoteInput implements RemoteInput {

	private DataInputStream inputStream;

	public static RemoteInput convert(InputStream inputStream) {
		return new StreamToRemoteInput(inputStream);
	}

	StreamToRemoteInput(InputStream inputStream) {
		if (inputStream == null)
			throw new NullPointerException("inputStream is null");
		this.inputStream = new DataInputStream(inputStream);
	}

	@Override
	public int read() throws IOException {
		return inputStream.read();
	}

	@Override
	public byte[] read(int len) throws IOException {
		byte[] buffer = new byte[len];
		inputStream.readFully(buffer);
		return buffer;
	}

	@Override
	public void close() throws IOException {
		inputStream.close();
	}

	@Override
	public int available() throws IOException {
		return inputStream.available();
	}

	@Override
	protected void finalize() throws Throwable {
		inputStream.close();
		super.finalize();
	}

}
