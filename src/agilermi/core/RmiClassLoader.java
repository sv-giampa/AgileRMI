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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.WeakHashMap;

import agilermi.codemobility.ClassLoaderFactory;

/**
 * The instance of this class associated to a {@link RmiRegistry} can be used to
 * load classes located on remote codebases.
 * 
 * @author Salvatore Giampa'
 *
 */
public final class RmiClassLoader extends ClassLoader {
	private int modificationNumber = 0;
	private Map<URL, ClassLoader> staticCodebases = new HashMap<>();
	private WeakHashMap<ClassLoader, URL> activeCodebases = new WeakHashMap<>();
	private ClassLoaderFactory classLoaderFactory;

	RmiClassLoader(Set<URL> staticCodebases, ClassLoaderFactory classLoaderFactory) {
		super(ClassLoader.getSystemClassLoader());
		this.classLoaderFactory = classLoaderFactory;

		for (URL url : staticCodebases) {
			ClassLoader classLoader = classLoaderFactory.createClassLoader(url, ClassLoader.getSystemClassLoader());
			this.staticCodebases.put(url, classLoader);
			this.activeCodebases.put(classLoader, url);
		}
		modificationNumber++;
	}

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
	 * Remove a codebase that is statically loaded in this RMI environment
	 * 
	 * @param url the url of the codebase to remove
	 */
	public void removeCodebase(URL url) {
		staticCodebases.remove(url);
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
