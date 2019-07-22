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
package agilermi.utility.file;

import java.io.Closeable;
import java.io.IOException;

import agilermi.configuration.Remote;
import agilermi.exception.RemoteException;

/**
 * Remote interface used to access a random access file remotely. The server
 * should use the {@link RandomAccessServiceAdapter} or another proper
 * implementation of this interface to provide access to a random access file or
 * to a similar data storage.
 * 
 * @author Salvatore Giampa'
 *
 */
public interface RandomAccessService extends Closeable, Remote {

	public byte[] readFully(int len) throws IOException, RemoteException;

	public int skipBytes(int n) throws IOException, RemoteException;

	public boolean readBoolean() throws IOException, RemoteException;

	public byte readByte() throws IOException, RemoteException;

	public int readUnsignedByte() throws IOException, RemoteException;

	public short readShort() throws IOException, RemoteException;

	public int readUnsignedShort() throws IOException, RemoteException;

	public char readChar() throws IOException, RemoteException;

	public int readInt() throws IOException, RemoteException;

	public long readLong() throws IOException, RemoteException;

	public float readFloat() throws IOException, RemoteException;

	public double readDouble() throws IOException, RemoteException;

	public String readLine() throws IOException, RemoteException;

	public String readUTF() throws IOException, RemoteException;

	public void write(int b) throws IOException, RemoteException;

	public void write(byte[] b) throws IOException, RemoteException;

	public void writeBoolean(boolean v) throws IOException, RemoteException;

	public void writeByte(int v) throws IOException, RemoteException;

	public void writeShort(int v) throws IOException, RemoteException;

	public void writeChar(int v) throws IOException, RemoteException;

	public void writeInt(int v) throws IOException, RemoteException;

	public void writeLong(long v) throws IOException, RemoteException;

	public void writeFloat(float v) throws IOException, RemoteException;

	public void writeDouble(double v) throws IOException, RemoteException;

	public void writeBytes(String s) throws IOException, RemoteException;

	public void writeChars(String s) throws IOException, RemoteException;

	public void writeUTF(String s) throws IOException, RemoteException;

	public void seek(long pos) throws IOException, RemoteException;

	public long length() throws IOException, RemoteException;

	public void setLength(long newLength) throws IOException, RemoteException;
}
