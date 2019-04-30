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
package agilermi.communication.gzip;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

/**
 * This class decodes the data streams encoded with
 * {@link ConitnuousGzipOutputStream}. This class needs the input stream to
 * decode only.
 * 
 * @author Salvatore Giampa'
 *
 */
public final class ContinuousGzipInputStream extends InputStream {

	private DataInputStream input;
	private byte[] buffer;
	private int bufferIndex = 0;

	/**
	 * Builds a decoder for streams encoded with {@link ConitnuousGzipOutputStream}.
	 * 
	 * @param input the input stream to decode
	 */
	public ContinuousGzipInputStream(InputStream input) {
		this.input = new DataInputStream(input);
	}

	/**
	 * Uncompresses the next chunk of data and stores it in the buffer
	 * 
	 * @throws IOException
	 */
	private void unzip() throws IOException {
		int compressedLen = input.readInt();
		byte[] compressedData = new byte[compressedLen];
		input.readFully(compressedData);

		ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
		DataInputStream gzip = new DataInputStream(new GZIPInputStream(bais));
		int bufferSize = gzip.readInt();
		bufferIndex = 0;
		buffer = new byte[bufferSize];
		gzip.readFully(buffer);
		gzip.close();
	}

	@Override
	public int read() throws IOException {
		if (buffer == null || bufferIndex >= buffer.length)
			unzip();
		return buffer[bufferIndex++];
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {

		for (int bIndex = 0; bIndex < len;) {
			if (buffer == null || bufferIndex >= buffer.length)
				unzip();
			int bytesToRead = Math.min(len - bIndex, buffer.length - bufferIndex);
			System.arraycopy(buffer, bufferIndex, b, off + bIndex, bytesToRead);

			bufferIndex += bytesToRead;
			bIndex += bytesToRead;
		}
		return len;
	}

	@Override
	public void close() throws IOException {
		input.close();
		super.close();
	}

	@Override
	public int available() throws IOException {
		return (buffer.length - bufferIndex) + (input.available() > 0 ? 1 : 0);
	}

}
