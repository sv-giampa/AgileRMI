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
package agilermi.remote.file;

import java.io.Closeable;
import java.io.IOException;

import agilermi.configuration.Remote;

public interface RandomAccessService extends Closeable, Remote {

	public byte[] readFully(int len) throws IOException;

	public int skipBytes(int n) throws IOException;

	public boolean readBoolean() throws IOException;

	public byte readByte() throws IOException;

	public int readUnsignedByte() throws IOException;

	public short readShort() throws IOException;

	public int readUnsignedShort() throws IOException;

	public char readChar() throws IOException;

	public int readInt() throws IOException;

	public long readLong() throws IOException;

	public float readFloat() throws IOException;

	public double readDouble() throws IOException;

	public String readLine() throws IOException;

	public String readUTF() throws IOException;

	public void write(int b) throws IOException;

	public void write(byte[] b) throws IOException;

	public void writeBoolean(boolean v) throws IOException;

	public void writeByte(int v) throws IOException;

	public void writeShort(int v) throws IOException;

	public void writeChar(int v) throws IOException;

	public void writeInt(int v) throws IOException;

	public void writeLong(long v) throws IOException;

	public void writeFloat(float v) throws IOException;

	public void writeDouble(double v) throws IOException;

	public void writeBytes(String s) throws IOException;

	public void writeChars(String s) throws IOException;

	public void writeUTF(String s) throws IOException;

	public void seek(long pos) throws IOException;

	public long length() throws IOException;

	public void setLength(long newLength) throws IOException;
}
