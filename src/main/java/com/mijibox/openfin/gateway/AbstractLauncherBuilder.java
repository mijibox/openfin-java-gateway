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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.json.JsonObject;

import com.sun.jna.Platform;

public abstract class AbstractLauncherBuilder implements OpenFinLauncherBuilder {

	protected String assetsUrl;
	protected String licenseKey;
	protected String runtimeVersion;
	protected List<String> runtimeOptions;
	protected Path openFinDirectory;
	protected JsonObject startupApp;
	protected boolean injectGatewayScript;

	AbstractLauncherBuilder() {
		this.assetsUrl = "https://cdn.openfin.co";
		this.runtimeOptions = new ArrayList<>();
		this.injectGatewayScript = true;
	}

	@Override
	public OpenFinLauncherBuilder runtimeVersion(String runtimeVersion) {
		this.runtimeVersion = runtimeVersion;
		return this;
	}

	String getRuntimeVersion() {
		return this.runtimeVersion;
	}

	@Override
	public OpenFinLauncherBuilder addRuntimeOption(String runtimeOption) {
		this.runtimeOptions.add(runtimeOption);
		return this;
	}

	List<String> getRuntimeOptions() {
		return this.runtimeOptions;
	}

	@Override
	public OpenFinLauncherBuilder licenseKey(String licenseKey) {
		this.licenseKey = licenseKey;
		return this;
	}

	String getLicenseKey() {
		return this.licenseKey;
	}

	@Override
	public OpenFinLauncherBuilder assetsUrl(String rvmAssetsUrl) {
		this.assetsUrl = rvmAssetsUrl;
		return this;
	}

	String getAssetsUrl() {
		return this.assetsUrl;
	}

	@Override
	public OpenFinLauncherBuilder openFinDirectory(Path openFinDirectory) {
		this.openFinDirectory = openFinDirectory;
		return this;
	}

	Path getOpenFinDirectory() {
		if (this.openFinDirectory == null) {
			if (Platform.isWindows()) {
				String localAppData = System.getenv("LOCALAPPDATA");
				if (localAppData == null) {
					throw new RuntimeException("unable to determine OpenFin directory");
				}
				else {
					this.openFinDirectory = Paths.get(localAppData, "OpenFin");
				}
			}
			else {
				this.openFinDirectory = Paths.get(System.getProperty("user.home"), "OpenFin");
			}
		}
		return this.openFinDirectory;
	}
}
