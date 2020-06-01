/*
Copyright 2020 MIJI Technology LLC

Licensed under the Apache License, Version 2.0 (the "License");

You may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.mijibox.openfin.gateway;

import java.io.StringReader;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.json.Json;
import javax.json.JsonReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;

public class PosixPortDiscoverer {

	private final static Logger logger = LoggerFactory.getLogger(PosixPortDiscoverer.class);
	private static PosixLibrary cLib;

	static {
		cLib = Native.load("c", PosixLibrary.class);
	}

	PosixPortDiscoverer() {

	}

	static String getNamedPipeFilePath(String pipeName) {
		String tmpDir = System.getProperty("java.io.tmpdir");
		if (!tmpDir.endsWith("/")) {
			tmpDir = tmpDir + "/";
		}
		return tmpDir + "gateway." + pipeName + ".sock";
	}

	CompletionStage<Integer> findPortNumber(String pipeName) {
		String socketName = getNamedPipeFilePath(pipeName);
		return CompletableFuture.supplyAsync(() -> {
			try {
				logger.debug("findPort: {}", socketName);
				int socket = cLib.socket(PosixLibrary.AF_UNIX, PosixLibrary.SOCK_STREAM, PosixLibrary.PROTOCOL);
				SockAddr sockAddr = new SockAddr();
				sockAddr.setPath(socketName);
				cLib.bind(socket, sockAddr, sockAddr.size());
				cLib.listen(socket, 1);
				SockAddr clientAddr = new SockAddr();
				IntByReference addrLen = new IntByReference(0);
				int clientSocket = cLib.accept(socket, clientAddr, addrLen);
				byte[] buffer = new byte[4096];
				int handShakeLength = cLib.read(clientSocket, buffer, buffer.length);
				logger.debug("handShakeLength: {}", handShakeLength);
				ByteBuffer bb = ByteBuffer.wrap(buffer);
				bb.putInt(20, cLib.getpid());
				int length= cLib.write(clientSocket, buffer, handShakeLength);
				logger.debug("wrote length: {}", length);
				length = cLib.read(clientSocket, buffer, buffer.length);
				logger.debug("read length: {}", length);
				String portInfoJson = new String(buffer, handShakeLength, length-handShakeLength);
				logger.debug("portInfo: {}", portInfoJson);
				JsonReader jsonReader = Json.createReader(new StringReader(portInfoJson));
				int port = jsonReader.readObject().getJsonObject("payload").getInt("port");
				cLib.close(socket);
				cLib.unlink(socketName);
				return port;
			}
			catch (Exception e) {
				throw new RuntimeException("unable to get port number", e);
			}
		});
	}

	public static class SockAddr extends Structure {
		public final static int SUN_PATH_SIZE = 108;
		public final static byte[] ZERO_BYTE = new byte[] { 0 };

		public short sun_family = 1;
		public byte[] sun_path = new byte[SUN_PATH_SIZE];

		public void setPath(String sunPath) {
			System.arraycopy(sunPath.getBytes(), 0, this.sun_path, 0, sunPath.length());
			System.arraycopy(ZERO_BYTE, 0, this.sun_path, sunPath.length(), 1);
		}

		@SuppressWarnings({ "rawtypes", "unchecked" })
		@Override
		protected List getFieldOrder() {
			String[] fields = new String[] { "sun_family", "sun_path" };
			return Arrays.asList(fields);
		}
	}

	interface PosixLibrary extends Library {
		static final int AF_UNIX = 1;
		static final int SOCK_STREAM = 1;
		static final int PROTOCOL = 0;

		public int bind(int socket, SockAddr sockAddr, int addrLen);

		public int listen(int socket, int queue);

		public int accept(int socket, SockAddr sockAddr, IntByReference addrLen);

		public int read(int socket, byte[] buffer, int length);

		public int write(int socket, byte[] buffer, int length);

		public int unlink(String name);

		public int socket(int domain, int type, int protocol);

		public int close(int fd);

		public int getpid();
	}
}
