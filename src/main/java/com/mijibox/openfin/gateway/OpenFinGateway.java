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

import java.util.concurrent.CompletionStage;

import javax.json.JsonValue;

public interface OpenFinGateway {

	interface OpenFinGatewayListener {
		default void onOpen(OpenFinGateway gateway) {
		};

		default void onError() {
		};

		default void onClose() {
		};
	}
	
	String getId();

	CompletionStage<InvokeResult> invoke(String method);

	CompletionStage<InvokeResult> invoke(String method, JsonValue... args);

	CompletionStage<InvokeResult> invoke(boolean createProxyObject, String method, JsonValue... args);

	CompletionStage<ProxyListener> addListener(String method, OpenFinEventListener listener);

	CompletionStage<ProxyListener> addListener(boolean createProxyListener, String method, OpenFinEventListener listener);

	CompletionStage<ProxyListener> addListener(String method, String event, OpenFinEventListener listener);

	CompletionStage<ProxyListener> addListener(boolean createProxyListener, String method, String event, OpenFinEventListener listener);

	CompletionStage<ProxyListener> addListener(boolean createProxyListener, String method, OpenFinEventListener listener, int listenerArgIdx, JsonValue... args);

	CompletionStage<Void> removeListener(String method, String event, ProxyListener listener);

	CompletionStage<OpenFinGateway> close();

	OpenFinInterApplicationBus getOpenFinInterApplicationBus();
}
