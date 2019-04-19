package agilermi.core;

import java.io.Closeable;
import java.net.URL;

import agilermi.classloading.ClassLoaderFactory;

class CodebaseClassLoader extends ClassLoader {
	private ClassLoader classLoader;
	private URL url;

	public CodebaseClassLoader(ClassLoaderFactory classLoaderFactory, URL url) {
		classLoader = classLoaderFactory.createClassLoader(url, ClassLoader.getSystemClassLoader());
		this.url = url;
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		return classLoader.loadClass(name);
	}

	@Override
	protected void finalize() throws Throwable {
		System.out.println("class loader finalized: " + url);
		if (classLoader instanceof Closeable)
			((Closeable) classLoader).close();
		super.finalize();
	}
}
