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
import java.io.Serializable;

import agilermi.exception.RemoteException;

class RemoteOutputToStream extends OutputStream implements Serializable {
	private static final long serialVersionUID = 7555786769301705196L;
	private RemoteOutput remoteOutput;

	public RemoteOutputToStream(RemoteOutput remoteOutput) {
		if (remoteOutput == null)
			throw new NullPointerException("remoteOutput is null");
		this.remoteOutput = remoteOutput;
	}

	@Override
	public void write(int b) throws IOException {
		try {
			remoteOutput.write(b);
		} catch (RemoteException e) {
			throw new IOException(e);
		}
	}

	@Override
	public void write(byte[] b) throws IOException {
		try {
			remoteOutput.write(b);
		} catch (RemoteException e) {
			throw new IOException(e);
		}
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		byte[] buffer = new byte[len];
		System.arraycopy(b, off, buffer, 0, len);
		try {
			remoteOutput.write(buffer);
		} catch (RemoteException e) {
			throw new IOException(e);
		}
	}

	@Override
	public void flush() throws IOException {
		remoteOutput.flush();
	}

	@Override
	public void close() throws IOException {
		remoteOutput.close();
	}

}
