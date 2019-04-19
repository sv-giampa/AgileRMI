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

	@Override
	public ClassLoader createClassLoader(URL url, ClassLoader parent) {
		return URLClassLoader.newInstance(new URL[] { url }, parent);
	}

}
