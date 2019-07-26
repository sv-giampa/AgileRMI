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

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;

import agilermi.configuration.Remote;
import agilermi.configuration.annotation.RMISuppressFaults;
import agilermi.exception.RemoteException;

/**
 * Represents a remote version of an {@link OutputStream}
 * 
 * @author Salvatore Giampa'
 *
 */
public interface RemoteOutput extends Closeable, Flushable, Remote {

	/**
	 * Returns a serializable wrapper that holds a remote reference to the given
	 * {@link OutputStream}.
	 * 
	 * @param output the {@link OutputStream} to wrap
	 * @return a remotely sharable version of the given {@link OutputStream}
	 */
	public static OutputStream wrap(OutputStream output) {
		return convert(convert(output));
	}

	/**
	 * Converts a {@link RemoteOutput} to an {@link OutputStream}.
	 * 
	 * @param output the {@link RemoteOutput} to convert
	 * @return a new {@link OutputStream} that wraps the given {@link RemoteOutput}
	 */
	public static OutputStream convert(RemoteOutput output) {
		return new RemoteOutputToStream(output);
	}

	/**
	 * Converts an {@link OutputStream} to a {@link RemoteOutput}.
	 * 
	 * @param output the {@link OutputStream} to convert
	 * @return a new {@link RemoteOutput} that wraps the given {@link OutputStream}
	 */
	public static RemoteOutput convert(OutputStream output) {
		return new StreamToRemoteOutput(output);
	}

	/**
	 * Write a byte to the remote output.
	 * 
	 * @param b the byte to write
	 * @throws IOException     if an I/O error occurs
	 * @throws RemoteException if a RMI failure occurs
	 */
	void write(int b) throws IOException, RemoteException;

	/**
	 * Write a block of bytes to the remote output.
	 * 
	 * @param bytes the bytes to write
	 * @throws IOException     if an I/O error occurs
	 * @throws RemoteException if a RMI failure occurs
	 */
	void write(byte[] bytes) throws IOException, RemoteException;

	@Override
	@RMISuppressFaults
	void close() throws IOException;

	@Override
	@RMISuppressFaults
	void flush() throws IOException;
}
