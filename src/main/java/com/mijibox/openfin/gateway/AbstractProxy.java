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

public abstract class AbstractProxy {
	JsonValue proxyId;
	OpenFinGatewayImpl gateway;
	ProxyObject invoker;
	
	AbstractProxy(JsonValue proxyId, ProxyObject invoker, OpenFinGatewayImpl gateway) {
		this.proxyId = proxyId;
		this.invoker = invoker;
		this.gateway = gateway;
	}
	
	public ProxyObject getInvoker() {
		return this.invoker;
	}
	
	public JsonValue getProxyId() {
		return this.proxyId;
	}
	
	public OpenFinGateway getGateway() {
		return this.gateway;
	}
	
	public CompletionStage<Void> dispose() {
		return this.gateway.deleteProxyObject(this.proxyId);
	}
}
