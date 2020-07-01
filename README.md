# OpenFin Java Gateway


Using [OpenFin API Protocol](https://github.com/HadoukenIO/core/wiki/API-Protocol) and built on top of the InterApplicationBus messaging. It opens the gateway between your Java application and [OpenFin JavaScript V2 API](https://cdn.openfin.co/docs/javascript/stable/index.html).

Using the gateway, you can send and receive OpenFin InterApplicationBus messages natively or invoke **almost** any OpenFin JavaScript V2 API methods.

The following code snippet shows how to connect to OpenFin Runtime and invoke OpenFin JavaScript V2 APIs. 
 
```java
OpenFinGatewayLauncher.newOpenFinGatewayLauncher()
        .launcherBuilder(OpenFinLauncher.newOpenFinLauncherBuilder()
                .runtimeVersion("stable")
                .addRuntimeOption("--v=1")
                .addRuntimeOption("--no-sandbox"))
        .open()
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

If asynchronous programming is not your thing, check out [OpenFin Java-JavaScript Adapter](https://github.com/mijibox/openfin-jjs-adapter), which is built on top of OpenFin Java Gateway.

Methods like [fin.System.monitorExternalProcess](https://developer.openfin.co/docs/javascript/stable/System.html#monitorExternalProcess) that have listener nested inside an object cannot be invoked correctly at the moment. Gateway v2 will have the ability to support such.

## License
Apache License 2.0

The code in this repository is covered by the included license.

However, if you run this code, it may call on the OpenFin RVM or OpenFin Runtime, which are covered by OpenFin’s Developer, Community, and Enterprise licenses. You can learn more about OpenFin licensing at the links listed below or just email OpenFin at support@openfin.co with questions.

https://openfin.co/developer-agreement/ <br/>
https://openfin.co/licensing/
