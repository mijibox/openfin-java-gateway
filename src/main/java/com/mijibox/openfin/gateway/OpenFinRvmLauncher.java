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

import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenFinRvmLauncher extends AbstractOpenFinLauncher {
	private final static Logger logger = LoggerFactory.getLogger(OpenFinRvmLauncher.class);
	protected String rvmVersion;
	protected Path rvmInstallDirectory;
	protected List<String> rvmOptions;

	public OpenFinRvmLauncher(OpenFinRvmLauncherBuilder builder) {
		super(builder);
		this.rvmInstallDirectory = builder.getOpenFinDirectory();
		this.rvmVersion = builder.getRvmVersion();
		this.rvmOptions = builder.getRvmOptions();
		if (this.runtimeVersion == null) {
			this.runtimeVersion = "stable";
		}
	}

	protected CompletionStage<Path> getRvmExecutablePath() {
		Path rvmPath = this.rvmInstallDirectory.resolve("OpenFinRVM.exe");
		if (!Files.exists(rvmPath, LinkOption.NOFOLLOW_LINKS)) {
			logger.debug("{} not available.", rvmPath);
			return AssetHelper.fetch(this.assetsUrl + "/release/rvm/" + this.rvmVersion).thenCompose(rvmZip->{
				return AssetHelper.unzip(rvmZip, this.rvmInstallDirectory).thenApply(f->{
					return rvmPath;
				});
			});
		}
		else {
			logger.debug("OpenFinRVM executable located: {}", rvmPath);
			return CompletableFuture.completedStage(rvmPath);
		}
	}

	@Override
	public CompletionStage<OpenFinConnection> launch() {
		logger.info("launching OpenFinRVM");
		String connectionUuid = UUID.randomUUID().toString();
		CompletionStage<Integer> portNumberFuture = this.findPortNumber(connectionUuid);
		return this.getRvmExecutablePath().thenApply(rvmPath -> {
			try {
				//rvm can handle runtime channel version
				Path configPath = this.createStartupConfig(connectionUuid);
				List<String> command = new ArrayList<>();
				command.add(rvmPath.toAbsolutePath().normalize().toString());
				for (String s : this.rvmOptions) {
					command.add(s);
				}
				command.add("--config=" + configPath.toUri().toString());
				
				logger.info("start process: {}", command);
				ProcessBuilder pb = new ProcessBuilder(command.toArray(new String[] {}))
						.redirectOutput(Redirect.DISCARD)
						.redirectError(Redirect.DISCARD);
				pb.start();
				return configPath;
			}
			catch (Exception e) {
				logger.error("error launching OpenFinRVM", e);
				throw new RuntimeException("error launching OpenFinRVM", e);
			}
		}).thenCombine(portNumberFuture, (configPath, port) -> {
			return new OpenFinConnection(connectionUuid, port, this.licenseKey, configPath.toUri().toString());
		});
	}
}
