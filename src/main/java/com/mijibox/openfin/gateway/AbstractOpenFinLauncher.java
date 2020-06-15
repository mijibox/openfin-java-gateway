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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.json.Json;
import javax.json.JsonObjectBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jna.Platform;

public abstract class AbstractOpenFinLauncher implements OpenFinLauncher {

	private final static Logger logger = LoggerFactory.getLogger(AbstractOpenFinLauncher.class);

	protected String assetsUrl;
	protected String licenseKey;
	protected String runtimeVersion;
	protected List<String> runtimeOptions;
	protected Path openFinDirectory;

	protected AbstractOpenFinLauncher() {
		this.runtimeOptions = new ArrayList<>();
	}

	public AbstractOpenFinLauncher(AbstractLauncherBuilder builder) {
		this.licenseKey = builder.getLicenseKey();
		this.assetsUrl = builder.getAssetsUrl();
		this.runtimeVersion = builder.getRuntimeVersion();
		this.runtimeOptions = builder.getRuntimeOptions();
		this.openFinDirectory = builder.getOpenFinDirectory();
	}

	protected Path createStartupConfig(String namedPipeName) throws IOException {
		StringBuilder args = new StringBuilder("--runtime-information-channel-v6=").append(namedPipeName);
		for (String s : this.runtimeOptions) {
			args.append(" ").append(s);
		}
		JsonObjectBuilder jsonConfigBuilder = Json.createObjectBuilder();

		if (this.licenseKey != null) {
			jsonConfigBuilder.add("licenseKey", this.licenseKey);
		}
		jsonConfigBuilder.add("runtime", Json.createObjectBuilder()
				.add("version", this.runtimeVersion)
				.add("arguments", args.toString()).build());
		Path config = Files.createTempFile(null, ".json");
		String configString = jsonConfigBuilder.build().toString();
		logger.debug("startup config: {}", configString);
		Files.write(config, configString.getBytes(), StandardOpenOption.CREATE,
				StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
		
		return config;
	}

	protected CompletionStage<Integer> findPortNumber(String namedPipeName) {
		if (Platform.isWindows()) {
			WindowsPortDiscoverer portDiscoverer = new WindowsPortDiscoverer();
			return portDiscoverer.findPortNumber(namedPipeName);
		}
		else if (Platform.isLinux() || Platform.isMac()) {
			PosixPortDiscoverer portDiscoverer = new PosixPortDiscoverer();
			return portDiscoverer.findPortNumber(namedPipeName);
		}
		else {
			return CompletableFuture.completedStage(9696);
		}
	}
}
