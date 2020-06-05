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

import javax.json.JsonObject;
import javax.json.JsonValue;

public class ProxyObject {
	JsonValue proxyObjId;
	OpenFinGatewayImpl apiGateway;
	ProxyObject invoker;
	JsonObject jsonObject;

	ProxyObject(JsonValue proxyObjId, JsonObject jsonObject, ProxyObject invoker, OpenFinGatewayImpl apiGateway) {
		this.proxyObjId = proxyObjId;
		this.jsonObject = jsonObject;
		this.invoker = invoker;
		this.apiGateway = apiGateway;
	}
	
	public ProxyObject getInvoker() {
		return this.invoker;
	}
	
	public CompletionStage<InvokeResult> invoke(String method) {
		return this.apiGateway.invoke(this, method);
	}
	
	public CompletionStage<InvokeResult> invoke(String method, JsonValue... args) {
		return this.apiGateway.invoke(this, method, args);
	}

	public CompletionStage<InvokeResult> invoke(boolean createProxyObject, String method, JsonValue... args) {
		return this.apiGateway.invoke(createProxyObject, this, method, args);
	}
	
	public CompletionStage<ProxyListener> addListener(String method, String event, OpenFinEventListener listener) {
		return this.apiGateway.addInstanceListener(true, this, method, event, listener);
	}
	
	public CompletionStage<ProxyListener> addListener(boolean createProxyListener, String method, String event, OpenFinEventListener listener) {
		return this.apiGateway.addInstanceListener(createProxyListener, this, method, event, listener);
	}

	public CompletionStage<ProxyListener> addListener(boolean createProxyListener, String method, OpenFinActionListener listener, JsonValue... args) {
		return this.apiGateway.addInstanceActionListener(createProxyListener, this, method, listener, 1, args);
	}

	public CompletionStage<Void> removeListener(String method, String event, ProxyListener listener) {
		return this.apiGateway.removeInstanceListener(this, method, event, listener);
	}

	public CompletionStage<Void> dispose() {
		return this.apiGateway.deleteProxyObject(this.proxyObjId);
	}

	public JsonValue getProxyObjectId() {
		return this.proxyObjId;
	}
	
	public JsonObject getProxyJsonObject() {
		return this.jsonObject;
	}
}
