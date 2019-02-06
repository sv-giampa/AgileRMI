/**
 *  Copyright 2017 Salvatore Giampà
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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

/**
 * This class uses the {@link GZIPOutputStream} of the standard JDK to
 * implements a continuous buffered output stream for data compression. This
 * stream does not need you to invoke a finish() method as the
 * {@link GZIPOutputStream} do. This stream is usable just as any other stream
 * decorator with the basic methods {@link #write(int)}, {@link #flush()},
 * {@link #close()} and so on. To reach its goal, this class buffers the data
 * bytes and compresses them per blocks.
 * 
 * @author Salvatore Giampa'
 *
 */
public final class ConitnuousGzipOutputStream extends OutputStream {

	private DataOutputStream output;
	private byte[] buffer;
	private int bufferIndex = 0;

	/**
	 * Build a new compression stream with the specified buffer size. The buffer
	 * size is the max number of data bytes that can be buffered before the next
	 * block compression.
	 * 
	 * @param output
	 * @param bufferSize
	 */
	public ConitnuousGzipOutputStream(OutputStream output, int bufferSize) {
		this.output = new DataOutputStream(output);
		this.buffer = new byte[bufferSize];
	}

	public int getBufferSize() {
		return buffer.length;
	}

	/**
	 * Compresses the current data in the buffer and write them to the underlying
	 * output stream
	 * 
	 * @throws IOException
	 */
	private void zip() throws IOException {
		if (bufferIndex == 0)
			return;

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		DataOutputStream gzip = new DataOutputStream(new GZIPOutputStream(baos));
		gzip.writeInt(bufferIndex);
		gzip.write(buffer, 0, bufferIndex);
		gzip.flush();
		gzip.close();

		byte[] compressedData = baos.toByteArray();
		output.writeInt(compressedData.length);
		output.write(compressedData);
		bufferIndex = 0;
	}

	@Override
	public void write(int b) throws IOException {
		if (bufferIndex >= buffer.length)
			zip();
		buffer[bufferIndex] = (byte) b;
		bufferIndex++;
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		int bytesToWrite;
		for (int bIndex = 0; bIndex < len;) {
			if (bufferIndex >= buffer.length)
				zip();
			bytesToWrite = Math.min(buffer.length - bufferIndex, len - bIndex);
			System.arraycopy(b, off + bIndex, buffer, bufferIndex, bytesToWrite);
			bufferIndex += bytesToWrite;
			bIndex += bytesToWrite;
		}
	}

	@Override
	public void flush() throws IOException {
		if (bufferIndex > 0) {
			zip();
			output.flush();
		}
	}

	@Override
	public void close() throws IOException {
		output.close();
		super.close();
	}

}
