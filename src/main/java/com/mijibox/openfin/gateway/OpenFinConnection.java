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

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocket.Listener;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenFinConnection implements Listener {
	private final static Logger logger = LoggerFactory.getLogger(OpenFinConnection.class);

	private StringBuilder receivedMessage;
	private CompletableFuture<?> accumulatedMessage;
	private AtomicInteger messageId;
	private ConcurrentHashMap<Integer, CompletableFuture<JsonObject>> ackMap;
	private int port;
	private WebSocket webSocket;
	private String uuid;
	private CompletableFuture<OpenFinConnection> authFuture;
	private ExecutorService processMessageThreadPool;
	private OpenFinInterApplicationBus interAppBus;
	private List<Listener> webSocketListeners;

	private boolean connected;

	public OpenFinConnection(int port) {
		this(UUID.randomUUID().toString(), port);
	}

	public OpenFinConnection(String uuid, int port) {
		this.uuid = uuid;
		this.port = port;
		this.receivedMessage = new StringBuilder();
		this.ackMap = new ConcurrentHashMap<>();
		this.accumulatedMessage = new CompletableFuture<>();
		this.messageId = new AtomicInteger(0);
		this.authFuture = new CompletableFuture<>();
		this.processMessageThreadPool = Executors.newFixedThreadPool(10);
		this.interAppBus = new OpenFinInterApplicationBus(this);
		this.webSocketListeners = new ArrayList<>();
	}

	public String getUuid() {
		return this.uuid;
	}

	public boolean isConnected() {
		return this.connected;
	}

	public CompletionStage<OpenFinConnection> connect() {
		try {
			String endpointURI = "ws://localhost:" + this.port + "/";
			logger.debug("connecting to {}", endpointURI);
			HttpClient httpClient = HttpClient.newHttpClient();
			httpClient.newWebSocketBuilder().buildAsync(new URI(endpointURI), this);
		}
		catch (URISyntaxException e) {
			e.printStackTrace();
		}
		finally {
		}
		return this.authFuture;
	}

	public void disconnect() {
		this.webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "normal closure");
	}
	
	private String getPackageVersion() {
		String v = "N/A";
		try {
			URL manifest = this.getClass().getClassLoader().getResource("META-INF/maven/com.mijibox.openfin/openfin-gateway/pom.properties");
			Properties prop = new Properties();
			InputStream inputStream = manifest.openStream();
			prop.load(inputStream);
			v = prop.getProperty("version");
			inputStream.close();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			
		}
		return v;
	}

	@Override
	public void onOpen(WebSocket webSocket) {
		this.connected = true;
		webSocket.request(1);
		this.webSocket = webSocket;
		logger.debug("websocket connected");
		for (Listener l : this.webSocketListeners) {
			try {
				l.onOpen(webSocket);
			}
			catch (Exception e) {
				logger.error("error invoking socket listener", e);
			}
		}

		JsonObject authPayload = Json.createObjectBuilder().add("action", "request-external-authorization")
				.add("payload", Json.createObjectBuilder()
						.add("uuid", this.uuid)
						.add("type", "file-token")
						.add("client", Json.createObjectBuilder()
								.add("type", "java")
								.add("version", getPackageVersion()).build())
						.build())
				.build();

		this.webSocket.sendText(authPayload.toString(), true);
	}

	@Override
	public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
		receivedMessage.append(data);
		webSocket.request(1);
		if (last) {
			String messageString = receivedMessage.toString();
			this.processMessageThreadPool.submit(() -> {
				processMessage(messageString);
			});
			receivedMessage = new StringBuilder();
			accumulatedMessage.complete(null);
			CompletionStage<?> cf = accumulatedMessage;
			accumulatedMessage = new CompletableFuture<>();
			return cf;
		}
		return accumulatedMessage;
	}

	@Override
	public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
		logger.debug("websocket closed, statusCode: {}, reason: {}", statusCode, reason);
		this.connected = false;
		for (Listener l : this.webSocketListeners) {
			try {
				l.onClose(webSocket, statusCode, reason);
			}
			catch (Exception e) {
				logger.error("error invoking socket listener", e);
			}
		}
		this.processMessageThreadPool.shutdown();
		return null;
	}

	@Override
	public void onError(WebSocket webSocket, Throwable error) {
		logger.debug("websocket error", error);
		this.connected = false;
		for (Listener l : this.webSocketListeners) {
			try {
				l.onError(webSocket, error);
			}
			catch (Exception e) {
				logger.error("error invoking socket listener", e);
			}
		}
		this.processMessageThreadPool.shutdown();
	}

	public synchronized CompletionStage<JsonObject> sendMessage(String action, JsonObject payload) {
		if (this.connected) {
			int msgId = this.messageId.getAndIncrement();
			CompletableFuture<JsonObject> ackFuture = new CompletableFuture<>();
			this.ackMap.put(msgId, ackFuture);
			JsonObjectBuilder json = Json.createObjectBuilder();
			JsonObject msgJson = json.add("action", action)
					.add("messageId", msgId)
					.add("payload", payload).build();
			String msg = msgJson.toString();
			logger.debug("sending: {}", msg);

			try {
				this.webSocket.sendText(msg, true)
						.exceptionally(e -> {
							logger.error("error sending message over websocket", e);
							return null;
						}).toCompletableFuture().get();
			}
			catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}

			return ackFuture;
		}
		else {
			throw new RuntimeException("not connected");
		}
	}

	private void processMessage(String message) {
		logger.debug("processMessage: {}", message);
		JsonReader jsonReader = Json.createReader(new StringReader(message));
		JsonObject receivedJson = jsonReader.readObject();
		String action = receivedJson.getString("action");
		JsonObject payload = receivedJson.getJsonObject("payload");
		if ("external-authorization-response".equals(action)) {
			String file = payload.getString("file");
			String token = payload.getString("token");
			try {
				Files.write(Paths.get(file), token.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.WRITE,
						StandardOpenOption.TRUNCATE_EXISTING);
				JsonObject requestAuthPayload = Json.createObjectBuilder()
						.add("uuid", this.uuid)
						.add("type", "file-token").build();
				this.sendMessage("request-authorization", requestAuthPayload);
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}
		else if ("authorization-response".equals(action)) {
			this.authFuture.complete(this);
		}
		else if ("ack".equals(action)) {
			int correlationId = receivedJson.getInt("correlationId");
			CompletableFuture<JsonObject> ackFuture = this.ackMap.remove(correlationId);
			if (ackFuture == null) {
				logger.error("missing ackFuture, correlationId={}", correlationId);
			}
			else {
				ackFuture.complete(payload);
			}
		}
		else if ("process-message".equals(action)) {
			this.interAppBus.processMessage(payload);
		}
		else {

		}
	}

	public OpenFinInterApplicationBus getInterAppBus() {
		return this.interAppBus;
	}

	void addWebSocketListener(Listener listener) {
		this.webSocketListeners.add(listener);
	}

	void removeWebSocketListenner(Listener listener) {
		this.webSocketListeners.remove(listener);
	}
}
