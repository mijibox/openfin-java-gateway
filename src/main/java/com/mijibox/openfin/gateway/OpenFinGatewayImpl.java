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

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.http.WebSocket;
import java.net.http.WebSocket.Listener;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenFinGatewayImpl implements OpenFinGateway {
	final static Logger logger = LoggerFactory.getLogger(OpenFinGatewayImpl.class);

	final static String ACTION_ADD_LISTENER = "add-listener";
	final static String ACTION_DELETE = "delete";
	final static String ACTION_ERROR = "error";
	final static String ACTION_INVOKE = "invoke";
	final static String ACTION_PING = "ping";
	final static String ACTION_QUIT = "quit";
	final static String ACTION_REMOVE_LISTENER = "remove-listener";

	// JSON property names
	final static String ACTION = "action";
	final static String ARGUMENTS = "args";
	final static String PROXY_LISTENER_ID = "proxyListenerId";
	final static String PROXY_ID = "proxyObjId";
	final static String PROXY_RESULT_OBJECT = "proxyResult";
	final static String EVENT = "event";
	final static String IAB_TOPIC = "iabTopic";
	final static String MESSAGE_ID = "messageId";
	final static String METHOD = "method";
	final static String PAYLOAD = "payload";
	final static String RESULT = "result";
	final static String LINSTENER_ARG_INDEX = "listenerArgIdx";

	private OpenFinInterApplicationBus iab;
	private JsonObject gatewayIdentity;
	private String topicExec;
	private String topicListener;
	private AtomicInteger messageId;
	private AtomicInteger listenerId;
	private ConcurrentHashMap<Integer, CompletableFuture<JsonObject>> execCorrelationMap;
	private String gatewayId;
	private OpenFinConnection connection;
	private OpenFinGatewayListener gatewayListener;
	private String gatewayScriptUrl;

	public static CompletionStage<? extends OpenFinGateway> newInstance(OpenFinGatewayLauncherImpl launcher,
			OpenFinConnection connection,
			OpenFinGatewayListener listener) {
		return new OpenFinGatewayImpl(null, connection, listener).createGatewayApplication()
				.thenCompose(gateway -> {
					return gateway.init();
				}).thenCompose(gateway -> {
					if (launcher.getManifestUrl() != null) {
						return gateway.startApplication(launcher.getManifestUrl());
					}
					else if (launcher.getStartupApp() != null) {
						return gateway.startApplication(launcher.getStartupApp());
					}
					else {
						return CompletableFuture.completedStage(gateway);
					}
				});
	}

	protected OpenFinGatewayImpl(String appUuid, OpenFinConnection connection, OpenFinGatewayListener listener) {
		this.gatewayId = appUuid;
		this.connection = connection;
		this.gatewayListener = listener;
		this.messageId = new AtomicInteger(0);
		this.listenerId = new AtomicInteger(0);
		this.execCorrelationMap = new ConcurrentHashMap<>();
		this.iab = connection.getInterAppBus();
	}

	@Override
	public String getId() {
		return this.gatewayId;
	}

	private void processIncomingMessage(JsonValue srcIdentity, JsonValue message) {
		JsonObject msg = ((JsonObject) message);
		String action = msg.getString(ACTION);
		CompletableFuture<JsonObject> resultFuture = this.execCorrelationMap.get(msg.getInt(MESSAGE_ID));
		if (ACTION_ERROR.equals(action)) {
			resultFuture.completeExceptionally(new RuntimeException("error: " + msg));
		}
		else {
			resultFuture.complete(msg);
		}
	}

	protected CompletionStage<OpenFinGatewayImpl> init() {
		this.topicExec = gatewayId + "-exec";
		this.topicListener = gatewayId + "-listener";
		if (this.gatewayIdentity == null) {
			this.gatewayIdentity = Json.createObjectBuilder().add("uuid", gatewayId).add("name", gatewayId).build();
		}

		return this.iab.subscribe(this.gatewayIdentity, this.topicExec, (srcIdentity, message) -> {
			processIncomingMessage(srcIdentity, message);
		}).thenCompose(v -> {
			boolean showConsole = Boolean
					.parseBoolean(System.getProperty("com.mijibox.openfin.gateway.showConsole", "false"));
			if (showConsole) {
				return this.invoke("fin.System.showDeveloperTools", gatewayIdentity);
			}
			else {
				return CompletableFuture.completedFuture(null);
			}
		}).thenApply(v -> {
			if (this.gatewayListener != null) {
				this.connection.addWebSocketListener(new Listener() {
					public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
						gatewayListener.onClose();
						return null;
					}

					public void onError(WebSocket webSocket, Throwable error) {
						gatewayListener.onError();
					}
				});
				this.gatewayListener.onOpen(this);
			}
			return this;
		});
	}

	@Override
	public String getGatewayScriptUrl() {
		return this.gatewayScriptUrl;
	}

	Path extractResource(String resource) throws IOException {
		URL gatewayHtml = this.getClass().getClassLoader().getResource(resource);
		String suffix = resource.substring(resource.lastIndexOf(".") - 1);
		// copy the content to temp directory
		ReadableByteChannel readableByteChannel = Channels.newChannel(gatewayHtml.openStream());
		Path tempFile = Files.createTempFile("OpenFinGateway-", suffix);
		FileOutputStream fileOutputStream = new FileOutputStream(tempFile.toFile());
		long size = fileOutputStream.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
		fileOutputStream.close();
		logger.debug("extracted {} to {}, size: {}", resource, tempFile, size);
		return tempFile;
	}

	protected CompletionStage<OpenFinGatewayImpl> startApplication(JsonObject appOpts) {
		return this.invoke("fin.Application.start", appOpts).thenApply(r -> {
			return this;
		});
	}

	protected CompletionStage<OpenFinGatewayImpl> startApplication(String manifestUrl) {
		return this.invoke("fin.Application.startFromManifest", Json.createValue(manifestUrl)).thenApply(r -> {
			return this;
		});
	}

	protected CompletionStage<OpenFinGatewayImpl> createGatewayApplication() {
		try {
			this.gatewayScriptUrl = this.extractResource("gateway.js").toAbsolutePath().toString();
			this.gatewayId = connection.getUuid() + "-gateway";
			this.gatewayIdentity = Json.createObjectBuilder().add("uuid", gatewayId).add("name", gatewayId).build();
			JsonObject appOpts = Json.createObjectBuilder()
					.add("uuid", this.gatewayId)
					.add("name", this.gatewayId)
					.add("url", this.extractResource("gateway.html").toUri().toString())
					.add("autoShow", false)
					.add("preloadScripts", Json.createArrayBuilder()
							.add(Json.createObjectBuilder().add("url", this.gatewayScriptUrl)))
					.build();
			return this.connection.sendMessage("create-application", appOpts).thenCompose(ack -> {
				return this.connection.sendMessage("run-application", this.gatewayIdentity);
			}).thenApply(ack -> {
				if (!ack.getBoolean("success", false)) {
					throw new RuntimeException(
							"error createGatewayApplication, reason: " + ack.getString("reason"));
				}
				else {
					return this;
				}
			});
		}
		catch (IOException e) {
			throw new RuntimeException("error createGatewayApplication", e);
		}
		finally {
		}
	}

	private CompletionStage<JsonObject> sendMessage(String action, JsonValue payload) {
		int msgId = this.messageId.getAndIncrement();
		CompletableFuture<JsonObject> responseFuture = new CompletableFuture<>();
		this.execCorrelationMap.put(msgId, responseFuture);
		JsonObject message = Json.createObjectBuilder().add(MESSAGE_ID, msgId)
				.add(ACTION, action)
				.add(PAYLOAD, payload).build();
		return this.iab.send(this.gatewayIdentity, this.topicExec, message).thenCombineAsync(responseFuture,
				(r1, r2) -> {
					return r2.getJsonObject(PAYLOAD);
				});
	}

	public CompletionStage<Void> ping() {
		long value = System.currentTimeMillis();
		return this.sendMessage(ACTION_PING, Json.createValue(value)).thenAccept(pongMsg -> {
		});
	}

	@Override
	public CompletionStage<InvokeResult> invoke(String method, JsonValue... args) {
		return this.invoke(false, method, args);
	}

	@Override
	public CompletionStage<InvokeResult> invoke(boolean createProxyObject, String method, JsonValue... args) {
		return this.invoke(createProxyObject, null, method, args);
	}

	CompletionStage<InvokeResult> invoke(ProxyObject proxyObject, String method, JsonValue... args) {
		return this.invoke(false, proxyObject, method, args);
	}

	CompletionStage<InvokeResult> invoke(boolean createProxyObject, ProxyObject proxyObject, String method,
			JsonValue... args) {
		JsonObjectBuilder builder = Json.createObjectBuilder()
				.add(PROXY_RESULT_OBJECT, createProxyObject)
				.add(METHOD, method);
		if (proxyObject != null) {
			builder.add(PROXY_ID, proxyObject.getProxyId());
		}
		if (args != null) {
			int lastNonNullIndex = -1;
			for (int i = 0; i < args.length; i++) {
				if (args[i] != null) {
					lastNonNullIndex = i;
				}
			}
			if (lastNonNullIndex >= 0) {
				// anything beyond can be stripped.
				JsonArrayBuilder argsBuilder = Json.createArrayBuilder();
				for (int i = 0; i <= lastNonNullIndex; i++) {
					if (args[i] == null) {
						argsBuilder.addNull();
					}
					else {
						argsBuilder.add(args[i]);
					}
				}
				builder.add(ARGUMENTS, argsBuilder.build());
			}
		}

		return this.sendMessage(ACTION_INVOKE, builder.build()).thenApply(resultObj -> {
			return new InvokeResult(proxyObject, resultObj, this);
		});
	}

	CompletionStage<Void> deleteProxyObject(JsonValue proxyObjId) {
		return this.sendMessage(ACTION_DELETE, proxyObjId).thenAccept(resp -> {
		});
	}

	@Override
	public CompletionStage<Void> addListener(String method, OpenFinEventListener listener) {
		return this.addListener(false, method, listener).thenAccept(r -> {

		});
	}

	@Override
	public CompletionStage<ProxyListener> addListener(boolean createProxyListener, String method,
			OpenFinEventListener listener) {
		return this.addListener(createProxyListener, method, null, listener);
	}

	@Override
	public CompletionStage<Void> addListener(String method, String event, OpenFinEventListener listener) {
		return this.addListener(false, method, event, listener).thenAccept(r -> {

		});
	}

	@Override
	public CompletionStage<ProxyListener> addListener(boolean createProxyListener, String method, String event,
			OpenFinEventListener listener) {
		if (event != null) {
			return this.addListener(createProxyListener, method, listener, 1, Json.createValue(event));
		}
		else {
			return this.addListener(createProxyListener, method, listener, 0);
		}
	}

	@Override
	public CompletionStage<ProxyListener> addListener(boolean createProxyListener, String method,
			OpenFinEventListener listener, int listenerArgIndex, JsonValue... args) {
		return this.addListener(createProxyListener, null, method, listener, listenerArgIndex, args);
	}

	CompletionStage<ProxyListener> addListener(boolean createProxyListener, ProxyObject proxyObject, String method,
			String event, OpenFinEventListener listener) {
		if (event != null) {
			return this.addListener(createProxyListener, proxyObject, method, listener, 1, Json.createValue(event));
		}
		else {
			return this.addListener(createProxyListener, proxyObject, method, listener, 0);
		}
	}

	/**
	 * Add event listener or register action callback.
	 * 
	 * @param createProxyListener true to create proxyListener so it can be
	 *                            referenced later.
	 * @param proxyObject         if not null, then invoke the instance method of
	 *                            this proxyObject, otherwise invoke the static
	 *                            method.
	 * @param method              method name to add the listener
	 * @param listener            the listener to be invoked when it's invoked in
	 *                            OpenFin runtime
	 * @param listenerArgIndex    listener location in the API method.
	 * @param args                arguments supplied for the function.
	 * @return proxyListener object if createProxyListener is set to true and it was
	 *         successfully created in OpenFin runtime.
	 */
	CompletionStage<ProxyListener> addListener(boolean createProxyListener, ProxyObject proxyObject, String method,
			OpenFinEventListener listener, int listenerArgIndex, JsonValue... args) {
		String iabTopic = this.topicListener + "-" + this.listenerId.getAndIncrement();
		OpenFinIabMessageListener iabListener = (src, e) -> {
			// if it expects the listener to return something (channel api registered
			// actions)
			JsonValue actionResult = listener.onEvent((JsonArray) e);
			if (actionResult != null) {
				iab.send(src, iabTopic, actionResult);
			}
		};
		return this.iab.subscribe(this.gatewayIdentity, iabTopic, iabListener).thenCompose(v -> {
			JsonObjectBuilder builder = Json.createObjectBuilder()
					.add(PROXY_RESULT_OBJECT, createProxyListener)
					.add(IAB_TOPIC, iabTopic)
					.add(METHOD, method)
					.add(LINSTENER_ARG_INDEX, listenerArgIndex);
			if (proxyObject != null) {
				builder.add(PROXY_ID, proxyObject.getProxyId());
			}
			if (args != null) {
				int lastNonNullIndex = -1;
				for (int i = 0; i < args.length; i++) {
					if (args[i] != null) {
						lastNonNullIndex = i;
					}
				}
				if (lastNonNullIndex >= 0) {
					// anything beyond can be stripped.
					JsonArrayBuilder argsBuilder = Json.createArrayBuilder();
					for (int i = 0; i <= lastNonNullIndex; i++) {
						if (args[i] == null) {
							argsBuilder.addNull();
						}
						else {
							argsBuilder.add(args[i]);
						}
					}
					builder.add(ARGUMENTS, argsBuilder.build());
				}
			}

			return this.sendMessage(ACTION_ADD_LISTENER, builder.build());
		}).thenApply(result -> {
			if (result.containsKey(PROXY_ID)) {
				ProxyListener proxyListener = new ProxyListener(result.get(PROXY_ID), proxyObject, iabTopic,
						iabListener, this);
				return proxyListener;
			}
			else {
				return null;
			}
		});
	}

	@Override
	public CompletionStage<Void> removeListener(String method, String event, ProxyListener proxyListener) {
		return this.removeInstanceListener(null, method, event, proxyListener);
	}

	CompletionStage<Void> removeInstanceListener(ProxyObject proxyObject, String method, String event,
			ProxyListener proxyListener) {
		return this.iab.unsubscribe(this.gatewayIdentity, proxyListener.getIabTopic(), proxyListener.getListener())
				.thenCompose(v -> {
					JsonObjectBuilder payloadBuilder = Json.createObjectBuilder()
							.add(METHOD, method)
							.add(EVENT, event)
							.add(PROXY_LISTENER_ID, proxyListener.getProxyId());
					if (proxyObject != null) {
						payloadBuilder.add(PROXY_ID, proxyObject.getProxyId());
					}
					return this.sendMessage(ACTION_REMOVE_LISTENER, payloadBuilder.build());
				}).thenCompose(r -> {
					return proxyListener.dispose();
				});
	}

	@Override
	public CompletionStage<OpenFinGateway> close() {
		return this.sendMessage(ACTION_QUIT, JsonValue.EMPTY_JSON_OBJECT).thenApply(o -> {
			this.connection.disconnect();
			return this;
		});
	}

	@Override
	public OpenFinInterApplicationBus getOpenFinInterApplicationBus() {
		return this.iab;
	}

	public CompletionStage<ProxyListener> addEventHandler(Consumer<? extends JsonValue> action) {
		return null;
	}

	@Override
	public CompletionStage<? extends OpenFinGateway> getApplicationGateway(String appUuid) {
		OpenFinGatewayImpl appGateway = new OpenFinGatewayImpl(appUuid, this.connection, null) {
			@Override
			public CompletionStage<OpenFinGateway> close() {
				throw new RuntimeException("invalid operation, unable to close application gateway");
			}
		};
		appGateway.gatewayScriptUrl = this.gatewayScriptUrl;
		return appGateway.init();
	}
}
