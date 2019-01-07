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
package agilermi.authentication;

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

	private Object lock = new String();

	// user level authorization
	private static final int USER_LEVEL = 0;
	// role level authorization
	private static final int ROLE_LEVEL = 1;

	// positive authorizations set
	private static final int POSITIVE_SET = 0;
	// negative authorizations set
	private static final int NEGATIVE_SET = 1;

	// Object-Method, Method, Object and Class levels for access authorization
	// setArray[USER_LEVEL][POSITIVE_SET] => set of granted accesses for users
	// setArray[USER_LEVEL][NEGATIVE_SET] => set of negated accesses for users
	// setArray[ROLE_LEVEL][POSITIVE_SET] => set of granted accesses for roles
	// setArray[ROLE_LEVEL][NEGATIVE_SET] => set of negated accesses for roles
	@SuppressWarnings("unchecked")
	private HashSet<ObjectMethodAccess>[/* AUTH LEVEL */][/* AUTH SET */] objectMethodSet = (HashSet<ObjectMethodAccess>[][]) new HashSet<?>[][] {
			new HashSet<?>[] { new HashSet<ObjectMethodAccess>(), new HashSet<ObjectMethodAccess>() },
			new HashSet<?>[] { new HashSet<ObjectMethodAccess>(), new HashSet<ObjectMethodAccess>() } };

	@SuppressWarnings("unchecked")
	private HashSet<MethodAccess>[/* AUTH LEVEL */][/* AUTH SET */] methodSet = (HashSet<MethodAccess>[][]) new HashSet<?>[][] {
			new HashSet<?>[] { new HashSet<MethodAccess>(), new HashSet<MethodAccess>() },
			new HashSet<?>[] { new HashSet<MethodAccess>(), new HashSet<MethodAccess>() } };

	@SuppressWarnings("unchecked")
	private HashSet<ObjectAccess>[/* AUTH LEVEL */][/* AUTH SET */] objectSet = (HashSet<ObjectAccess>[][]) new HashSet<?>[][] {
			new HashSet<?>[] { new HashSet<ObjectAccess>(), new HashSet<ObjectAccess>() },
			new HashSet<?>[] { new HashSet<ObjectAccess>(), new HashSet<ObjectAccess>() } };

	@SuppressWarnings("unchecked")
	private HashSet<ClassAccess>[/* AUTH LEVEL */][/* AUTH SET */] classSet = (HashSet<ClassAccess>[][]) new HashSet<?>[][] {
			new HashSet<?>[] { new HashSet<ClassAccess>(), new HashSet<ClassAccess>() },
			new HashSet<?>[] { new HashSet<ClassAccess>(), new HashSet<ClassAccess>() } };

	// map: authId -> role
	private Map<String, Set<String>> roleMap = new HashMap<>();

	// default authorization access
	private boolean defaultAuthorization = true;

	private Map<String, byte[]> authenticationMap = new HashMap<>();

	private Map<String, Set<ObjectMethodAccess>> objectMethodEntriesByAuthId = new HashMap<>();
	private Map<String, Set<MethodAccess>> methodEntriesByAuthId = new HashMap<>();
	private Map<String, Set<ObjectAccess>> objectEntriesByAuthId = new HashMap<>();
	private Map<String, Set<ClassAccess>> classEntriesByAuthId = new HashMap<>();

	private Map<String, Set<ObjectMethodAccess>> objectMethodEntriesByRole = new HashMap<>();
	private Map<String, Set<MethodAccess>> methodEntriesByRole = new HashMap<>();
	private Map<String, Set<ObjectAccess>> objectEntriesByRole = new HashMap<>();
	private Map<String, Set<ClassAccess>> classEntriesByRole = new HashMap<>();

	public StandardAuthenticator() {
	}

	@Override
	public boolean authenticate(InetSocketAddress remoteAddress, String authId, String passphrase) {
		synchronized (lock) {
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
		synchronized (lock) {
			if (authenticationMap.containsKey(authId))
				return false;
			try {
				MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
				byte[] computedDigest = messageDigest.digest(passphrase.getBytes());
				authenticationMap.put(authId, computedDigest);
				roleMap.put(authId, new TreeSet<>());
				objectMethodEntriesByAuthId.put(authId, new HashSet<>());
				methodEntriesByAuthId.put(authId, new HashSet<>());
				objectEntriesByAuthId.put(authId, new HashSet<>());
				classEntriesByAuthId.put(authId, new HashSet<>());
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
	 * Change the pass-phrase of a registered authentication identifier
	 * 
	 * @param authId     authentication identifier
	 * @param passphrase new pass-phrase
	 * @return true if operation was successful, false if the user is not registered
	 *         or the hash algorithm is not available
	 */
	public boolean changePassphrase(String authId, String passphrase) {
		synchronized (lock) {
			if (!isRegistered(authId))
				return false;
			try {
				MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
				byte[] computedDigest = messageDigest.digest(passphrase.getBytes());
				authenticationMap.put(authId, computedDigest);
				return true;
			} catch (NoSuchAlgorithmException e) {
				e.printStackTrace();
			}
			return false;
		}
	}

	/**
	 * Unregister an identity.
	 * 
	 * @param authId the authentication identifier to unregister
	 */
	public void unregister(String authId) {
		synchronized (lock) {
			authenticationMap.remove(authId);
			roleMap.remove(authId);

			objectMethodSet[USER_LEVEL][POSITIVE_SET].removeAll(objectMethodEntriesByAuthId.get(authId));
			objectMethodSet[USER_LEVEL][NEGATIVE_SET].removeAll(objectMethodEntriesByAuthId.remove(authId));
			methodSet[USER_LEVEL][POSITIVE_SET].removeAll(methodEntriesByAuthId.get(authId));
			methodSet[USER_LEVEL][NEGATIVE_SET].removeAll(methodEntriesByAuthId.remove(authId));
			objectSet[USER_LEVEL][POSITIVE_SET].removeAll(objectEntriesByAuthId.get(authId));
			objectSet[USER_LEVEL][NEGATIVE_SET].removeAll(objectEntriesByAuthId.remove(authId));
			classSet[USER_LEVEL][POSITIVE_SET].removeAll(classEntriesByAuthId.get(authId));
			classSet[USER_LEVEL][NEGATIVE_SET].removeAll(classEntriesByAuthId.remove(authId));
		}
	}

	private Set<String> roles = new TreeSet<>();

	/**
	 * Create a new user role
	 * 
	 * @param role the role to create
	 */
	public void createRole(String role) {
		synchronized (lock) {
			if (roles.contains(role))
				return;
			roles.add(role);
			objectMethodEntriesByRole.put(role, new HashSet<>());
			methodEntriesByRole.put(role, new HashSet<>());
			objectEntriesByRole.put(role, new HashSet<>());
			classEntriesByRole.put(role, new HashSet<>());

		}
	}

	/**
	 * Delete a user role
	 * 
	 * @param role the role to delete
	 */
	public void deleteRole(String role) {
		synchronized (lock) {
			if (!roles.contains(role))
				return;
			roles.remove(role);
			objectMethodSet[ROLE_LEVEL][POSITIVE_SET].removeAll(objectMethodEntriesByRole.get(role));
			objectMethodSet[ROLE_LEVEL][NEGATIVE_SET].removeAll(objectMethodEntriesByRole.remove(role));
			methodSet[ROLE_LEVEL][POSITIVE_SET].removeAll(methodEntriesByRole.get(role));
			methodSet[ROLE_LEVEL][NEGATIVE_SET].removeAll(methodEntriesByRole.remove(role));
			objectSet[ROLE_LEVEL][POSITIVE_SET].removeAll(objectEntriesByRole.get(role));
			objectSet[ROLE_LEVEL][NEGATIVE_SET].removeAll(objectEntriesByRole.remove(role));
			classSet[ROLE_LEVEL][POSITIVE_SET].removeAll(classEntriesByRole.get(role));
			classSet[ROLE_LEVEL][NEGATIVE_SET].removeAll(classEntriesByRole.remove(role));
		}
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
		synchronized (lock) {
			roleMap.get(authId).add(role);
		}
	}

	/**
	 * Remove a role from a user
	 * 
	 * @param authId the identifier of the user
	 * @param role   the role to remove
	 */
	public void removeRole(String authId, String role) {
		synchronized (lock) {
			if (!isRegistered(authId))
				return;
			roleMap.get(authId).remove(role);
		}
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
		synchronized (lock) {
			Set<ObjectMethodAccess> set = objectMethodEntriesByAuthId.get(authId);
			ObjectMethodAccess key = new ObjectMethodAccess(authId, object, method);
			if (authorized < 0 || authorized > 1) {
				objectMethodSet[USER_LEVEL][0].remove(key);
				objectMethodSet[USER_LEVEL][1].remove(key);
				set.remove(key);
			} else {
				objectMethodSet[USER_LEVEL][authorized].add(key);
				set.add(key);
			}
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
		synchronized (lock) {
			Set<MethodAccess> set = methodEntriesByAuthId.get(authId);
			MethodAccess key = new MethodAccess(authId, method);
			if (authorized < 0 || authorized > 1) {
				methodSet[USER_LEVEL][0].remove(key);
				methodSet[USER_LEVEL][1].remove(key);
				set.remove(key);
			} else {
				methodSet[USER_LEVEL][authorized].add(key);
				set.add(key);
			}
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
		synchronized (lock) {
			Set<ObjectAccess> set = objectEntriesByAuthId.get(authId);
			ObjectAccess key = new ObjectAccess(authId, object);
			if (authorized < 0 || authorized > 1) {
				objectSet[USER_LEVEL][0].remove(key);
				objectSet[USER_LEVEL][1].remove(key);
				set.add(key);
			} else {
				objectSet[USER_LEVEL][authorized].add(new ObjectAccess(authId, object));
				set.add(key);
			}
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
		synchronized (lock) {
			Set<ClassAccess> set = classEntriesByAuthId.get(authId);
			ClassAccess key = new ClassAccess(authId, cls);
			if (authorized < 0 || authorized > 1) {
				classSet[USER_LEVEL][0].remove(key);
				classSet[USER_LEVEL][1].remove(key);
				set.add(key);
			} else {
				classSet[USER_LEVEL][authorized].add(key);
				set.add(key);
			}
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
		synchronized (lock) {
			ObjectMethodAccess key = new ObjectMethodAccess(role, object, method);
			if (authorized < 0 || authorized > 1) {
				objectMethodSet[ROLE_LEVEL][0].remove(key);
				objectMethodSet[ROLE_LEVEL][1].remove(key);
			} else {
				objectMethodSet[ROLE_LEVEL][authorized].add(key);
			}
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
		synchronized (lock) {
			MethodAccess key = new MethodAccess(role, method);
			if (authorized < 0 || authorized > 1) {
				methodSet[ROLE_LEVEL][0].remove(key);
				methodSet[ROLE_LEVEL][1].remove(key);
			} else {
				methodSet[ROLE_LEVEL][authorized].add(key);
			}
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
		synchronized (lock) {
			ObjectAccess key = new ObjectAccess(role, object);
			if (authorized < 0 || authorized > 1) {
				objectSet[ROLE_LEVEL][0].remove(key);
				objectSet[ROLE_LEVEL][1].remove(key);
			} else {
				objectSet[ROLE_LEVEL][authorized].add(key);
			}
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
		synchronized (lock) {
			ClassAccess key = new ClassAccess(role, cls);
			if (authorized < 0 || authorized > 1) {
				classSet[ROLE_LEVEL][0].remove(key);
				classSet[ROLE_LEVEL][1].remove(key);
			} else {
				classSet[ROLE_LEVEL][authorized].add(key);
			}
		}
	}

	/*
	 * **********************************************
	 * **********************************************
	 */

	@Override
	public boolean authorize(String authId, Object object, Method method) {
		synchronized (lock) {
			if (objectMethodSet[USER_LEVEL][POSITIVE_SET].contains(new ObjectMethodAccess(authId, object, method)))
				return true;
			if (objectMethodSet[USER_LEVEL][NEGATIVE_SET].contains(new ObjectMethodAccess(authId, object, method)))
				return false;

			if (methodSet[USER_LEVEL][POSITIVE_SET].contains(new MethodAccess(authId, method)))
				return true;
			if (methodSet[USER_LEVEL][NEGATIVE_SET].contains(new MethodAccess(authId, method)))
				return false;

			if (objectSet[USER_LEVEL][POSITIVE_SET].contains(new ObjectAccess(authId, object)))
				return true;
			if (objectSet[USER_LEVEL][NEGATIVE_SET].contains(new ObjectAccess(authId, object)))
				return false;

			if (classSet[USER_LEVEL][POSITIVE_SET].contains(new ClassAccess(authId, object.getClass())))
				return true;
			if (classSet[USER_LEVEL][NEGATIVE_SET].contains(new ClassAccess(authId, object.getClass())))
				return false;

			Set<String> userRoles = roleMap.get(authId);
			if (userRoles != null && userRoles.size() > 0) {
				boolean unauthorized = false;
				for (String role : userRoles) {
					if (objectMethodSet[ROLE_LEVEL][POSITIVE_SET]
							.contains(new ObjectMethodAccess(role, object, method)))
						return true;
					if (objectMethodSet[ROLE_LEVEL][NEGATIVE_SET]
							.contains(new ObjectMethodAccess(role, object, method)))
						unauthorized = true;

					if (methodSet[ROLE_LEVEL][POSITIVE_SET].contains(new MethodAccess(role, method)))
						return true;
					if (methodSet[ROLE_LEVEL][NEGATIVE_SET].contains(new MethodAccess(role, method)))
						unauthorized = true;

					if (objectSet[ROLE_LEVEL][POSITIVE_SET].contains(new ObjectAccess(role, object)))
						return true;
					if (objectSet[ROLE_LEVEL][NEGATIVE_SET].contains(new ObjectAccess(role, object)))
						unauthorized = true;

					if (classSet[ROLE_LEVEL][POSITIVE_SET].contains(new ClassAccess(role, object.getClass())))
						return true;
					if (classSet[ROLE_LEVEL][NEGATIVE_SET].contains(new ClassAccess(role, object.getClass())))
						unauthorized = true;
				}
				if (unauthorized)
					return false;
			}

			return defaultAuthorization;
		}
	}

	/**
	 * Object-Method authorization
	 * 
	 * @author Salvatore Giampa'
	 *
	 */
	private static class ObjectMethodAccess implements Serializable {
		private static final long serialVersionUID = -8218248533055503132L;
		String id;
		Object object;
		Method method;

		public ObjectMethodAccess(String id, Object object, Method method) {
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
			ObjectMethodAccess other = (ObjectMethodAccess) obj;
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
	private static class MethodAccess implements Serializable {
		private static final long serialVersionUID = -6833607610910292885L;
		String id;
		Method method;

		public MethodAccess(String id, Method method) {
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
			MethodAccess other = (MethodAccess) obj;
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
	private static class ObjectAccess implements Serializable {
		private static final long serialVersionUID = 1004449160898466928L;
		String id;
		Object object;

		public ObjectAccess(String id, Object object) {
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
			ObjectAccess other = (ObjectAccess) obj;
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
	private static class ClassAccess implements Serializable {
		private static final long serialVersionUID = 52120543231718091L;
		String id;
		Class<?> cls;

		public ClassAccess(String id, Class<?> cls) {
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
			ClassAccess other = (ClassAccess) obj;
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
