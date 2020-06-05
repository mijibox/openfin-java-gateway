package com.mijibox.openfin.gateway;

import javax.json.JsonArray;
import javax.json.JsonValue;

@FunctionalInterface
public interface OpenFinActionListener {
	public JsonValue onEvent(JsonArray message);
}
