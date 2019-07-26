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
import java.io.IOException;
import java.io.InputStream;

import agilermi.configuration.Remote;
import agilermi.configuration.annotation.RMISuppressFaults;
import agilermi.exception.RemoteException;

/**
 * Represents a remote version of an {@link InputStream}
 * 
 * @author Salvatore Giampa'
 *
 */
public interface RemoteInput extends Closeable, Remote {

	/**
	 * Returns a serializable wrapper that holds a remote reference to the given
	 * {@link InputStream}.
	 * 
	 * @param input the {@link InputStream} to wrap
	 * @return a remotely sharable version of the given {@link InputStream}
	 */
	public static InputStream wrap(InputStream input) {
		return convert(convert(input));
	}

	/**
	 * Converts a {@link RemoteInput} to an {@link InputStream}.
	 * 
	 * @param input the {@link RemoteInput} to convert
	 * @return a new {@link InputStream} that wraps the given {@link RemoteInput}
	 */
	public static InputStream convert(RemoteInput input) {
		return new RemoteInputToStream(input);
	}

	/**
	 * Converts an {@link InputStream} to a {@link RemoteInput}.
	 * 
	 * @param input the {@link InputStream} to convert
	 * @return a new {@link RemoteInput} that wraps the given {@link InputStream}
	 */
	public static RemoteInput convert(InputStream input) {
		return new StreamToRemoteInput(input);
	}

	/**
	 * Read data from the remote byte input.
	 * 
	 * @return a byte read as an integer number between 0 and 255 or -1 if no data
	 *         is available
	 * @throws IOException     if an I/O error occurs
	 * @throws RemoteException if a RMI failure occurs
	 */
	int read() throws IOException, RemoteException;

	/**
	 * Read data from the remote byte input.
	 * 
	 * @param len the maximum number of bytes to read
	 * @return the array of bytes read
	 * @throws IOException     if an I/O error occurs
	 * @throws RemoteException if a RMI failure occurs
	 */
	byte[] read(int len) throws IOException, RemoteException;

	/**
	 * Gets an estimate of the available bytes.
	 * 
	 * @return an estimate of the available bytes
	 * @throws IOException     if an I/O error occurs
	 * @throws RemoteException if a RMI failure occurs
	 */
	int available() throws IOException, RemoteException;

	@Override
	@RMISuppressFaults
	void close() throws IOException;
}
