package agilermi.core;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import agilermi.classloading.ClassLoaderFactory;
import agilermi.classloading.URLClassLoaderFactory;

public class RmiClassLoader extends ClassLoader implements Closeable {
	private Map<URL, ClassLoader> urlLoaders = new HashMap<>();
	private Map<URL, Boolean> codebaseRefAdded = new HashMap<>();
	private ClassLoaderFactory classLoaderFactory;
	private RmiRegistry rmiRegistry;
	private boolean closed = false;

	public RmiClassLoader(ClassLoaderFactory classLoaderFactory, RmiRegistry rmiRegistry) {
		super(ClassLoader.getSystemClassLoader());
		this.rmiRegistry = rmiRegistry;
		if (classLoaderFactory != null)
			this.classLoaderFactory = classLoaderFactory;
		else
			this.classLoaderFactory = new URLClassLoaderFactory();
	}

	public synchronized void addURL(URL url) {
		if (urlLoaders.containsKey(url))
			return;
		ClassLoader urlLoader = classLoaderFactory.createClassLoader(url);
		urlLoaders.put(url, urlLoader);
		codebaseRefAdded.put(url, false);
	}

	public synchronized Set<URL> getURLs() {
		return Collections.unmodifiableSet(urlLoaders.keySet());
	}

	@Override
	public Class<?> findClass(String name) throws ClassNotFoundException {
		for (URL url : urlLoaders.keySet()) {
			ClassLoader urlLoader = urlLoaders.get(url);
			try {
				Class<?> cls = urlLoader.loadClass(name);
				if (!codebaseRefAdded.get(url)) {
					rmiRegistry.addCodebaseRef(url);
					codebaseRefAdded.put(url, true);
				}
				return cls;
			} catch (ClassNotFoundException e) {
			}
		}
		throw new ClassNotFoundException();
	}

	@Override
	protected void finalize() throws Throwable {
		close();
		for (URL url : urlLoaders.keySet()) {
			if (codebaseRefAdded.get(url))
				rmiRegistry.removeCodebaseRef(url);
		}
		super.finalize();
	}

	@Override
	public void close() {
		if (closed)
			return;
		for (ClassLoader urlLoader : urlLoaders.values()) {
			try {
				if (urlLoader instanceof Closeable)
					((Closeable) urlLoader).close();
			} catch (IOException e) {
			}
		}
		closed = true;
	}
}
