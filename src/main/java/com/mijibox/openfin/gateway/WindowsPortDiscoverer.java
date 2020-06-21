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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.json.Json;
import javax.json.JsonReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;

public class WindowsPortDiscoverer {

	private final static Logger logger = LoggerFactory.getLogger(WindowsPortDiscoverer.class);

	public WindowsPortDiscoverer() {
	}

	public CompletionStage<Integer> findPortNumber(String connectionUuid) {
		return CompletableFuture.supplyAsync(() -> {
			String namedPipeName = "\\\\.\\pipe\\chrome." + connectionUuid;
			logger.debug("creating named pipe: {}", namedPipeName);
			HANDLE hNamedPipe = Kernel32.INSTANCE.CreateNamedPipe(namedPipeName, WinBase.PIPE_ACCESS_DUPLEX, // dwOpenMode
					WinBase.PIPE_TYPE_BYTE | WinBase.PIPE_READMODE_BYTE | WinBase.PIPE_WAIT, // dwPipeMode
					1, // nMaxInstances,
					Byte.MAX_VALUE, // nOutBufferSize,
					Byte.MAX_VALUE, // nInBufferSize,
					1000, // nDefaultTimeOut,
					null); // lpSecurityAttributes

			if (Kernel32.INSTANCE.ConnectNamedPipe(hNamedPipe, null)) {
				logger.debug("connected to named pipe {}", namedPipeName);
			}

			byte[] buffer = new byte[4096];
			IntByReference lpNumberOfBytesRead = new IntByReference(0);
			Kernel32.INSTANCE.ReadFile(hNamedPipe, buffer, buffer.length, lpNumberOfBytesRead, null);

			ByteBuffer bb = ByteBuffer.wrap(buffer);
			bb.putInt(20, Kernel32.INSTANCE.GetCurrentProcessId());
			IntByReference lpNumberOfBytesWrite = new IntByReference(0);
			Kernel32.INSTANCE.WriteFile(hNamedPipe, buffer, buffer.length, lpNumberOfBytesWrite, null);
			Kernel32.INSTANCE.ReadFile(hNamedPipe, buffer, buffer.length, lpNumberOfBytesRead, null);
			logger.debug("read port info from named pipe, size:{}", lpNumberOfBytesRead.getValue());
			String portInfoJson = new String(buffer, 24, lpNumberOfBytesRead.getValue() - 24);
			logger.debug("portInfo: {}", portInfoJson);
			JsonReader jsonReader = Json.createReader(new StringReader(portInfoJson));
			int port = jsonReader.readObject().getJsonObject("payload").getInt("port");
			logger.debug("port number: {}", port);
			Kernel32.INSTANCE.CloseHandle(hNamedPipe);
			return port;
		});
	}
}
