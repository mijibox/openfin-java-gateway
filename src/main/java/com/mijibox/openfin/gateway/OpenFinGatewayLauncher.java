package com.mijibox.openfin.gateway;

import java.util.concurrent.CompletionStage;

import javax.json.JsonObject;

import com.mijibox.openfin.gateway.OpenFinGateway.OpenFinGatewayListener;

public interface OpenFinGatewayLauncher {

	static OpenFinGatewayLauncher newOpenFinGatewayLauncher() {
		return new OpenFinGatewayLauncherImpl();
	}

	OpenFinGatewayLauncher launcherBuilder(OpenFinLauncherBuilder launcherBuilder);

	OpenFinGatewayLauncher gatewayListener(OpenFinGatewayListener listener);

	CompletionStage<? extends OpenFinGateway> open();

	CompletionStage<? extends OpenFinGateway> open(String manifestUrl);

	CompletionStage<? extends OpenFinGateway> open(JsonObject startupApp);
}
