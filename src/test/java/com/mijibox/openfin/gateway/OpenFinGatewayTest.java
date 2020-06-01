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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.mijibox.openfin.gateway.OpenFinGateway.OpenFinGatewayListener;

@RunWith(Parameterized.class)
public class OpenFinGatewayTest {

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
		OpenFinRuntimeLauncherBuilder builder = new OpenFinRuntimeLauncherBuilder();
		builder.runtimeVersion(this.runtimeVersion);
//		OpenFinLauncherBuilder builder = OpenFinLauncher.newOpenFinLauncherBuilder();

		CompletableFuture<OpenFinGateway> listenerFuture = new CompletableFuture<>();
		builder.open(new OpenFinGatewayListener() {

			@Override
			public void onOpen(OpenFinGateway gateway) {
				listenerFuture.complete(gateway);
			}

			@Override
			public void onError() {
			}

			@Override
			public void onClose() {
			}
		}).thenAccept(gateway -> {
			gateway.close();
		});
		listenerFuture.get(20, TimeUnit.SECONDS);
	}

	@Test
	public void gatewayListenerOnClose() throws Exception {
//		OpenFinRuntimeLauncherBuilder builder = new OpenFinRuntimeLauncherBuilder();
//		builder.runtimeVersion(this.runtimeVersion);
		OpenFinLauncherBuilder builder = OpenFinLauncher.newOpenFinLauncherBuilder();

		CompletableFuture<OpenFinGateway> listenerFuture = new CompletableFuture<>();
		builder.open(new OpenFinGatewayListener() {

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
		}).thenAccept(gateway -> {
			gateway.close();
		});
		listenerFuture.get(20, TimeUnit.SECONDS);
	}

	@Ignore
	@Test
	public void showConsole() throws Exception {
		System.setProperty("com.mijibox.openfin.gateway.showConsole", "true");
		OpenFinRuntimeLauncherBuilder builder = new OpenFinRuntimeLauncherBuilder();
//		builder.runtimeVersion(this.runtimeVersion);
//		OpenFinGateway apiGateway = OpenFinLauncher.newOpenFinLauncherBuilder()
				builder.addRuntimeOption("--v=1")
				.open(null).toCompletableFuture().get();
		Thread.sleep(Long.MAX_VALUE);
	}
}
