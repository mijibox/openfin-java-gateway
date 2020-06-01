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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A messaging bus that allows for pub/sub messaging between different applications.
 * @author Anthony
 *
 */
public class OpenFinInterApplicationBus {
	private final static Logger logger = LoggerFactory.getLogger(OpenFinInterApplicationBus.class);

	private OpenFinConnection connection;
	private ConcurrentHashMap<String, CopyOnWriteArrayList<OpenFinIabMessageListener>> listenerMap;

	OpenFinInterApplicationBus(OpenFinConnection connection) {
		this.connection = connection;
		this.listenerMap = new ConcurrentHashMap<>();
	}

	/**
	 * Publishes a message to all applications running on OpenFin Runtime that are subscribed to the specified topic.
	 * @param topic The topic on which the message is sent
	 * @param message The message to be published. 
	 * @return the new CompletionStage
	 */
	public CompletionStage<Void> publish(String topic, JsonObject message) {
		JsonObject payload = Json.createObjectBuilder().add("topic", topic)
				.add("message", message).build();
		return this.connection.sendMessage("publish-message", payload).thenAcceptAsync(ack -> {
			if (!ack.getBoolean("success", false)) {
				throw new RuntimeException("error publish, reason: " + ack.getString("reason"));
			}
		});
	}

	/**
	 * Sends a message to a specific application on a specific topic.
	 * @param destionation The identity of the application to which the message is sent
	 * @param topic The topic on which the message is sent
	 * @param message The message to be sent. 
	 * @return the new CompletionStage
	 */
	public CompletionStage<Void> send(JsonObject destionation, String topic, JsonValue message) {
		JsonObject payload = Json.createObjectBuilder()
				.add("destinationUuid", destionation.getString("uuid"))
				.add("destinationWindowName", destionation.getString("name"))
				.add("topic", topic)
				.add("message", message).build();
		return this.connection.sendMessage("send-message", payload).thenAcceptAsync(ack -> {
			if (!ack.getBoolean("success", false)) {
				throw new RuntimeException("error send, reason: " + ack.getString("reason"));
			}
		});
	}

	/**
	 * Subscribes to messages from the specified application on the specified topic.
	 * @param source Source identity
	 * @param topic The topic on which the message is sent
	 * @param listener The listener that is called when a message has been received
	 * @return the new CompletionStage
	 */
	public CompletionStage<Void> subscribe(JsonObject source, String topic, OpenFinIabMessageListener listener) {
		source = source == null ? JsonValue.EMPTY_JSON_OBJECT : source;
		String uuid = source.getString("uuid", "*");
		String name = source.getString("name", "*");
		String key = this.getSubscriptionKey(uuid, name, topic);
		CopyOnWriteArrayList<OpenFinIabMessageListener> listeners = this.listenerMap.get(key);
		if (listeners == null) {
			CopyOnWriteArrayList<OpenFinIabMessageListener> existingListener = this.listenerMap.put(key,
					new CopyOnWriteArrayList<>(new OpenFinIabMessageListener[] { listener }));
			if (existingListener == null) {
				// first one, send out the subscription
				JsonObject payload = Json.createObjectBuilder()
						.add("sourceUuid", uuid)
						.add("sourceWindowName", name)
						.add("topic", topic).build();
				return this.connection.sendMessage("subscribe", payload).thenAcceptAsync(ack -> {
					if (!ack.getBoolean("success", false)) {
						throw new RuntimeException(
								"error subscribe, reason: " + ack.getString("reason"));
					}
				});
			}
			else {
				existingListener.add(listener);
				return CompletableFuture.completedStage(null);
			}
		}
		else {
			listeners.add(listener);
			return CompletableFuture.completedStage(null);
		}
	}

	/**
	 * Unsubscribes to messages from the specified application on the specified topic.
	 * @param source Source identity
	 * @param topic The topic on which the message is sent
	 * @param listener the listener previously registered with subscribe()
	 * @return the new CompletionStage
	 */
	public CompletionStage<Void> unsubscribe(JsonObject source, String topic, OpenFinIabMessageListener listener) {
		source = source == null ? JsonValue.EMPTY_JSON_OBJECT : source;
		String uuid = source.getString("uuid", "*");
		String name = source.getString("name", "*");
		String key = this.getSubscriptionKey(uuid, name, topic);
		CopyOnWriteArrayList<OpenFinIabMessageListener> listeners = this.listenerMap.get(key);
		if (listeners != null) {
			boolean removed = listeners.remove(listener);
			if (removed && listeners.size() == 0) {
				//last one, unsubscribe the topic
				JsonObject payload = Json.createObjectBuilder()
						.add("sourceUuid", uuid)
						.add("sourceWindowName", name)
						.add("topic", topic).build();
				return this.connection.sendMessage("unsubscribe", payload).thenAcceptAsync(ack -> {
					if (!ack.getBoolean("success", false)) {
						throw new RuntimeException("error unsubscribe, reason: " + ack.getString("reason"));
					}
				});
			}
			else {
				return CompletableFuture.completedStage(null);
			}
		}
		else {
			//should be error
			return CompletableFuture.completedStage(null);
		}
		
	}

	private String getSubscriptionKey(String uuid, String name, String topic) {
		return uuid + "::" + name + "::" + topic;
	}

	void processMessage(JsonObject payload) {
		// check if it has subsubscribed topic
		String sourceUuid = payload.getString("sourceUuid");
		String sourceWindowName = payload.getString("sourceWindowName");
		String topic = payload.getString("topic");

		JsonObject identity = Json.createObjectBuilder().add("uuid", sourceUuid).add("name", sourceWindowName)
				.build();

		//exact match
		String key = this.getSubscriptionKey(sourceUuid, sourceWindowName, topic);
		this.processMessage(key, identity, payload);
		//wildcard name
		key = this.getSubscriptionKey(sourceUuid, "*", topic);
		this.processMessage(key, identity, payload);
		//wildcard uuid and name
		key = this.getSubscriptionKey("*", "*", topic);
		this.processMessage(key, identity, payload);
	}
	
	void processMessage(String key, JsonObject identity, JsonObject payload) {
		CopyOnWriteArrayList<OpenFinIabMessageListener> listeners = this.listenerMap.get(key);
		if (listeners != null) {
			listeners.forEach(l -> {
				JsonValue msg = payload.get("message");
				try {
					l.onMessage(identity, msg);
				}
				catch (Exception e) {
					logger.error("error invoking IAB message listener", e);
				}
				finally {

				}
			});
		}
	}
	
	public OpenFinConnection getConnection() {
		return this.connection;
	}
}
