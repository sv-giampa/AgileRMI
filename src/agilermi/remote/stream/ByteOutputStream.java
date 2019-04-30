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
package agilermi.remote.stream;

import java.io.IOException;
import java.io.OutputStream;

public class ByteOutputStream extends OutputStream {
	private ByteOutput byteOutput;

	public ByteOutputStream(ByteOutput byteOutput) {
		if (byteOutput == null)
			throw new NullPointerException("byteOutput is null");
		this.byteOutput = byteOutput;
	}

	@Override
	public void write(int b) throws IOException {
		byteOutput.write(b);
	}

	@Override
	public void write(byte[] b) throws IOException {
		byteOutput.write(b);
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		byte[] buffer = new byte[len];
		System.arraycopy(b, off, buffer, 0, len);
		byteOutput.write(buffer);
	}

	@Override
	public void flush() throws IOException {
		byteOutput.flush();
	}

	@Override
	public void close() throws IOException {
		byteOutput.close();
	}

}
