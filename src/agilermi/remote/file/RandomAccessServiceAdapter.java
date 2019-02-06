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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * This class is a wrapper for the standard {@link RandomAccessFile} instances
 * of the JDK. In fact, The mentioned class does not implement a unique
 * interface that exports all the methods of the implementation. It is useful
 * but not strictly necessary to export the {@link RandomAccessFile} instances
 * remotely.
 * 
 * @author Salvatore Giampa'
 *
 */
public class RandomAccessServiceAdapter implements RandomAccessService {

	private RandomAccessFile raf;

	public RandomAccessServiceAdapter(RandomAccessFile randomAccessFile) {
		this.raf = randomAccessFile;
	}

	public RandomAccessServiceAdapter(File file, String mode) throws FileNotFoundException {
		this.raf = new RandomAccessFile(file, mode);
	}

	public RandomAccessServiceAdapter(String name, String mode) throws FileNotFoundException {
		this.raf = new RandomAccessFile(name, mode);
	}

	@Override
	public byte[] readFully(int len) throws IOException {
		byte[] buffer = new byte[len];

		raf.readFully(buffer);
		return buffer;
	}

	@Override
	public int skipBytes(int n) throws IOException {
		return raf.skipBytes(n);
	}

	@Override
	public boolean readBoolean() throws IOException {
		return raf.readBoolean();
	}

	@Override
	public byte readByte() throws IOException {
		return raf.readByte();
	}

	@Override
	public int readUnsignedByte() throws IOException {
		return raf.readUnsignedByte();
	}

	@Override
	public short readShort() throws IOException {
		return raf.readShort();
	}

	@Override
	public int readUnsignedShort() throws IOException {
		return raf.readUnsignedShort();
	}

	@Override
	public char readChar() throws IOException {
		return raf.readChar();
	}

	@Override
	public int readInt() throws IOException {
		return raf.readInt();
	}

	@Override
	public long readLong() throws IOException {
		return raf.readLong();
	}

	@Override
	public float readFloat() throws IOException {
		return raf.readFloat();
	}

	@Override
	public double readDouble() throws IOException {
		return raf.readDouble();
	}

	@Override
	public String readLine() throws IOException {
		return raf.readLine();
	}

	@Override
	public String readUTF() throws IOException {
		return raf.readUTF();
	}

	@Override
	public void write(int b) throws IOException {
		raf.write(b);
	}

	@Override
	public void write(byte[] b) throws IOException {
		raf.write(b);
	}

	@Override
	public void writeBoolean(boolean v) throws IOException {
		raf.writeBoolean(v);
	}

	@Override
	public void writeByte(int v) throws IOException {
		raf.writeByte(v);
	}

	@Override
	public void writeShort(int v) throws IOException {
		raf.writeShort(v);
	}

	@Override
	public void writeChar(int v) throws IOException {
		raf.writeChar(v);
	}

	@Override
	public void writeInt(int v) throws IOException {
		raf.writeInt(v);
	}

	@Override
	public void writeLong(long v) throws IOException {
		raf.writeLong(v);
	}

	@Override
	public void writeFloat(float v) throws IOException {
		raf.writeFloat(v);
	}

	@Override
	public void writeDouble(double v) throws IOException {
		raf.writeDouble(v);
	}

	@Override
	public void writeBytes(String s) throws IOException {
		raf.writeBytes(s);
	}

	@Override
	public void writeChars(String s) throws IOException {
		raf.writeChars(s);
	}

	@Override
	public void writeUTF(String s) throws IOException {
		raf.writeUTF(s);
	}

	@Override
	public void close() throws IOException {
		raf.close();
	}

	@Override
	protected void finalize() throws Throwable {
		raf.close();
		super.finalize();
	}

	@Override
	public void seek(long pos) throws IOException {
		raf.seek(pos);
	}

	@Override
	public long length() throws IOException {
		return raf.length();
	}

	@Override
	public void setLength(long newLength) throws IOException {
		raf.setLength(newLength);
	}

}
