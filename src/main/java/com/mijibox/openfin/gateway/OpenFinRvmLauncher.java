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
	private String rvmVersion;
	private Path rvmInstallDirectory;
	private String rvmExecutableName;
	private List<String> rvmOptions;

	public OpenFinRvmLauncher(OpenFinRvmLauncherBuilder builder) {
		super(builder);
		this.rvmInstallDirectory = builder.getOpenFinDirectory();
		this.rvmExecutableName = builder.getRvmExecutableName();
		this.rvmVersion = builder.getRvmVersion();
		this.rvmOptions = builder.getRvmOptions();
		if (this.runtimeVersion == null) {
			this.runtimeVersion = "stable";
		}
	}

	private CompletionStage<Path> getRvmExecutablePath() {
		Path rvmPath = this.rvmInstallDirectory.resolve(this.rvmExecutableName);
		if (!Files.exists(rvmPath, LinkOption.NOFOLLOW_LINKS)) {
			return CompletableFuture.supplyAsync(() -> {
				logger.debug("{} not available.", rvmPath);
				try {
					this.download();
					return rvmPath;
				}
				catch (Exception e) {
					throw new RuntimeException("error downloading OpenFinRVM", e);
				}
			});
		}
		else {
			logger.debug("OpenFinRVM executable located: {}", rvmPath);
			return CompletableFuture.completedStage(rvmPath);
		}
	}

	private void download() throws Exception {
		String rvmTarget = "/release/rvm/" + this.rvmVersion;
		logger.info("download OpenFinRVM from {}", rvmTarget);
		Path rvmZip = this.download(rvmTarget);
		logger.debug("RVM downloaded, version: {}, path: {}", this.rvmVersion, rvmZip);
		this.unzip(rvmZip, this.rvmInstallDirectory);
		Files.delete(rvmZip);
	}


	@Override
	public CompletionStage<OpenFinConnection> launch() {
		logger.info("launching OpenFinRVM");
		String namedPipeName = UUID.randomUUID().toString();
		CompletionStage<Integer> portNumberFuture = this.findPortNumber(namedPipeName);
		return this.getRvmExecutablePath().thenApply(rvmPath -> {
			try {
				//rvm can handle runtime channel version
				var configPath = this.createStartupConfig(namedPipeName);
				List<String> command = new ArrayList<>();
				command.add(rvmPath.toAbsolutePath().normalize().toString());
				for (String s : this.rvmOptions) {
					command.add(s);
				}
				command.add("--config=" + configPath.toAbsolutePath().normalize().toUri().toURL().toString());
				
				logger.info("start process: {}", command);
				ProcessBuilder pb = new ProcessBuilder(command.toArray(new String[] {}))
						.redirectOutput(Redirect.DISCARD)
						.redirectError(Redirect.DISCARD);
				pb.start();
				return pb;
			}
			catch (Exception e) {
				logger.error("error launching OpenFinRVM", e);
				throw new RuntimeException("error launching OpenFinRVM", e);
			}
		}).thenCombine(portNumberFuture, (v, port) -> {
			return new OpenFinConnection(namedPipeName, port);
		});
	}
}
