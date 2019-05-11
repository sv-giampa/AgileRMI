/**
 *  Copyright 2018-2019 Salvatore Giampà
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
package agilermi.codemobility;

import java.net.URL;

import agilermi.core.RMIRegistry;

/**
 * This class is used to define a proper class loading strategy for the target
 * platform and to obtain a functional weak code mobility at the RMI protocol
 * level. The actual instances of this interfaces can be passed to the
 * {@link RMIRegistry.Builder} to customize the class loading process when
 * receiving remote objects whose code is not known in the local environment. By
 * default, {@link RMIRegistry} uses the {@link URLClassLoaderFactory} to
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
