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
import java.io.RandomAccessFile;

import agilermi.configuration.Remote;
import agilermi.configuration.annotation.RMISuppressFaults;
import agilermi.exception.RemoteException;

/**
 * Remote interface used to access a random access file remotely. The server
 * should use the {@link RafToRas} or another proper implementation of this
 * interface to provide access to a random access file or to a similar data
 * storage.
 * 
 * @author Salvatore Giampa'
 *
 */
public interface RandomAccessService extends Closeable, Remote {

	/**
	 * Converts a {@link RandomAccessFile} to a remote {@link RandomAccessService}.
	 * 
	 * @param raf the {@link RandomAccessFile} to convert
	 * @return A {@link RandomAccessService} that wraps the given
	 *         {@link RandomAccessFile} instance
	 */
	public static RandomAccessService convert(RandomAccessFile raf) {
		return new RafToRas(raf);
	}

	/**
	 * Same as {@link RandomAccessFile#readFully(byte[], int, int)} but construct
	 * the byte[] result itself, instead of taking it as a parameter.
	 * 
	 * @param len the max number of bytes to read
	 * @return the bytes read
	 * @throws IOException     if an I/O error occurs.
	 * @throws RemoteException if a RMI failure occurs
	 */
	public byte[] readFully(int len) throws IOException, RemoteException;

	/**
	 * Same as {@link RandomAccessFile#skipBytes(int)}.
	 * 
	 * @param n the number of bytes to be skipped.
	 * @return the actual number of bytes skipped.
	 * @throws IOException     if an I/O error occurs.
	 * @throws RemoteException if a RMI failure occurs
	 */
	public int skipBytes(int n) throws IOException, RemoteException;

	/**
	 * Same as {@link RandomAccessFile#readBoolean()}.
	 * 
	 * @return the read value
	 * @throws IOException     if an I/O error occurs.
	 * @throws RemoteException if a RMI failure occurs
	 */
	public boolean readBoolean() throws IOException, RemoteException;

	/**
	 * Same as {@link RandomAccessFile#readByte()}.
	 * 
	 * @return the read value
	 * @throws IOException     if an I/O error occurs.
	 * @throws RemoteException if a RMI failure occurs
	 */
	public byte readByte() throws IOException, RemoteException;

	/**
	 * Same as {@link RandomAccessFile#readUnsignedByte()}.
	 * 
	 * @return the read value
	 * @throws IOException     if an I/O error occurs.
	 * @throws RemoteException if a RMI failure occurs
	 */
	public int readUnsignedByte() throws IOException, RemoteException;

	/**
	 * Same as {@link RandomAccessFile#readShort()}.
	 * 
	 * @return the read value
	 * @throws IOException     if an I/O error occurs.
	 * @throws RemoteException if a RMI failure occurs
	 */
	public short readShort() throws IOException, RemoteException;

	/**
	 * Same as {@link RandomAccessFile#readUnsignedShort()}.
	 * 
	 * @return the read value
	 * @throws IOException     if an I/O error occurs.
	 * @throws RemoteException if a RMI failure occurs
	 */
	public int readUnsignedShort() throws IOException, RemoteException;

	/**
	 * Same as {@link RandomAccessFile#readChar()}.
	 * 
	 * @return the read value
	 * @throws IOException     if an I/O error occurs.
	 * @throws RemoteException if a RMI failure occurs
	 */
	public char readChar() throws IOException, RemoteException;

	/**
	 * Same as {@link RandomAccessFile#readInt()}.
	 * 
	 * @return the read value
	 * @throws IOException     if an I/O error occurs.
	 * @throws RemoteException if a RMI failure occurs
	 */
	public int readInt() throws IOException, RemoteException;

	/**
	 * Same as {@link RandomAccessFile#readLong()}.
	 * 
	 * @return the read value
	 * @throws IOException     if an I/O error occurs.
	 * @throws RemoteException if a RMI failure occurs
	 */
	public long readLong() throws IOException, RemoteException;

	/**
	 * Same as {@link RandomAccessFile#readFloat()}.
	 * 
	 * @return the read value
	 * @throws IOException     if an I/O error occurs.
	 * @throws RemoteException if a RMI failure occurs
	 */
	public float readFloat() throws IOException, RemoteException;

	/**
	 * Same as {@link RandomAccessFile#readDouble()}.
	 * 
	 * @return the read value
	 * @throws IOException     if an I/O error occurs.
	 * @throws RemoteException if a RMI failure occurs
	 */
	public double readDouble() throws IOException, RemoteException;

	/**
	 * Same as {@link RandomAccessFile#readLine()}.
	 * 
	 * @return the read value
	 * @throws IOException     if an I/O error occurs.
	 * @throws RemoteException if a RMI failure occurs
	 */
	public String readLine() throws IOException, RemoteException;

	/**
	 * Same as {@link RandomAccessFile#readUTF()}.
	 * 
	 * @return the read value
	 * @throws IOException     if an I/O error occurs.
	 * @throws RemoteException if a RMI failure occurs
	 */
	public String readUTF() throws IOException, RemoteException;

