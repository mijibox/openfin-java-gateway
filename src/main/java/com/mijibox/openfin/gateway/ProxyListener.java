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

import javax.json.JsonValue;

public class ProxyListener extends AbstractProxy {
	private OpenFinIabMessageListener listener;
	private String iabTopic;
	
	ProxyListener(JsonValue proxyId, ProxyObject invoker, String iabTopic, OpenFinIabMessageListener listener, OpenFinGatewayImpl gateway) {
		super(proxyId, invoker, gateway);
		this.iabTopic = iabTopic;
		this.listener = listener;
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
}
