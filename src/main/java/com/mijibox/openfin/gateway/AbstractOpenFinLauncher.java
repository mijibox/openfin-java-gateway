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

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.json.Json;
import javax.json.JsonObjectBuilder;

import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jna.Platform;

public abstract class AbstractOpenFinLauncher implements OpenFinLauncher {

	private final static Logger logger = LoggerFactory.getLogger(AbstractOpenFinLauncher.class);

	private String assetsUrl;
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

	protected Path download(String target) throws Exception {
		URL url = new URL(this.assetsUrl + target);
		logger.info("download: {}", url.toString());
		ReadableByteChannel readableByteChannel = Channels.newChannel(url.openStream());
		Path tempFile = Files.createTempFile(null, null);
//		Path tempFile = Paths.get("./runtime-" + this.runtimeVersion + ".zip");
		FileOutputStream fileOutputStream = new FileOutputStream(tempFile.toFile());
		long size = fileOutputStream.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
		logger.debug("{} downloaded, path: {}, size: {}", target, tempFile, size);
		fileOutputStream.close();
		return tempFile;
	}

	protected void unzip(Path zipFilePath, Path targetFolder) throws IOException {
		logger.debug("unzip {} to {}", zipFilePath, targetFolder);
		Files.createDirectories(targetFolder);
		ZipFile zipFile = new ZipFile(zipFilePath.toFile());
		Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
		
		//need to do the symbolics the last
		List<ZipArchiveEntry> symLinks = new ArrayList<>();
		
		while (entries.hasMoreElements()) {
			ZipArchiveEntry entry = entries.nextElement();
			Path filePath = targetFolder.resolve(entry.getName());
			logger.debug("unzipping {}, size: {}", filePath, entry.getSize());
			// incase parent folder is not created yet.
			Files.createDirectories(filePath.getParent());
			if (entry.isUnixSymlink()) {
				logger.debug("symLink, do it later: {}");
				symLinks.add(entry);
			}
			else if (!entry.isDirectory()) {
				Files.createFile(filePath);
				ReadableByteChannel readableByteChannel = Channels.newChannel(zipFile.getInputStream(entry));
				FileOutputStream fileOutputStream = new FileOutputStream(filePath.toFile());
				long size = fileOutputStream.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
				if (Platform.isLinux() || Platform.isMac()) {
					int unixMode = entry.getUnixMode();
					if ((unixMode & UnixStat.PERM_MASK & 01111) != 0) {
						logger.debug("can execute: {}", filePath);
						filePath.toFile().setExecutable(true);
					}
				}
				logger.debug("File extracted, path: {}, size: {}", filePath, size);
				fileOutputStream.close();
			}
			else {
				Files.createDirectories(filePath);
				logger.debug("created directory: {}", filePath);
			}
		}
		
		while (symLinks.size() > 0) {
			ZipArchiveEntry entry = symLinks.remove(0);
			Path filePath = targetFolder.resolve(entry.getName());
			BufferedReader br = new BufferedReader(new InputStreamReader(zipFile.getInputStream(entry)));
			String linkTarget = br.readLine();
			br.close();
			try {
				Path targetPath = filePath.getParent().resolve(linkTarget);
				logger.debug("creating link from {} to {}", filePath, targetPath);
				if (Files.exists(targetPath)) {
					Files.createSymbolicLink(filePath, Paths.get(linkTarget));
				}
				else {
					logger.debug("target doesn't exist, do it later");
					symLinks.add(entry);
				}
			}
			catch(Exception e) {
				e.printStackTrace();
				throw e;
			}
		}
		zipFile.close();
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

	CompletionStage<Integer> findPortNumber(String namedPipeName) {
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
