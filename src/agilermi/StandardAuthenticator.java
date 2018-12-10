package agilermi;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Standard authenticator implementation that maintain authentication
 * information in central memory. It stores authentication identifiers and the
 * SHA-1 values of related pass-phrases. It allows to and authorization rules.
 * 
 * @author Salvatore Giampa'
 *
 */
public class StandardAuthenticator implements Authenticator, Serializable {
	private static final long serialVersionUID = -3193956625893347894L;

	// user level authorization
	private static final int USER_LEVEL = 0;
	// role level authorization
	private static final int ROLE_LEVEL = 1;

	// positive authorizations set
	private static final int POSITIVE_SET = 0;
	// negative authorizations set
	private static final int NEGATIVE_SET = 1;

	// Object-Method access authorization
	@SuppressWarnings("unchecked")
	private HashSet<OMKey>[/* AUTH LEVEL */][/* AUTH SET */] om = (HashSet<OMKey>[][]) new HashSet<?>[][] {
			new HashSet<?>[] { new HashSet<OMKey>(), new HashSet<OMKey>() },
			new HashSet<?>[] { new HashSet<OMKey>(), new HashSet<OMKey>() } };

	@SuppressWarnings("unchecked")
	private HashSet<MKey>[/* AUTH LEVEL */][/* AUTH SET */] m = (HashSet<MKey>[][]) new HashSet<?>[][] {
			new HashSet<?>[] { new HashSet<MKey>(), new HashSet<MKey>() },
			new HashSet<?>[] { new HashSet<MKey>(), new HashSet<MKey>() } };

	@SuppressWarnings("unchecked")
	private HashSet<OKey>[/* AUTH LEVEL */][/* AUTH SET */] o = (HashSet<OKey>[][]) new HashSet<?>[][] {
			new HashSet<?>[] { new HashSet<OKey>(), new HashSet<OKey>() },
			new HashSet<?>[] { new HashSet<OKey>(), new HashSet<OKey>() } };

	@SuppressWarnings("unchecked")
	private HashSet<CKey>[/* AUTH LEVEL */][/* AUTH SET */] c = (HashSet<CKey>[][]) new HashSet<?>[][] {
			new HashSet<?>[] { new HashSet<CKey>(), new HashSet<CKey>() },
			new HashSet<?>[] { new HashSet<CKey>(), new HashSet<CKey>() } };

	// map: authId -> role
	private Map<String, Set<String>> roleMap = new HashMap<>();

	// default authorization access
	private boolean defaultAuthorization = true;

	private Map<String, byte[]> authenticationMap = new TreeMap<>();

	private Map<String, Set<OMKey>> authIdOMKeys = new HashMap<>();
	private Map<String, Set<MKey>> authIdMKeys = new HashMap<>();
	private Map<String, Set<OKey>> authIdOKeys = new HashMap<>();
	private Map<String, Set<CKey>> authIdCKeys = new HashMap<>();

	private Map<String, Set<OMKey>> roleOMKeys = new HashMap<>();
	private Map<String, Set<MKey>> roleMKeys = new HashMap<>();
	private Map<String, Set<OKey>> roleOKeys = new HashMap<>();
	private Map<String, Set<CKey>> roleCKeys = new HashMap<>();

	public StandardAuthenticator() {
	}

