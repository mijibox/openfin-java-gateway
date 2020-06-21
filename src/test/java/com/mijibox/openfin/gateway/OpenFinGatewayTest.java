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

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mijibox.openfin.gateway.OpenFinGateway.OpenFinGatewayListener;

@RunWith(Parameterized.class)
public class OpenFinGatewayTest {
	final static Logger logger = LoggerFactory.getLogger(OpenFinGatewayTest.class);

	@Parameters
	public static String[] data() {
		return new String[] {
				"10.66.41.18",
//				"11.69.42.29",
//				"12.69.43.22",
//				"13.76.45.15",
//				"14.78.48.16",
//				"15.80.50.34",
//				"16.83.50.9"
		};
	}

	private String runtimeVersion;

	public OpenFinGatewayTest(String runtimeVersion) {
		this.runtimeVersion = runtimeVersion;
	}

	@Test
	public void gatewayListenerOnOpen() throws Exception {
		CompletableFuture<?> listenerFuture = new CompletableFuture<>();
		OpenFinGatewayLauncher.newOpenFinGatewayLauncher()
				.launcherBuilder(OpenFinLauncher.newOpenFinLauncherBuilder()
						.runtimeVersion(this.runtimeVersion)
						.addRuntimeOption("--v=1")
						.addRuntimeOption("--no-sandbox"))
				.gatewayListener(new OpenFinGatewayListener() {
					@Override
					public void onOpen(OpenFinGateway gateway) {
						listenerFuture.complete(null);
					}

					@Override
					public void onError() {
					}

					@Override
					public void onClose() {
					}
				})
				.open().thenAccept(gateway -> {
					gateway.close();
				})
				.exceptionally(e -> {
					e.printStackTrace();
					return null;
				})
				.toCompletableFuture().get(120, TimeUnit.SECONDS);
		listenerFuture.get(20, TimeUnit.SECONDS);
	}

	@Test
	public void gatewayListenerOnClose() throws Exception {
		CompletableFuture<?> listenerFuture = new CompletableFuture<>();
		OpenFinGatewayLauncher.newOpenFinGatewayLauncher()
				.launcherBuilder(OpenFinLauncher.newOpenFinLauncherBuilder()
						.runtimeVersion(this.runtimeVersion)
						.addRuntimeOption("--v=1")
						.addRuntimeOption("--no-sandbox"))
				.gatewayListener(new OpenFinGatewayListener() {
					@Override
					public void onOpen(OpenFinGateway gateway) {
					}

					@Override
					public void onError() {
					}

					@Override
					public void onClose() {
						listenerFuture.complete(null);
					}
				})
				.open().thenAccept(gateway -> {
					gateway.close();
				})
				.exceptionally(e -> {
					e.printStackTrace();
					return null;
				})
				.toCompletableFuture().get(120, TimeUnit.SECONDS);
		listenerFuture.get(20, TimeUnit.SECONDS);
	}

	@Ignore
	@Test
	public void showConsole() throws Exception {
		System.setProperty("com.mijibox.openfin.gateway.showConsole", "true");
		OpenFinGatewayLauncher
				.newOpenFinGatewayLauncher()
				.open()
				.toCompletableFuture().get(10, TimeUnit.SECONDS);
		Thread.sleep(Long.MAX_VALUE);
	}

	@Test
	public void setPermission() throws Exception {
		String appUuid = UUID.randomUUID().toString();
		JsonObject startupApp = Json.createObjectBuilder()
				.add("uuid", appUuid)
				.add("name", appUuid)
				.add("url", "https://www.google.com")
				.add("permissions", Json.createObjectBuilder()
						.add("System", Json.createObjectBuilder()
								.add("readRegistryValue", true)))
				.add("autoShow", true)
				.build();
		// System.setProperty("com.mijibox.openfin.gateway.showConsole", "true");
		OpenFinGateway gateway = OpenFinGatewayLauncher
				.newOpenFinGatewayLauncher()
				.launcherBuilder(OpenFinLauncher.newOpenFinLauncherBuilder()
						.addRuntimeOption("--v=1")
						.runtimeVersion("stable-v11"))
				.open(startupApp)
				.toCompletableFuture().get(10, TimeUnit.SECONDS);

		gateway.invoke("fin.System.readRegistryValue", Json.createValue("HKEY_LOCAL_MACHINE"),
				Json.createValue("HARDWARE\\DESCRIPTION\\System"), Json.createValue("BootArchitecture"))
				.thenCompose(r -> {
					logger.debug("readRegistryValue: {}", r.getResultAsJsonObject());
					return gateway
							.invoke(true, "fin.Application.wrap",
									Json.createObjectBuilder().add("uuid", appUuid).build())
							.thenCompose(r2 -> {
								return r2.getProxyObject().invoke("quit", JsonValue.TRUE).thenCompose(q -> {
									return gateway.close();
								});
							});
				})
				.toCompletableFuture().get(10, TimeUnit.SECONDS);
	}

	@Test
	public void openManifestUrl() throws Exception {
		OpenFinGatewayLauncher
				.newOpenFinGatewayLauncher()
				.launcherBuilder(OpenFinLauncher.newOpenFinLauncherBuilder()
						.addRuntimeOption("--v=1")
						.runtimeVersion("beta"))
				.open("https://cdn.openfin.co/demos/hello/app.json").thenCompose(gateway -> {
					return gateway
							.invoke(true, "fin.Application.wrap",
									Json.createObjectBuilder().add("uuid", "OpenFinHelloWorld").build())
							.thenCompose(r -> {
								return r.getProxyObject().invoke("quit", JsonValue.TRUE).thenCompose(q -> {
									return gateway.close();
								});
							});
				})
				.toCompletableFuture()
				.get(180, TimeUnit.SECONDS);
	}

	@Test
	public void runtimeLauncherChannelVersion() throws Exception {
		String appUuid = UUID.randomUUID().toString();
		JsonObject startupApp = Json.createObjectBuilder()
				.add("uuid", appUuid)
				.add("name", appUuid)
				.add("url", "https://www.google.com")
				.add("autoShow", true)
				.build();
		OpenFinGatewayLauncher
				.newOpenFinGatewayLauncher()
				.launcherBuilder(new OpenFinRuntimeLauncherBuilder()
						.addRuntimeOption("--v=1")
						.runtimeVersion("stable-v12"))
				.open(startupApp)
				.thenCompose(gateway -> {
					return gateway
							.invoke(true, "fin.Application.wrap",
									Json.createObjectBuilder().add("uuid", appUuid).build())
							.thenCompose(r2 -> {
								return r2.getProxyObject().invoke("quit", JsonValue.TRUE).thenCompose(q -> {
									return gateway.close();
								});
							});
				})
				.toCompletableFuture().get(180, TimeUnit.SECONDS);

	}

}
