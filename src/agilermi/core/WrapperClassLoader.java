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

import java.io.Closeable;
import java.net.URL;

import agilermi.codemobility.ClassLoaderFactory;

final class WrapperClassLoader extends ClassLoader {
	private ClassLoader classLoader;
	private URL url;

	public WrapperClassLoader(ClassLoaderFactory classLoaderFactory, URL url) {
		classLoader = classLoaderFactory.createClassLoader(url, ClassLoader.getSystemClassLoader());
		this.url = url;
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		Class<?> cls = classLoader.loadClass(name);
		if (Debug.CLASSLOADERS)
			System.out.printf("[WrapperClassLoader] loaded class %s from codesource %s\n", cls.getName(),
					cls.getProtectionDomain().getCodeSource());
		return cls;
	}

	@Override
	protected void finalize() throws Throwable {
		if (Debug.CLASSLOADERS)
			System.out.println("[WrapperClassLoader.finalize()] class loader finalized: " + url);
		if (classLoader instanceof Closeable)
			((Closeable) classLoader).close();
		super.finalize();
	}
}
