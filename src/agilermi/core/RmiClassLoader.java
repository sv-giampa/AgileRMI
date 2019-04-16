package agilermi.core;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class RmiClassLoader extends ClassLoader implements Closeable {
	private Map<URL, URLClassLoader> urlLoaders = new HashMap<>();

	public RmiClassLoader(ClassLoader parent) {
		super(parent);
		// System.out.println("RmiClassLoader created!");
	}

	public synchronized void addURL(URL url) {
		if (urlLoaders.containsKey(url))
			return;
		URLClassLoader urlLoader = URLClassLoader.newInstance(new URL[] { url }, super.getParent());
		urlLoaders.put(url, urlLoader);
	}

	public synchronized void removeURL(URL url) {
		if (!urlLoaders.containsKey(url))
			return;

		try {
			urlLoaders.remove(url).close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void removeAllURLs() {
		for (URLClassLoader urlLoader : urlLoaders.values()) {
			try {
				urlLoader.close();
			} catch (IOException e) {
			}
		}
		urlLoaders.clear();
	}

	public synchronized Set<URL> getURLs() {
		return Collections.unmodifiableSet(urlLoaders.keySet());
	}

	@Override
	public Class<?> findClass(String name) throws ClassNotFoundException {
		for (URLClassLoader urlLoader : urlLoaders.values()) {
			try {
				return urlLoader.loadClass(name);
			} catch (ClassNotFoundException e) {
			}
		}
		throw new ClassNotFoundException();
	}

	@Override
	protected void finalize() throws Throwable {
		removeAllURLs();
		super.finalize();
		// System.out.println("RmiClassLoader finalized!");
	}

	@Override
	public void close() {
		removeAllURLs();
	}
}