	@Override
	public boolean authenticate(InetSocketAddress remoteAddress, String authId, String passphrase) {
		synchronized (authenticationMap) {
			if (!authenticationMap.containsKey(authId))
				return false;
			try {
				MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
				byte[] computedDigest = messageDigest.digest(passphrase.getBytes());
				byte[] knownDigest = authenticationMap.get(authId);
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
		synchronized (authenticationMap) {
			if (authenticationMap.containsKey(authId))
				return false;
			try {
				MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
				byte[] computedDigest = messageDigest.digest(passphrase.getBytes());
				authenticationMap.put(authId, computedDigest);
				roleMap.put(authId, new TreeSet<>());
				authIdOMKeys.put(authId, new HashSet<>());
				authIdMKeys.put(authId, new HashSet<>());
				authIdOKeys.put(authId, new HashSet<>());
				authIdCKeys.put(authId, new HashSet<>());
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
		return authenticationMap.containsKey(authId);
	}

	/**
	 * Unregister an identity.
	 * 
	 * @param authId the authentication identifier to unregister
	 */
	public void unregister(String authId) {
		synchronized (authenticationMap) {
			authenticationMap.remove(authId);
			roleMap.remove(authId);

			om[USER_LEVEL][POSITIVE_SET].removeAll(authIdOMKeys.get(authId));
			om[USER_LEVEL][NEGATIVE_SET].removeAll(authIdOMKeys.remove(authId));
			m[USER_LEVEL][POSITIVE_SET].removeAll(authIdMKeys.get(authId));
			m[USER_LEVEL][NEGATIVE_SET].removeAll(authIdMKeys.remove(authId));
			o[USER_LEVEL][POSITIVE_SET].removeAll(authIdOKeys.get(authId));
			o[USER_LEVEL][NEGATIVE_SET].removeAll(authIdOKeys.remove(authId));
			c[USER_LEVEL][POSITIVE_SET].removeAll(authIdCKeys.get(authId));
			c[USER_LEVEL][NEGATIVE_SET].removeAll(authIdCKeys.remove(authId));
		}
	}

	private Set<String> roles = new TreeSet<>();

	/**
	 * Create a new user role
	 * 
	 * @param role the role to create
	 */
	public void createRole(String role) {
		if (roles.contains(role))
			return;
		roles.add(role);
		roleOMKeys.put(role, new HashSet<>());
		roleMKeys.put(role, new HashSet<>());
		roleOKeys.put(role, new HashSet<>());
		roleCKeys.put(role, new HashSet<>());
	}

	/**
	 * Delete a user role
	 * 
	 * @param role the role to delete
	 */
	public void deleteRole(String role) {
		if (!roles.contains(role))
			return;
		roles.remove(role);
		om[ROLE_LEVEL][POSITIVE_SET].removeAll(roleOMKeys.get(role));
		om[ROLE_LEVEL][NEGATIVE_SET].removeAll(roleOMKeys.remove(role));
		m[ROLE_LEVEL][POSITIVE_SET].removeAll(roleMKeys.get(role));
		m[ROLE_LEVEL][NEGATIVE_SET].removeAll(roleMKeys.remove(role));
		o[ROLE_LEVEL][POSITIVE_SET].removeAll(roleOKeys.get(role));
		o[ROLE_LEVEL][NEGATIVE_SET].removeAll(roleOKeys.remove(role));
		c[ROLE_LEVEL][POSITIVE_SET].removeAll(roleCKeys.get(role));
		c[ROLE_LEVEL][NEGATIVE_SET].removeAll(roleCKeys.remove(role));
	}

	/**
	 * Add a role to a user
	 * 
	 * @param authId the identifier of the user
	 * @param role   the role to add
	 */
	public void addRole(String authId, String role) {
		if (!isRegistered(authId))
			throw new IllegalStateException("authentication identifier not registered");
		if (!roles.contains(role))
			throw new IllegalStateException("role not created");
		roleMap.get(authId).add(role);
	}

	/**
	 * Remove a role from a user
	 * 
	 * @param authId the identifier of the user
	 * @param role   the role to remove
	 */
	public void removeRole(String authId, String role) {
		if (!isRegistered(authId))
			return;
		roleMap.get(authId).remove(role);
	}

	/**
	 * Sets the default authorization, that is the default return value of the
	 * {@link #authorize(String, Object, Method)} method when no criterion has been
	 * found
	 * 
	 * @param authorizeByDefault the default authorization
	 */
	public void setDefaultAuthorization(boolean authorizeByDefault) {
		this.defaultAuthorization = authorizeByDefault;
	}

	/**
	 * Returns the default authorization, that is the default return value of the
	 * {@link #authorize(String, Object, Method)} method when no criterion has been
	 * found
	 * 
	 * @return the default authorization
	 */
	public boolean getDefaultAuthorization() {
		return defaultAuthorization;
	}

	/*
	 * **********************************************
	 * **********************************************
	 */

	/**
	 * Set object-method couple authorization for a user
	 * 
	 * @param authId     the authentication identifier to authorize
	 * @param object     the object on which authorization is set
	 * @param method     the method on which authorization is set
	 * @param authorized 0=authorized, 1=not authorized, otherwise=default
	 *                   authorization
	 */
	public void setIdAuthorization(String authId, Object object, Method method, int authorized) {
		Set<OMKey> set = authIdOMKeys.get(authId);
		OMKey key = new OMKey(authId, object, method);
		if (authorized < 0 || authorized > 1) {
			om[USER_LEVEL][0].remove(key);
			om[USER_LEVEL][1].remove(key);
			set.remove(key);
		} else {
			om[USER_LEVEL][authorized].add(key);
			set.add(key);
		}
	}

	/**
	 * Set method couple authorization for a user
	 * 
	 * @param authId     the authentication identifier to authorize
	 * @param method     the method on which authorization is set
	 * @param authorized 0=authorized, 1=not authorized, otherwise=default
	 *                   authorization
	 */
	public void setIdAuthorization(String authId, Method method, int authorized) {
		Set<MKey> set = authIdMKeys.get(authId);
		MKey key = new MKey(authId, method);
		if (authorized < 0 || authorized > 1) {
			m[USER_LEVEL][0].remove(key);
			m[USER_LEVEL][1].remove(key);
			set.remove(key);
		} else {
			m[USER_LEVEL][authorized].add(key);
			set.add(key);
		}
	}

	/**
	 * Set object couple authorization for a user
	 * 
	 * @param authId     the authentication identifier to authorize
	 * @param object     the object on which authorization is set
	 * @param authorized 0=authorized, 1=not authorized, otherwise=default
	 *                   authorization
	 */
	public void setIdAuthorization(String authId, Object object, int authorized) {
		Set<OKey> set = authIdOKeys.get(authId);
		OKey key = new OKey(authId, object);
		if (authorized < 0 || authorized > 1) {
			o[USER_LEVEL][0].remove(key);
			o[USER_LEVEL][1].remove(key);
			set.add(key);
		} else {
			o[USER_LEVEL][authorized].add(new OKey(authId, object));
			set.add(key);
		}
	}

	/**
	 * Set class couple authorization for a user
	 * 
	 * @param authId     the authentication identifier to authorize
	 * @param cls        the class on which authorization is set
	 * @param authorized 0=authorized, 1=not authorized, otherwise=default
	 *                   authorization
	 */
	public void setIdAuthorization(String authId, Class<?> cls, int authorized) {
		Set<CKey> set = authIdCKeys.get(authId);
		CKey key = new CKey(authId, cls);
		if (authorized < 0 || authorized > 1) {
			c[USER_LEVEL][0].remove(key);
			c[USER_LEVEL][1].remove(key);
			set.add(key);
		} else {
			c[USER_LEVEL][authorized].add(key);
			set.add(key);
		}
	}

	/*
	 * **********************************************
	 * **********************************************
	 */

	/**
	 * Set object-method couple authorization for a role
	 * 
	 * @param role       the role to authorize
	 * @param object     the object on which authorization is set
	 * @param method     the method on which authorization is set
	 * @param authorized 0=authorized, 1=not authorized, otherwise=default
	 *                   authorization
	 */
	public void setRoleAuthorization(String role, Object object, Method method, int authorized) {
		OMKey key = new OMKey(role, object, method);
		if (authorized < 0 || authorized > 1) {
			om[ROLE_LEVEL][0].remove(key);
			om[ROLE_LEVEL][1].remove(key);
		} else {
			om[ROLE_LEVEL][authorized].add(key);
		}
	}

	/**
	 * Set method authorization for a role
	 * 
	 * @param role       the role to authorize
	 * @param method     the method on which authorization is set
	 * @param authorized 0=authorized, 1=not authorized, otherwise=default
	 *                   authorization
	 */
	public void setRoleAuthorization(String role, Method method, int authorized) {
		MKey key = new MKey(role, method);
		if (authorized < 0 || authorized > 1) {
			m[ROLE_LEVEL][0].remove(key);
			m[ROLE_LEVEL][1].remove(key);
		} else {
			m[ROLE_LEVEL][authorized].add(key);
		}
	}

	/**
	 * Set object authorization for a role
	 * 
	 * @param role       the role to authorize
	 * @param object     the object on which authorization is set
	 * @param authorized 0=authorized, 1=not authorized, otherwise=default
	 *                   authorization
	 */
	public void setRoleAuthorization(String role, Object object, int authorized) {
		OKey key = new OKey(role, object);
		if (authorized < 0 || authorized > 1) {
			o[ROLE_LEVEL][0].remove(key);
			o[ROLE_LEVEL][1].remove(key);
		} else {
			o[ROLE_LEVEL][authorized].add(key);
		}
	}

	/**
	 * Set class authorization for a role
	 * 
	 * @param role       the role to authorize
	 * @param cls        the class on which authorization is set
	 * @param authorized 0=authorized, 1=not authorized, otherwise=default
	 *                   authorization
	 */
	public void setRoleAuthorization(String role, Class<?> cls, int authorized) {
		CKey key = new CKey(role, cls);
		if (authorized < 0 || authorized > 1) {
			c[ROLE_LEVEL][0].remove(key);
			c[ROLE_LEVEL][1].remove(key);
		} else {
			c[ROLE_LEVEL][authorized].add(key);
		}
	}

	/*
	 * **********************************************
	 * **********************************************
	 */

	@Override
	public boolean authorize(String authId, Object object, Method method) {

		if (om[USER_LEVEL][POSITIVE_SET].contains(new OMKey(authId, object, method)))
			return true;
		if (om[USER_LEVEL][NEGATIVE_SET].contains(new OMKey(authId, object, method)))
			return false;

		if (m[USER_LEVEL][POSITIVE_SET].contains(new MKey(authId, method)))
			return true;
		if (m[USER_LEVEL][NEGATIVE_SET].contains(new MKey(authId, method)))
			return false;

		if (o[USER_LEVEL][POSITIVE_SET].contains(new OKey(authId, object)))
			return true;
		if (o[USER_LEVEL][NEGATIVE_SET].contains(new OKey(authId, object)))
			return false;

		if (c[USER_LEVEL][POSITIVE_SET].contains(new CKey(authId, object.getClass())))
			return true;
		if (c[USER_LEVEL][NEGATIVE_SET].contains(new CKey(authId, object.getClass())))
			return false;

		Set<String> userRoles = roleMap.get(authId);
		if (userRoles != null && userRoles.size() > 0) {
			boolean unauthorized = false;
			for (String role : userRoles) {
				if (om[ROLE_LEVEL][POSITIVE_SET].contains(new OMKey(role, object, method)))
					return true;
				if (om[ROLE_LEVEL][NEGATIVE_SET].contains(new OMKey(role, object, method)))
					unauthorized = true;

				if (m[ROLE_LEVEL][POSITIVE_SET].contains(new MKey(role, method)))
					return true;
				if (m[ROLE_LEVEL][NEGATIVE_SET].contains(new MKey(role, method)))
					unauthorized = true;

				if (o[ROLE_LEVEL][POSITIVE_SET].contains(new OKey(role, object)))
					return true;
				if (o[ROLE_LEVEL][NEGATIVE_SET].contains(new OKey(role, object)))
					unauthorized = true;

				if (c[ROLE_LEVEL][POSITIVE_SET].contains(new CKey(role, object.getClass())))
					return true;
				if (c[ROLE_LEVEL][NEGATIVE_SET].contains(new CKey(role, object.getClass())))
					unauthorized = true;
			}
			if (unauthorized)
				return false;
		}

		return defaultAuthorization;
	}

	/**
	 * Object-Method authorization
	 * 
	 * @author Salvatore Giampa'
	 *
	 */
	private static class OMKey implements Serializable {
		private static final long serialVersionUID = -8218248533055503132L;
		String id;
		Object object;
		Method method;

		public OMKey(String id, Object object, Method method) {
			super();
			this.id = id;
			this.object = object;
			this.method = method;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((id == null) ? 0 : id.hashCode());
			result = prime * result + ((method == null) ? 0 : method.hashCode());
			result = prime * result + ((object == null) ? 0 : object.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			OMKey other = (OMKey) obj;
			if (id == null) {
				if (other.id != null)
					return false;
			} else if (!id.equals(other.id))
				return false;
			if (method == null) {
				if (other.method != null)
					return false;
			} else if (!method.equals(other.method))
				return false;
			if (object == null) {
				if (other.object != null)
					return false;
			} else if (!object.equals(other.object))
				return false;
			return true;
		}

	}

	/**
	 * Method authorization
	 * 
	 * @author Salvatore Giampa'
	 *
	 */
	private static class MKey implements Serializable {
		private static final long serialVersionUID = -6833607610910292885L;
		String id;
		Method method;

		public MKey(String id, Method method) {
			super();
			this.id = id;
			this.method = method;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((id == null) ? 0 : id.hashCode());
			result = prime * result + ((method == null) ? 0 : method.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			MKey other = (MKey) obj;
			if (id == null) {
				if (other.id != null)
					return false;
			} else if (!id.equals(other.id))
				return false;
			if (method == null) {
				if (other.method != null)
					return false;
			} else if (!method.equals(other.method))
				return false;
			return true;
		}

	}

	/**
	 * Object authorization
	 * 
	 * @author Salvatore Giampa'
	 *
	 */
	private static class OKey implements Serializable {
		private static final long serialVersionUID = 1004449160898466928L;
		String id;
		Object object;

		public OKey(String id, Object object) {
			super();
			this.id = id;
			this.object = object;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((id == null) ? 0 : id.hashCode());
			result = prime * result + ((object == null) ? 0 : object.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			OKey other = (OKey) obj;
			if (id == null) {
				if (other.id != null)
					return false;
			} else if (!id.equals(other.id))
				return false;
			if (object == null) {
				if (other.object != null)
					return false;
			} else if (!object.equals(other.object))
				return false;
			return true;
		}
	}

	/**
	 * Class authorization
	 * 
	 * @author Salvatore Giampa'
	 *
	 */
	private static class CKey implements Serializable {
		private static final long serialVersionUID = 52120543231718091L;
		String id;
		Class<?> cls;

		public CKey(String id, Class<?> cls) {
			super();
			this.id = id;
			this.cls = cls;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((cls == null) ? 0 : cls.hashCode());
			result = prime * result + ((id == null) ? 0 : id.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			CKey other = (CKey) obj;
			if (cls == null) {
				if (other.cls != null)
					return false;
			} else if (!cls.equals(other.cls))
				return false;
			if (id == null) {
				if (other.id != null)
					return false;
			} else if (!id.equals(other.id))
				return false;
			return true;
		}
	}

	public static void main(String[] args) throws NoSuchMethodException, SecurityException {
		StandardAuthenticator auth = new StandardAuthenticator();
		auth.setDefaultAuthorization(false);

		Object obj = new String();
		Method method = String.class.getMethod("length");

		auth.createRole("USER");
		auth.register("user", "pass");
		auth.addRole("user", "USER");

		System.out.println("authenticate: " + auth.authenticate(null, "user", "pass"));
		System.out.println("authorize: " + auth.authorize("user", obj, method));
		auth.setIdAuthorization("user", obj.getClass(), 1);
		auth.setIdAuthorization("user", obj, method, 0);
		System.out.println("authorize: " + auth.authorize("user", obj, method));

	}

}
