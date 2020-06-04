# OpenFin Java Gateway


Using [OpenFin API Protocol](https://github.com/HadoukenIO/core/wiki/API-Protocol) and built on top of the InterApplicationBus messaging. It opens the gateway between your Java application and [OpenFin JavaScript V2 API](https://cdn.openfin.co/docs/javascript/stable/index.html).

Using the gateway, you can send and receive OpenFin InterApplicationBus messages natively or invoke any OpenFin JavaScript V2 API methods.

The following code snippet shows how to connect to OpenFin Runtime and invoke OpenFin JavaScript V2 APIs. 
 
```java
OpenFinLauncher.newOpenFinLauncherBuilder()
		.addRuntimeOption("--v=1")
		.open(gatewayListener)
		.thenAccept(gateway -> {
			gateway.invoke("fin.System.getVersion").thenAccept(r -> {
				System.out.println("openfin version: " + r.getResultAsString());
			});

			gateway.invoke(true, "fin.Application.startFromManifest",
					Json.createValue("https://cdn.openfin.co/demos/hello/app.json")).thenAccept(r -> {
						JsonObject appObj = r.getResultAsJsonObject();
						System.out.println("appUuid: " + appObj.getJsonObject("identity").getString("uuid"));
						ProxyObject proxyAppObj = r.getProxyObject();
						proxyAppObj.addListener("on", "closed", (e) -> {
							System.out.println("hello openfin closed, listener got event: " + e);
							gateway.close();
						});
					}).exceptionally(e -> {
						System.err.println("error starting hello openfin app");
						e.printStackTrace();
						gateway.close();
						return null;
					});
		});

```

Under the hood, it launches OpenFin Runtime directly on MacOS or Linux, and via [OpenFin RVM](https://developers.openfin.co/docs/runtime-version-manager) on Windows. If OpenFin Runtime or RVM cannot be found locally, it will be downloaded from OpenFin's server or specified [assetsUrl](https://developers.openfin.co/docs/hosting-runtimes-rvm-and-other-assets).  

More examples using OpenFin Java Gateway can be found in [OpenFin Java Gateway Examples](https://github.com/mijibox/openfin-java-gateway-examples)
