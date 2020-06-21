package com.mijibox.openfin.gateway;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AssetHelperTest {
	private final static Logger logger = LoggerFactory.getLogger(AssetHelperTest.class);

	@Test
	public void fetchFromHttp() throws Exception {
		String version = AssetHelper.fetchContent("https://chromedriver.storage.googleapis.com/LATEST_RELEASE_84")
				.toCompletableFuture().get(10, TimeUnit.SECONDS);
		logger.debug("fetched version: {}", version);
	}

	@Test
	public void fetchFromResource() throws Exception {
		URL resource = this.getClass().getClassLoader().getResource("logback.xml");
		String logbackXml = AssetHelper.fetchContent(resource.toString()).toCompletableFuture().get(10,
				TimeUnit.SECONDS);
		logger.debug("fetched logbackXml: {}", logbackXml);
	}

	@Test
	public void fetchToFile() throws Exception {
		Path targetFile = Paths.get("chrome_version.txt");
		Files.deleteIfExists(targetFile);
		assertFalse(Files.exists(targetFile));
		Path fetchedFile = AssetHelper
				.fetch("https://chromedriver.storage.googleapis.com/LATEST_RELEASE_84", targetFile)
				.toCompletableFuture()
				.get(10, TimeUnit.SECONDS);

		logger.debug("fetched to: {}", fetchedFile.toAbsolutePath().toString());

		assertTrue(Files.exists(fetchedFile));
		assertEquals(targetFile.toAbsolutePath().toString(), fetchedFile.toAbsolutePath().toString());
		Files.delete(fetchedFile);
	}

	@Test
	public void unzipRvm() throws Exception {
		Path fetchedFile = AssetHelper.fetch("https://cdn.openfin.co/release/rvm/5.7.0.14")
				.toCompletableFuture()
				.get(60, TimeUnit.SECONDS);

		logger.debug("fetched to: {}", fetchedFile.toAbsolutePath().toString());
		assertTrue(Files.exists(fetchedFile));
		Path folder = AssetHelper.unzip(fetchedFile).toCompletableFuture().get(10, TimeUnit.SECONDS);
		logger.debug("unzipped to: {}", folder.toAbsolutePath().toString());
		assertTrue(Files.exists(folder.resolve("OpenFinRVM.exe")));
	}

	@Test
	public void unzipRuntime() throws Exception {
		Path fetchedFile = AssetHelper.fetch("https://cdn.openfin.co/release/runtime/x64/16.83.50.9")
				.toCompletableFuture()
				.get(180, TimeUnit.SECONDS);

		logger.debug("fetched to: {}", fetchedFile.toAbsolutePath().toString());
		assertTrue(Files.exists(fetchedFile));
		Path folder = AssetHelper.unzip(fetchedFile).toCompletableFuture().get(10, TimeUnit.SECONDS);
		logger.debug("unzipped to: {}", folder.toAbsolutePath().toString());
		assertTrue(Files.exists(folder.resolve("OpenFin/openfin.exe")));
	}
}
