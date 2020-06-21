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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jna.Platform;

public class OpenFinRuntimeLauncher extends AbstractOpenFinLauncher {
	private final static Logger logger = LoggerFactory.getLogger(OpenFinRuntimeLauncher.class);
	private Path runtimeDirectory;
	private Path runtimeExecutablePath;

	public OpenFinRuntimeLauncher(OpenFinRuntimeLauncherBuilder builder) {
		super(builder);
		this.runtimeDirectory = builder.getRuntimeDirectory();
	}

	@Override
	protected CompletionStage<String> getRuntimeVersion() {
		if (!this.runtimeVersion.contains(".")) {
			return AssetHelper.fetchContent(this.assetsUrl + "/release/runtime/" + this.runtimeVersion)
					.thenApply(v -> {
						logger.debug("channel version: {}, fetched real version: {}", this.runtimeVersion, v);
						this.runtimeVersion = v;
						return this.runtimeVersion;
					});
		}
		else {
			return CompletableFuture.completedStage(this.runtimeVersion);
		}
	}


	
	public CompletionStage<List<String>> getCommandArguments() {
		return this.getExecutablePath().thenCompose(runtimePath -> {
			return this.getStartupConfigPath().thenApply(configPath -> {
				List<String> command = new ArrayList<>();
				command.add("--version-keyword=\"" + this.runtimeVersion + "\"");
				for (String s : this.runtimeOptions) {
					command.add(s);
				}
				if (Platform.isWindows()) {
//					command.add("--user-data-dir=" + this.openFinDirectory.normalize().toAbsolutePath().toString());
					command.add("--runtime-information-channel-v6=" + this.connectionUuid);
					command.add("--startup-url=" + configPath.normalize().toAbsolutePath().toUri());
				}
				else {
//					command.add("--user-data-dir=/" + this.openFinDirectory.normalize().toAbsolutePath().toString());
					command.add("--runtime-information-channel-v6=/"
							+ PosixPortDiscoverer.getNamedPipeFilePath(this.connectionUuid));
					command.add("--startup-url=file:///" + configPath.normalize().toAbsolutePath().toString());
				}
				return command;
			});
		});
	}

	public CompletionStage<Path> getExecutablePath() {
		if (this.runtimeExecutablePath == null) {
			if (Platform.isWindows()) {
				runtimeExecutablePath = this.runtimeDirectory
						.resolve(Paths.get(this.runtimeVersion, "OpenFin/openfin.exe"));
			}
			else if (Platform.isLinux()) {
				runtimeExecutablePath = this.runtimeDirectory.resolve(Paths.get(this.runtimeVersion, "openfin"));
			}
			else if (Platform.isMac()) {
				runtimeExecutablePath = this.runtimeDirectory
						.resolve(Paths.get(this.runtimeVersion, "OpenFin.app/Contents/MacOS/OpenFin"));
			}
			else {
				throw new RuntimeException("OpenFin runtime unsupported on this platform");
			}
			if (!Files.exists(runtimeExecutablePath, LinkOption.NOFOLLOW_LINKS)) {
				logger.debug("{} not available.", runtimeExecutablePath);
				String target = null;
				if (Platform.isWindows() && Platform.is64Bit()) {
					target = "/release/runtime/x64/" + this.runtimeVersion;
				}
				else if (Platform.isWindows()) {
					target = "/release/runtime/" + this.runtimeVersion;
				}
				else if (Platform.isLinux() && Platform.isARM()) {
					target = "/release/runtime/linux/arm/" + this.runtimeVersion;
				}
				else if (Platform.isLinux()) {
					target = "/release/runtime/linux/x64/" + this.runtimeVersion;
				}
				else if (Platform.isMac()) {
					target = "/release/runtime/mac/x64/" + this.runtimeVersion;
				}

				if (target != null) {
					return AssetHelper.fetch(this.assetsUrl + target).thenCompose(zipFile -> {
						return AssetHelper.unzip(zipFile, this.runtimeDirectory.resolve(this.runtimeVersion))
								.thenApply(f -> {
									return runtimeExecutablePath;
								});
					});
				}
				else {
					throw new RuntimeException("no applicable OpenFin runtime available.");
				}
			}
			else {
				logger.debug("OpenFin runtime executable located: {}", runtimeExecutablePath);
				return CompletableFuture.completedStage(runtimeExecutablePath);
			}
		}
		else {
			return CompletableFuture.completedStage(runtimeExecutablePath);
		}
	}

	@Override
	public CompletionStage<OpenFinConnection> launch() {
		logger.debug("launching OpenFin Runtime, runtimeDirectory: {}, runtimeVersion: {}", this.runtimeDirectory,
				this.runtimeVersion);

		CompletionStage<Integer> portNumberFuture = this.findPortNumber();

		return this.getRuntimeVersion()
				.thenCompose(v -> {
					return this.getExecutablePath();
				})
				.thenCompose(runtimePath -> {
					return this.getStartupConfigPath();
				})
				.thenCompose(configPath -> {
					return this.getCommandArguments();
				})
				.thenApply(args -> {
					try {
						ArrayList<String> command = new ArrayList<>(args);
						command.add(0, this.runtimeExecutablePath.toAbsolutePath().toString());
						logger.info("start process: {}", command);
						ProcessBuilder pb = new ProcessBuilder(command.toArray(new String[command.size()]))
								.inheritIO();
						pb.directory(this.openFinDirectory.getParent().toFile());
						pb.start();
						logger.debug("process started");
						return this.startupConfig;
					}
					catch (Exception e) {
						logger.error("error launching OpenFin Runtime", e);
						throw new RuntimeException("error launching OpenFin runtime", e);
					}
				})
				.thenCombine(portNumberFuture, (configPath, n) -> {
					return new OpenFinConnection(this.connectionUuid, n, this.licenseKey,
							configPath.toUri().toString());
				});
	}

}
