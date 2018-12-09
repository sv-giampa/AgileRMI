package agilermi;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

/**
 * Standard authenticator implementation that maintain authentication
 * information in central memory. It stores authentication identifiers and the
 * SHA-1 values of related pass-phrases. It authorizes all the invocations.
 * 
 * @author Salvatore Giampa'
 *
 */
public class StandardAuthenticator implements Authenticator {

	private Map<String, byte[]> auth = new TreeMap<>();

	@Override
	public boolean authenticate(InetSocketAddress remoteAddress, String authId, String passphrase) {
		synchronized (auth) {
			if (!auth.containsKey(authId))
				return false;
			try {
				MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
				byte[] computedDigest = messageDigest.digest(passphrase.getBytes());
				byte[] knownDigest = auth.get(authId);
				return Arrays.equals(knownDigest, computedDigest);
			} catch (NoSuchAlgorithmException e) {
				e.printStackTrace();
			}
			return false;
		}
	}

	/**
	 * REgister a new identity
	 * 
	 * @param authId     authentication id
	 * @param passphrase authentication pass-phrase
	 * @return true if registration was successful, false if a user with the given
	 *         identifier was already registered
	 */
	public boolean register(String authId, String passphrase) {
		synchronized (auth) {
			if (auth.containsKey(authId))
				return false;
			try {
				MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
				byte[] computedDigest = messageDigest.digest(passphrase.getBytes());
				auth.put(authId, computedDigest);
				return true;
			} catch (NoSuchAlgorithmException e) {
				e.printStackTrace();
			}
			return false;
		}
	}

	/**
	 * Gets the registration status for the given identifier
	 * 
	 * @param authId the authentication identifier to check
	 * @return true if the identifier was registered, false otherwise
	 */
	public boolean isRegistered(String authId) {
		return auth.containsKey(authId);
	}

	/**
	 * Unregister an identity.
	 * 
	 * @param authId the authentication identifier to unregister
	 */
	public void unregister(String authId) {
		synchronized (auth) {
			auth.remove(authId);
		}
	}

	@Override
	public boolean authorize(String authId, Object object, Method method) {
		return true;
	}

}
