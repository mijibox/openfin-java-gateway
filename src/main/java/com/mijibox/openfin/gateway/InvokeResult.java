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

import java.math.BigDecimal;
import java.math.BigInteger;

import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonString;
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
	
	public Boolean getResultAsBoolean() {
		Boolean b = null;
		if (result != null && JsonValue.TRUE.equals(result)) {
			b = Boolean.TRUE;
		}
		else if (result != null) {
			b = Boolean.FALSE;
		}
		return b;
	}
	
	public Integer getResultAsInteger() {
		return this.result== null ? null : Integer.valueOf(((JsonNumber)this.result).intValue());
	}

	public Long getResultAsLong() {
		return this.result== null ? null : Long.valueOf(((JsonNumber)this.result).longValue());
	}

	public Double getResultAsDouble() {
		return this.result== null ? null : Double.valueOf(((JsonNumber)this.result).doubleValue());
	}

	public BigInteger getResultAsBigInteger() {
		return this.result== null ? null : ((JsonNumber)this.result).bigIntegerValue();
	}
	
	public BigDecimal getResultAsBigDecimal() {
		return this.result== null ? null : ((JsonNumber)this.result).bigDecimalValue();
	}
	
	public String getResultAsString() {
		return this.result == null ? null : ((JsonString) this.result).getString();
	}

	public JsonObject getResultAsJsonObject() {
		return (JsonObject) this.result;
	}
	
	public JsonArray getResultAsJsonArray() {
		return (JsonArray) this.result;
	}

}
