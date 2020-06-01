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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mijibox.openfin.gateway.OpenFinGateway.OpenFinGatewayListener;

public class OpenFinRvmLauncherBuilder extends AbstractLauncherBuilder {
	private final static Logger logger = LoggerFactory.getLogger(OpenFinRvmLauncherBuilder.class);

	private String rvmVersion;
	private List<String> rvmOptions;

	OpenFinRvmLauncherBuilder() {
		this.rvmVersion = "latest";
		this.rvmOptions = new ArrayList<>();
	}

	@Override
	public CompletionStage<OpenFinGateway> open(OpenFinGatewayListener listener) {
		return CompletableFuture.supplyAsync(() -> {
			return new OpenFinRvmLauncher(this);
		}).thenCompose(rvm -> {
			return rvm.launch();
		}).thenCompose(connection -> {
			return connection.connect();
		}).thenCompose(connection -> {
			return OpenFinGatewayImpl.newInstance(connection, listener);
		});
	}

	List<String> getRvmOptions() {
		return this.rvmOptions;
	}

	public OpenFinRvmLauncherBuilder rvmVersion(String rvmVersion) {
		this.rvmVersion = rvmVersion;
		return this;
	}

	String getRvmVersion() {
		return this.rvmVersion;
	}

	String getRvmExecutableName() {
		return "OpenFinRVM.exe";
	}

	CompletableFuture<String> getRvmLatestVersion() {
		return CompletableFuture.supplyAsync(() -> {
			String v = null;
			try {
				String latestVersionUrl = this.assetsUrl + "/release/rvm/latestVersion";
				logger.info("retrieving latest RVM version number from: {}", latestVersionUrl);
				URL url = new URL(latestVersionUrl);
				BufferedInputStream bis = new BufferedInputStream(url.openStream());
				v = new String(bis.readAllBytes());
				logger.info("Got RVM latestVersion: {}", v);
				bis.close();
				this.rvmVersion = v;
			}
			catch (IOException e) {
				logger.error("error getRVMLatestVersion", e);
			}
			finally {
			}
			return v;
		});
	}

	public OpenFinRvmLauncherBuilder addRvmOption(String rvmOption) {
		this.rvmOptions.add(rvmOption);
		return this;
	}
}
