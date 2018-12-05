/**
 *  Copyright 2017 Salvatore Giampà
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

package agilermi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * This interface is used to specify a factory for input and output streams that
 * are used on TCP communication in Agile RMI. For example, this interface can
 * be used to add a compress layer to the communication between hosts.
 * 
 * @author Salvatore Giampa'
 *
 */
public interface FilterFactory {

	OutputStream buildOutputStream(OutputStream outputStream) throws IOException;

	InputStream buildInputStream(InputStream inputStream) throws IOException;

}
