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

import java.io.InputStream;
import java.io.OutputStream;

import agilermi.communication.ProtocolEndpointFactory;

public class GzipEndpointFactory implements ProtocolEndpointFactory {
	private int bufferSize;

	public GzipEndpointFactory() {
		this(512);
	}

	public GzipEndpointFactory(int bufferSize) {
		this.bufferSize = bufferSize;
	}

	public int getBufferSize() {
		return bufferSize;
	}

	@Override
	public GzipEndpoint createEndpoint(OutputStream outputStream, InputStream inputStream) {
		return new GzipEndpoint(outputStream, inputStream, bufferSize);
	}

//	public static void main(String[] args) throws IOException {
//		GzipEndpointFactory factory = new GzipEndpointFactory();
//
//		PipedOutputStream uout1 = new PipedOutputStream();
//		PipedOutputStream uout2 = new PipedOutputStream();
//		PipedInputStream uin1 = new PipedInputStream(uout2, 3000);
//		PipedInputStream uin2 = new PipedInputStream(uout1, 3000);
//
//		GzipEndpoint ep1 = factory.createEndpoint(uout1, uin1);
//		GzipEndpoint ep2 = factory.createEndpoint(uout2, uin2);
//
//		DataOutputStream out1 = new DataOutputStream(ep1.getOutputStream());
//		DataInputStream in1 = new DataInputStream(ep1.getInputStream());
//
//		DataOutputStream out2 = new DataOutputStream(ep2.getOutputStream());
//		DataInputStream in2 = new DataInputStream(ep2.getInputStream());
//
//		out1.writeUTF("there ");
//		out1.flush();
//		out1.writeUTF("and");
//		out1.flush();
//		out1.writeUTF("...");
//		out1.flush();
//		System.out.println(in2.readUTF() + in2.readUTF() + in2.readUTF());
//
//		out2.writeUTF("...");
//		out2.flush();
//		out2.writeUTF("back ");
//		out2.flush();
//		out2.writeUTF("again");
//		out2.flush();
//		System.out.println(in1.readUTF() + in1.readUTF() + in1.readUTF());
//	}

}
