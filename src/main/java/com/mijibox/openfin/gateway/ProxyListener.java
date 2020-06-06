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

public class ProxyListener {
	private JsonValue proxyListenerId;
	private OpenFinIabMessageListener listener;
	private String iabTopic;
	private ProxyObject invoker;
	private OpenFinGatewayImpl gateway;
	
	ProxyListener(ProxyObject invoker, OpenFinIabMessageListener listener, OpenFinGatewayImpl gateway) {
		this.invoker = invoker;
		this.listener = listener;
		this.gateway = gateway;
	}
	
	void setProxyListenerId(JsonValue proxyListenerId) {
		this.proxyListenerId = proxyListenerId;
	}
	
	void setIabTopic(String iabTopic) {
		this.iabTopic = iabTopic;
	}
	
	String getIabTopic() {
		return this.iabTopic;
	}
	
	public ProxyObject getInvoker() {
		return this.invoker;
	}
	
	OpenFinIabMessageListener getListener() {
		return this.listener;
	}

	JsonValue getProxyListenerId() {
		return this.proxyListenerId;
	}

	public CompletionStage<Void> dispose() {
		return this.gateway.deleteProxyObject(this.proxyListenerId);
	}
}
