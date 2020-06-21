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

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenFinRvmLauncher extends AbstractOpenFinLauncher {
	private final static Logger logger = LoggerFactory.getLogger(OpenFinRvmLauncher.class);
	protected String rvmVersion;
	protected Path rvmInstallDirectory;
	protected List<String> rvmOptions;
	protected Path rvmExecutablePath;

	public OpenFinRvmLauncher(OpenFinRvmLauncherBuilder builder) {
		super(builder);
		this.rvmInstallDirectory = builder.getOpenFinDirectory();
		this.rvmVersion = builder.getRvmVersion();
		this.rvmOptions = builder.getRvmOptions();
		if (this.runtimeVersion == null) {
			this.runtimeVersion = "stable";
		}
	}

	public CompletionStage<Path> getExecutablePath() {
		this.rvmExecutablePath = this.rvmInstallDirectory.resolve("OpenFinRVM.exe");
		if (!Files.exists(this.rvmExecutablePath, LinkOption.NOFOLLOW_LINKS)) {
			logger.debug("{} not available.", this.rvmExecutablePath);
			return AssetHelper.fetch(this.assetsUrl + "/release/rvm/" + this.rvmVersion).thenCompose(rvmZip -> {
				return AssetHelper.unzip(rvmZip, this.rvmInstallDirectory).thenApply(f -> {
					return this.rvmExecutablePath;
				});
			});
		}
		else {
			logger.debug("OpenFinRVM executable located: {}", this.rvmExecutablePath);
			return CompletableFuture.completedStage(this.rvmExecutablePath);
		}
	}

	public CompletionStage<List<String>> getCommandArguments() {
		return this.getStartupConfigPath().thenApply(configPath -> {
			List<String> command = new ArrayList<>();
			for (String s : this.rvmOptions) {
				command.add(s);
			}
			command.add("--config=" + configPath.toUri().toString());
			return command;
		});
	}

	
	@Override
	public CompletionStage<OpenFinConnection> launch() {
		logger.info("launching Open Runtim via OpenFinRVM");

		CompletionStage<Integer> portNumberFuture = this.findPortNumber();

		return this.getExecutablePath()
				.thenCompose(rvmPath -> {
					return this.getStartupConfigPath();
				})
				.thenCompose(configPath -> {
					return this.getCommandArguments();
				})
				.thenApply(args -> {
					try {
						ArrayList<String> command = new ArrayList<>(args);
						command.add(0, this.rvmExecutablePath.toAbsolutePath().toString());
						logger.info("start process: {}", command);
						ProcessBuilder pb = new ProcessBuilder(command.toArray(new String[command.size()]))
								.inheritIO();
						pb.directory(this.openFinDirectory.getParent().toFile());
						pb.start();
						logger.debug("process started");
						return this.startupConfig;
					}
					catch (Exception e) {
						logger.error("error launching OpenFinRVM", e);
						throw new RuntimeException("error launching OpenFinRVM", e);
					}
				})
				.thenCombine(portNumberFuture, (configPath, n) -> {
					return new OpenFinConnection(this.connectionUuid, n, this.licenseKey,
							configPath.toUri().toString());
				});
	}
}
