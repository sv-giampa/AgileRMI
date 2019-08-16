package agilermi.authentication;

import java.io.Serializable;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class AuthenticationGroup implements Serializable {
	private static final long serialVersionUID = -6748965878496455129L;

	private String authId;
	private String passphrase;
	private List<SimpleEntry<String, Integer>> hosts;

	public void setCredentials(String authId, String passphrase) {
		this.authId = authId;
		this.passphrase = passphrase;
	}

	public String getAuthId() {
		return authId;
	}

	public String getPassphrase() {
		return passphrase;
	}

	public void addHost(String address, int port) {
		hosts.add(new SimpleEntry<>(address, port));
	}

	public boolean containsHost(String address, int port) {
		return hosts.stream().anyMatch(host -> host.getKey().equals(address) && host.getValue().equals(port));
	}

	public Collection<SimpleEntry<String, Integer>> getHosts() {
		return new ArrayList<>(hosts);
	}
}
