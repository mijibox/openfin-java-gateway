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
import java.util.concurrent.CompletionStage;

import javax.json.JsonObject;

import com.mijibox.openfin.gateway.OpenFinGateway.OpenFinGatewayListener;

public interface OpenFinLauncherBuilder {
	/**
	 * where to download OpenFin assets, default to "https://cdn.openfin.co"
	 * @param assetsUrl
	 * @return
	 */
	OpenFinLauncherBuilder assetsUrl(String assetsUrl);
	
	/**
	 * the license key provided by OpenFin
	 * @param licenseKey
	 * @return
	 */
	OpenFinLauncherBuilder licenseKey(String licenseKey);

	/**
	 * OpenFin runtime version, default to "stable"
	 * @param runtimeVersion
	 * @return
	 */
	OpenFinLauncherBuilder runtimeVersion(String runtimeVersion);

	/**
	 * Additional OpenFin Runtime options.
	 * @param runtimeOption
	 * @return
	 */
	OpenFinLauncherBuilder addRuntimeOption(String runtimeOption);

	/**
	 * Directory to find OpenFin RVM or Runtime, default to "%LOCALAPPDATA\\OpenFin" on Windows and "~/OpenFin" on Linux/Mac.
	 * @param openFinDirectory
	 * @return
	 */
	OpenFinLauncherBuilder openFinDirectory(Path openFinDirectory);

	/**
	 * When opening the OpenFin Java Gateway, replace default application options with this one. 
	 * @param startupAppOptions
	 * @return
	 */
	OpenFinLauncherBuilder startupApp(JsonObject startupAppOptions);

	/**
	 * When not using default application options, prepend gateway javascript to preloadScripts. 
	 * @param injectGatewayScript
	 * @return
	 */
	OpenFinLauncherBuilder injectGatewayScript(boolean injectGatewayScript);

	/**
	 * Open the OpenFin Java Gateway
	 * @param listener
	 * @return
	 */
	CompletionStage<OpenFinGateway> open(OpenFinGatewayListener listener);
}
