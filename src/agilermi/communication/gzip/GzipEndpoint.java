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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import agilermi.communication.ProtocolEndpoint;

public class GzipEndpoint implements ProtocolEndpoint {
	private ConitnuousGzipOutputStream output;
	private ContinuousGzipInputStream input;

	public GzipEndpoint(OutputStream output, InputStream input, int bufferSize) {
		this.output = new ConitnuousGzipOutputStream(output, bufferSize);
		this.input = new ContinuousGzipInputStream(input);
	}

	public int getOutputBufferSize() {
		return output.getBufferSize();
	}

	@Override
	public OutputStream getOutputStream() {
		return output;
	}

	@Override
	public InputStream getInputStream() {
		return input;
	}

	@Override
	public void connectionEnd() {
		try {
			output.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			input.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
