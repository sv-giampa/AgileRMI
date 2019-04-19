package agilermi.core;

import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

import agilermi.classloading.ClassLoaderFactory;

/**
 * The instance of this class associated to a {@link RmiRegistry} can be used to
 * load classes located on remote codebases.
 * 
 * @author Salvatore Giampa'
 *
 */
public final class RmiClassLoader extends ClassLoader {
	private int modificationNumber = 0;
	private ClassLoader[] staticCodebases;
	private WeakHashMap<ClassLoader, URL> activeCodebases = new WeakHashMap<>();

	RmiClassLoader(Set<URL> staticCodebases, ClassLoaderFactory classLoaderFactory) {
		super(ClassLoader.getSystemClassLoader());
		this.staticCodebases = new ClassLoader[staticCodebases.size()];
		int index = 0;
		for (URL codebase : staticCodebases) {
			this.staticCodebases[index] = classLoaderFactory.createClassLoader(codebase,
					ClassLoader.getSystemClassLoader());
			this.activeCodebases.put(this.staticCodebases[index], codebase);
			index++;
		}
		modificationNumber++;
	}

	/**
	 * Called by the {@link RmiObjectInputStream} when a new class loader is
	 * activated for a remote codebase.
	 * 
	 * @param url         the codebase url
	 * @param classLoader the codebase class loader
	 */
	synchronized void addActiveCodebase(URL url, ClassLoader classLoader) {
		activeCodebases.put(classLoader, url);
		modificationNumber++;
	}

	/**
	 * Gets the modification number that is incremented every time a new change to
	 * the codebase set is done on this {@link RmiClassLoader}. This method is used
	 * by {@link RmiHandler} to decide when the set of the local active codebases
	 * should be sent to the remote peer.
	 * 
	 * @return the number of modifications apported to this class loader
	 */
	int getModificationNumber() {
		return modificationNumber;
	}

	@Override
	protected synchronized Class<?> findClass(String name) throws ClassNotFoundException {
		try {
			return ClassLoader.getSystemClassLoader().loadClass(name);
		} catch (ClassNotFoundException e) {
		}
		for (ClassLoader classLoader : activeCodebases.keySet()) {
			try {
				return classLoader.loadClass(name);
			} catch (ClassNotFoundException e) {
			}
		}
		throw new ClassNotFoundException();
	}

	/**
	 * Gets the set of currently active codebases.
	 * 
	 * @return a set of active codebases
	 */
	public synchronized Set<URL> getCodebases() {
		return new HashSet<>(activeCodebases.values());
	}
}
