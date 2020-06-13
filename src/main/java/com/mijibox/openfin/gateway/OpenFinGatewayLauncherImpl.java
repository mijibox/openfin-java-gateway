package com.mijibox.openfin.gateway;

import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.json.JsonObject;

import com.mijibox.openfin.gateway.OpenFinGateway.OpenFinGatewayListener;

public class OpenFinGatewayLauncherImpl implements OpenFinGatewayLauncher {

	private OpenFinLauncherBuilder launcherBuilder;
	private boolean injectGatewayScript;
	private JsonObject starupApp;
	private URL configUrl;
	private OpenFinGatewayListener gatewayListener;
	
	OpenFinGatewayLauncherImpl() {
		this.injectGatewayScript = true;
	}

	@Override
	public OpenFinGatewayLauncher launcherBuilder(OpenFinLauncherBuilder launcherBuilder) {
		this.launcherBuilder = launcherBuilder;
		return this;
	}

	@Override
	public OpenFinGatewayLauncher injectGatewayScript(boolean injectGatewayScript) {
		this.injectGatewayScript = injectGatewayScript;
		return this;
	}

	@Override
	public OpenFinGatewayLauncher gatewayListener(OpenFinGatewayListener listener) {
		this.gatewayListener = listener;
		return this;
	}

	@Override
	public CompletionStage<OpenFinGateway> open() {
		return this.openGateway();
	}

	@Override
	public CompletionStage<OpenFinGateway> open(URL configUrl) {
		this.configUrl = configUrl;
		return this.openGateway();
	}

	@Override
	public CompletionStage<OpenFinGateway> open(JsonObject startupApp) {
		this.starupApp = startupApp;
		return this.openGateway();
	}

	boolean isInjectGatewayScript() {
		return this.injectGatewayScript;
	}
	
	JsonObject getStartupApp() {
		return this.starupApp;
	}
	
	URL getConfigUrl() {
		return this.configUrl;
	}

	private CompletionStage<OpenFinGateway> openGateway() {
		return CompletableFuture.supplyAsync(() -> {
			if (this.launcherBuilder == null) {
				this.launcherBuilder = OpenFinLauncher.newOpenFinLauncherBuilder();
			}
			return this.launcherBuilder;
		}).thenCompose(launcherBuilder->{
			return this.launcherBuilder.build()
					.thenCompose(launcher -> {
						return launcher.launch();
					})
					.thenCompose(connection -> {
						return connection.connect();
					})
					.thenCompose(connection -> {
						return OpenFinGatewayImpl.newInstance(this, connection, gatewayListener);
					});
		});
	}

}
