package test.agilermi.codemobility;

import java.io.IOException;

import agilermi.codemobility.BasicCodeServer;

public class CodeMobilityCodeServer {

	public static void main(String[] args) throws IOException {
		BasicCodeServer.create().setCodeDirectory("bin").listen(80, false).enableDebugLogs();
	}

}
