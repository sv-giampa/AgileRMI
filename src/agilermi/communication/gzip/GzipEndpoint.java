package agilermi.communication.gzip;

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

}