	/**
	 * Same as {@link RandomAccessFile#write(int)}.
	 * 
	 * @param b the byte to write
	 * @throws IOException     if an I/O error occurs.
	 * @throws RemoteException if a RMI failure occurs
	 */
	public void write(int b) throws IOException, RemoteException;

	/**
	 * Same as {@link RandomAccessFile#write(byte[])}.
	 * 
	 * @param b the bytes to write
	 * @throws IOException     if an I/O error occurs.
	 * @throws RemoteException if a RMI failure occurs
	 */
	public void write(byte[] b) throws IOException, RemoteException;

	/**
	 * Same as {@link RandomAccessFile#writeBoolean(boolean)}.
	 * 
	 * @param v the value to write
	 * @throws IOException     if an I/O error occurs.
	 * @throws RemoteException if a RMI failure occurs
	 */
	public void writeBoolean(boolean v) throws IOException, RemoteException;

	/**
	 * Same as {@link RandomAccessFile#writeByte(int)}.
	 * 
	 * @param v the value to write
	 * @throws IOException     if an I/O error occurs.
	 * @throws RemoteException if a RMI failure occurs
	 */
	public void writeByte(int v) throws IOException, RemoteException;

	/**
	 * Same as {@link RandomAccessFile#writeShort(int)}.
	 * 
	 * @param v the value to write
	 * @throws IOException     if an I/O error occurs.
	 * @throws RemoteException if a RMI failure occurs
	 */
	public void writeShort(int v) throws IOException, RemoteException;

	/**
	 * Same as {@link RandomAccessFile#writeChar(int)}.
	 * 
	 * @param v the integer to write
	 * @throws IOException     if an I/O error occurs.
	 * @throws RemoteException if a RMI failure occurs
	 */
	public void writeChar(int v) throws IOException, RemoteException;

	/**
	 * Same as {@link RandomAccessFile#writeInt(int)}.
	 * 
	 * @param v the integer to write
	 * @throws IOException     if an I/O error occurs.
	 * @throws RemoteException if a RMI failure occurs
	 */
	public void writeInt(int v) throws IOException, RemoteException;

	/**
	 * Same as {@link RandomAccessFile#writeLong(long)}.
	 * 
	 * @param v the integer to write
	 * @throws IOException     if an I/O error occurs.
	 * @throws RemoteException if a RMI failure occurs
	 */
	public void writeLong(long v) throws IOException, RemoteException;

	/**
	 * Same as {@link RandomAccessFile#writeFloat(float)}.
	 * 
	 * @param v the integer to write
	 * @throws IOException     if an I/O error occurs.
	 * @throws RemoteException if a RMI failure occurs
	 */
	public void writeFloat(float v) throws IOException, RemoteException;

	/**
	 * Same as {@link RandomAccessFile#writeDouble(double)}.
	 * 
	 * @param v the integer to write
	 * @throws IOException     if an I/O error occurs.
	 * @throws RemoteException if a RMI failure occurs
	 */
	public void writeDouble(double v) throws IOException, RemoteException;

	/**
	 * Same as {@link RandomAccessFile#writeBytes(String)}.
	 * 
	 * @param s the string to write
	 * @throws IOException     if an I/O error occurs.
	 * @throws RemoteException if a RMI failure occurs
	 */
	public void writeBytes(String s) throws IOException, RemoteException;

	/**
	 * Same as {@link RandomAccessFile#writeChars(String)}.
	 * 
	 * @param s the string to write
	 * @throws IOException     if an I/O error occurs.
	 * @throws RemoteException if a RMI failure occurs
	 */
	public void writeChars(String s) throws IOException, RemoteException;

	/**
	 * Same as {@link RandomAccessFile#writeUTF(String)}.
	 * 
	 * @param s the string to write
	 * @throws IOException     if an I/O error occurs.
	 * @throws RemoteException if a RMI failure occurs
	 */
	public void writeUTF(String s) throws IOException, RemoteException;

	/**
	 * Same as {@link RandomAccessFile#seek(long)}.
	 * 
	 * @param pos the offset position, measured in bytes from thebeginning of the
	 *            file, at which to set the filepointer.
	 * @throws IOException     if an I/O error occurs.
	 * @throws RemoteException if a RMI failure occurs
	 */
	public void seek(long pos) throws IOException, RemoteException;

	/**
	 * Same as {@link RandomAccessFile#length()}.
	 * 
	 * @return the length of this file, measured in bytes.
	 * @throws IOException     if an I/O error occurs.
	 * @throws RemoteException if a RMI failure occurs
	 */
	public long length() throws IOException, RemoteException;

	/**
	 * Same as {@link RandomAccessFile#setLength(long)}.
	 * 
	 * @param newLength The desired length of the file
	 * @throws IOException     if an I/O error occurs.
	 * @throws RemoteException if a RMI failure occurs
	 */
	public void setLength(long newLength) throws IOException, RemoteException;

	@Override
	@RMISuppressFaults
	void close() throws IOException;
}
