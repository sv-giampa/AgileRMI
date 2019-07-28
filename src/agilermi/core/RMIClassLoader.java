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
package agilermi.core;

import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.WeakHashMap;

import agilermi.codemobility.ClassLoaderFactory;

/**
 * The instance of this class associated to a {@link RMIRegistry} can be used to
 * load classes located on remote codebases.
 * 
 * @author Salvatore Giampa'
 *
 */
public final class RMIClassLoader extends ClassLoader {
	private int modificationNumber = 0;
	private Map<URL, ClassLoader> staticCodebases = new HashMap<>();
	private WeakHashMap<ClassLoader, URL> activeCodebases = new WeakHashMap<>();
	private ClassLoaderFactory classLoaderFactory;

	RMIClassLoader(Set<URL> staticCodebases, ClassLoaderFactory classLoaderFactory) {
		super(ClassLoader.getSystemClassLoader());
		this.classLoaderFactory = classLoaderFactory;

		for (URL url : staticCodebases) {
			ClassLoader classLoader = classLoaderFactory.createClassLoader(url, ClassLoader.getSystemClassLoader());
			this.staticCodebases.put(url, classLoader);
			this.activeCodebases.put(classLoader, url);
		}
		modificationNumber++;
	}

	/**
	 * Adds a codebase where code of the local application can be loaded. The
	 * accepted URLs (e.g. that use specific protocols) depend by the underlyng
	 * {@link ClassLoaderFactory} used on {@link RMIRegistry} initialization. See
	 * the {@link RMIRegistry.Builder#setClassLoaderFactory(ClassLoaderFactory)}
	 * method.
	 * 
	 * @param url the codebase url
	 */
	public void addCodebase(URL url) {
		if (staticCodebases.containsKey(url))
			return;
		if (activeCodebases.values().contains(url)) {
			for (Entry<ClassLoader, URL> entry : activeCodebases.entrySet())
				if (entry.getValue().equals(url)) {
					staticCodebases.put(entry.getValue(), entry.getKey());
					break;
				}
		} else {
			ClassLoader classLoader = classLoaderFactory.createClassLoader(url, ClassLoader.getSystemClassLoader());
			this.staticCodebases.put(url, classLoader);
			this.activeCodebases.put(classLoader, url);
		}
		modificationNumber++;
	}

	/**
	 * Remove a codebase that is statically loaded in this RMI environment (A
	 * codebase that contains the code of the local application). This method cannot
	 * remove remotely loaded codebases.
	 * 
	 * @param url the url of the codebase to remove
	 */
	public void removeCodebase(URL url) {
		removeCodebases(url);
	}

	/**
	 * Remove a codebase that is statically loaded in this RMI environment (A
	 * codebase that contains the code of the local application). This method cannot
	 * remove remotely loaded codebases.
	 * 
	 * @param url the url of the codebase to remove
	 */
	public void removeCodebases(Collection<URL> url) {
		removeCodebases(url.toArray(new URL[0]));
	}

	/**
	 * Remove a codebase that is statically loaded in this RMI environment (A
	 * codebase that contains the code of the local application). This method cannot
	 * remove remotely loaded codebases.
	 * 
	 * @param urls the urls of the code-bases to remove
	 */
	public void removeCodebases(URL... urls) {
		for (URL url : urls)
			staticCodebases.remove(url);
		modificationNumber += urls.length;
		System.gc();
	}

	/**
	 * Called by the {@link RMIObjectInputStream} when a new class loader is
	 * activated for a remote codebase (A class was downloaded from a remote
	 * codebase).
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
	 * the codebase-set is done on this {@link RMIClassLoader}. This method is used
	 * by {@link RMIHandler} to decide when the set of the local active codebases
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
	public synchronized Set<URL> getCodebasesSet() {
		return new HashSet<>(activeCodebases.values());
	}

	/**
	 * Remove all statically known codebases.<br>
	 * This method cannot remove instantly the static codebases whose code is
	 * currently loaded and used in the Java Virtual Machine.
	 */
	public void clearCodebasesSet() {
		staticCodebases.clear();
	}
}
