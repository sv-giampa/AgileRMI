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
package agilermi.utility.bytestream;

import java.io.IOException;
import java.io.InputStream;

public class RemoteInputToStream extends InputStream {
	private RemoteInput remoteInput;

	public static InputStream convert(RemoteInput remoteInput) {
		return new RemoteInputToStream(remoteInput);
	}

	private RemoteInputToStream(RemoteInput remoteInput) {
		if (remoteInput == null)
			throw new NullPointerException("remoteInput is null");
		this.remoteInput = remoteInput;
	}

	@Override
	public int read() throws IOException {
		return remoteInput.read();
	}

	@Override
	public int read(byte[] b) throws IOException {
		byte[] buffer = remoteInput.read(b.length);
		System.arraycopy(buffer, 0, b, 0, buffer.length);
		return b.length;
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		byte[] buffer = remoteInput.read(len);
		System.arraycopy(buffer, 0, b, off, len);
		return b.length;
	}

	@Override
	public void close() throws IOException {
		remoteInput.close();
	}

	@Override
	public int available() throws IOException {
		return remoteInput.available();
	}
}
