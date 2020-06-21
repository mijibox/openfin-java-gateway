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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jna.Platform;

public interface AssetHelper {

	final static Logger logger = LoggerFactory.getLogger(AssetHelper.class);

	public static CompletionStage<Path> fetch(String assetUrl) {
		try {
			return fetch(assetUrl, Files.createTempFile(null, null));
		}
		catch (Exception e) {
			logger.error("fetch error", e);
			throw new RuntimeException("unable to fetch " + assetUrl, e);
		}
	}

	public static CompletionStage<Path> fetch(String assetUrl, Path targetPath) {
		return CompletableFuture.supplyAsync(()->{
			try {
				long startTime = System.currentTimeMillis();
				URL url = new URL(assetUrl);
				logger.info("fetching: {}", url);
				ReadableByteChannel readableByteChannel = Channels.newChannel(url.openStream());
				FileOutputStream fileOutputStream = new FileOutputStream(targetPath.toFile());
				long size = fileOutputStream.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
				fileOutputStream.close();
				long endTime = System.currentTimeMillis();
				logger.debug("{} fetched to: {}, size: {}, time spent: {}ms", url, targetPath, size, (endTime - startTime));
				return targetPath;
			}
			catch (Exception e) {
				logger.error("fetch error", e);
				throw new RuntimeException("unable to fetch " + assetUrl, e);
			}
			finally {
			}
		});
	}
	
	public static CompletionStage<String> fetchContent(String assetUrl) {
		return fetch(assetUrl).thenApply(f->{
			try {
				String content = new String(Files.readAllBytes(f));
				Files.delete(f);
				return content;
			}
			catch (Exception e) {
				throw new RuntimeException("unable to fetchContent", e);
			}
		});
	}
	
	public static CompletionStage<Path> unzip(Path zipFilePath) {
		try {
			return unzip(zipFilePath, Files.createTempDirectory(null)); 
		}
		catch (IOException e) {
			logger.error("unzip error", e);
			throw new RuntimeException("unable to unzip " + zipFilePath, e);
		}
	}
	
	public static CompletionStage<Path> unzip(Path zipFilePath, Path targetFolder) {
		return CompletableFuture.supplyAsync(()->{
			try {
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
					// in case parent folder is not created yet.
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
				return targetFolder;
			}
			catch (Exception e) {
				throw new RuntimeException("unable to unzip file", e);
			}
			finally {
			}
		});
	}
}
