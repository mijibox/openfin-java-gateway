package com.mijibox.openfin.gateway;

import java.net.URL;
import java.util.concurrent.CompletionStage;

import javax.json.JsonObject;

import com.mijibox.openfin.gateway.OpenFinGateway.OpenFinGatewayListener;

public interface OpenFinGatewayLauncher {
	
	static OpenFinGatewayLauncher newOpenFinGatewayLauncher() {
		return new OpenFinGatewayLauncherImpl();
	}
	
	OpenFinGatewayLauncher launcherBuilder(OpenFinLauncherBuilder launcherBuilder);
	
	/**
	 * When not using default application options, prepend gateway javascript to preloadScripts. 
	 * @param injectGatewayScript
	 * @return
	 */
	OpenFinGatewayLauncher injectGatewayScript(boolean injectGatewayScript);

	OpenFinGatewayLauncher gatewayListener(OpenFinGatewayListener listener);
	
	CompletionStage<OpenFinGateway> open();

	CompletionStage<OpenFinGateway> open(URL configUrl);

	CompletionStage<OpenFinGateway> open(JsonObject startupApp);
}
