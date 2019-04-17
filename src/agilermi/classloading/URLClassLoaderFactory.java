package agilermi.classloading;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * This class is the default implementation of the {@link ClassLoaderFactory}
 * interface that creates {@link URLClassLoader} instances that are fully
 * supported on standard Java Virtual Machines.
 * 
 * @author Salvatore Giampa'
 *
 */
public class URLClassLoaderFactory implements ClassLoaderFactory {
	private ClassLoader parent;

	/**
	 * Construct an instance with the system class loader as parent (See
	 * {@link ClassLoader#getSystemClassLoader()})
	 */
	public URLClassLoaderFactory() {
		parent = ClassLoader.getSystemClassLoader();
	}

	/**
	 * Construct an instance with the given parent.
	 */
	public URLClassLoaderFactory(ClassLoader parent) {
		this.parent = parent;
	}

	@Override
	public ClassLoader createClassLoader(URL url) {
		return URLClassLoader.newInstance(new URL[] { url }, parent);
	}

}
