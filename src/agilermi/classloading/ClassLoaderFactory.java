package agilermi.classloading;

import java.net.URL;

import agilermi.core.RmiRegistry;

/**
 * This class is used to define a proper class loading strategy for the target
 * platform and to obtain a functional weak code mobility at the RMI protocol
 * level. The actual instances of this interfaces can be passed to the
 * {@link RmiRegistry.Builder} to customize the class loading process when
 * receiving remote objects whose code is not known in the local environment. By
 * default, {@link RmiRegistry} uses the {@link URLClassLoaderFactory} to
 * download code from remote codebases.
 * 
 * Some platform, that implements its own Java Virtual Machine, needs different
 * class loading mechanisms that do more operations than just loading standard
 * jar or class files. These mechanisms can be implemented in a custom
 * {@link ClassLoader} whose new instances can be returned by the
 * {@link #createClassLoader(URL)} method of this interface.
 * 
 * @author Salvatore Giampa'
 *
 */
public interface ClassLoaderFactory {

	/**
	 * Create a new instance of the {@link ClassLoader} implementation.
	 * 
	 * @param url the URL of the codebase whose code must be loaded
	 * @return a new {@link ClassLoader} instance
	 */
	public ClassLoader createClassLoader(URL url, ClassLoader parent);
}
