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

/**
 * Provides classes and interfaces used to access byte streams through RMI. For
 * example, a {@link java.io.FileOutputStream FileOutputStream} can be exposed
 * as a remote object.<br>
 * <br>
 * To expose an {@link java.io.OutputStream OutputStream} instance on RMI,
 * transforming it to a remote object do the following:<br>
 * <ol>
 * <li>build a new {@link agilermi.utility.bytestream.StreamToRemoteOutput
 * StreamToRemoteOutput} instance passing the {@link java.io.OutputStream} to
 * its constructor;
 * <li>publish or share the pointer to the
 * {@link agilermi.utility.bytestream.StreamToRemoteOutput
 * StreamToRemoteOutput};
 * <li>On the remote side, yoou can transform the
 * {@link agilermi.utility.bytestream.RemoteOutput RemoteOutput} instance to an
 * {@link java.io.OutputStream OutputStream} building a
 * {@link agilermi.utility.bytestream.RemoteOutputToStream
 * RemoteOutputToStream}.
 * <li>Now you can use the remote reference to your {@link java.io.OutputStream
 * OutputStream} as any other stream (e.g. decorating it with
 * {@link java.io.BufferedOutputStream BufferedOutputStream} and similar
 * decorators).
 * </ol>
 * <br>
 * You should follow this same way for {@link java.io.InputStream InputStreams},
 * by using {@link agilermi.utility.bytestream.StreamToRemoteInput
 * StreamToRemoteInput} and
 * {@link agilermi.utility.bytestream.RemoteInputToStream RemoteInputToStream}
 * classes.
 */
package agilermi.utility.bytestream;
