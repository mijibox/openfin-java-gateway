package com.mijibox.openfin.gateway;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mijibox.openfin.gateway.OpenFinGateway.OpenFinGatewayListener;

public class OpenFinGatewayLauncherImpl implements OpenFinGatewayLauncher {
	private final static Logger logger = LoggerFactory.getLogger(OpenFinGatewayLauncherImpl.class);

	protected OpenFinLauncherBuilder launcherBuilder;
	protected JsonObject startupApp;
	protected String manifestUrl;
	protected OpenFinGatewayListener gatewayListener;
	
	public OpenFinGatewayLauncherImpl() {
	}

	@Override
	public OpenFinGatewayLauncher launcherBuilder(OpenFinLauncherBuilder launcherBuilder) {
		this.launcherBuilder = launcherBuilder;
		return this;
	}

	@Override
	public OpenFinGatewayLauncher gatewayListener(OpenFinGatewayListener listener) {
		this.gatewayListener = listener;
		return this;
	}

	@Override
	public CompletionStage<? extends OpenFinGateway> open() {
		return this.openGateway();
	}

	@Override
	public CompletionStage<? extends OpenFinGateway> open(String manifestUrl) {
		logger.debug("open gateway using manifestUrl: {}", manifestUrl);
		this.startupApp = null;
		this.manifestUrl = manifestUrl;
		return this.openGateway();
	}

	@Override
	public CompletionStage<? extends OpenFinGateway> open(JsonObject startupApp) {
		logger.debug("open gateway using startupApp: {}", startupApp);
		this.manifestUrl = null;
		this.startupApp = startupApp;
		return this.openGateway();
	}
	
	public JsonObject getStartupApp() {
		return this.startupApp;
	}
	
	public String getManifestUrl() {
		return this.manifestUrl;
	}

	protected CompletionStage<? extends OpenFinGateway> openGateway() {
		return CompletableFuture.supplyAsync(() -> {
			if (this.launcherBuilder == null) {
				this.launcherBuilder = OpenFinLauncher.newOpenFinLauncherBuilder();
			}
			return this.launcherBuilder;
		}).thenCompose(launcherBuilder->{
			logger.debug("using launcherBuilder: {}", this.launcherBuilder.getClass().getName());
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
