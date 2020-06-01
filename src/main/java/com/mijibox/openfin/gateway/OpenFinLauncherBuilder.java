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

import com.mijibox.openfin.gateway.OpenFinGateway.OpenFinGatewayListener;

public interface OpenFinLauncherBuilder {
	OpenFinLauncherBuilder assetsUrl(String assetsUrl);
	
	OpenFinLauncherBuilder licenseKey(String licenseKey);

	OpenFinLauncherBuilder runtimeVersion(String runtimeVersion);

	OpenFinLauncherBuilder addRuntimeOption(String runtimeOption);

	OpenFinLauncherBuilder openFinDirectory(Path openFinDirectory);

	CompletionStage<OpenFinGateway> open(OpenFinGatewayListener listener);
}
