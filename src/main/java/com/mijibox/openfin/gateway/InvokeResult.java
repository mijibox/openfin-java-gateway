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

import static com.mijibox.openfin.gateway.OpenFinGatewayImpl.PROXY_OBJECT_ID;
import static com.mijibox.openfin.gateway.OpenFinGatewayImpl.RESULT;

import javax.json.JsonObject;
import javax.json.JsonValue;

public class InvokeResult {
	private JsonValue proxyObjId;
	private JsonValue result;
	private ProxyObject proxyObject;
	private OpenFinGatewayImpl apiGateway;
	private ProxyObject invoker;

	InvokeResult(ProxyObject invoker, JsonObject invokeResult, OpenFinGatewayImpl apiGateway) {
		this.invoker = invoker;
		this.apiGateway = apiGateway;
		this.proxyObjId = invokeResult.containsKey(PROXY_OBJECT_ID)
				? invokeResult.get(PROXY_OBJECT_ID)
				: null;
		this.result = invokeResult.containsKey(RESULT) ? invokeResult.get(RESULT) : null;
	}

	public ProxyObject getProxyObject() {
		if (this.proxyObject == null && this.proxyObjId != null) {
			this.proxyObject = new ProxyObject(this.proxyObjId, this.invoker, this.apiGateway);
		}
		return this.proxyObject;
	}

	public JsonValue getResult() {
		return this.result;
	}
}
