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

import static org.junit.Assert.assertEquals;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;

import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(Parameterized.class)
public class OpenFinGatewayApiTest {
	final static Logger logger = LoggerFactory.getLogger(OpenFinGatewayApiTest.class);

	@Parameters
	public static String[] data() {
		return new String[] {
//				"10.66.41.18",
//				"11.69.42.29",
//				"12.69.43.22",
//				"13.76.45.15",
//				"14.78.48.16",
//				"15.80.50.34",
				"16.83.50.9"
		};
	}

	private static OpenFinGateway apiGateway;
	private String runtimeVersion;

	public OpenFinGatewayApiTest(String runtimeVersion) throws Exception {
		if (apiGateway != null) {
			apiGateway.close().toCompletableFuture().get(20, TimeUnit.SECONDS);
			apiGateway = null;
		}
		System.setProperty("com.mijibox.openfin.gateway.showConsole", "true");
		this.runtimeVersion = runtimeVersion;
		logger.debug("runtime version: {}", this.runtimeVersion);
		apiGateway = OpenFinLauncher.newOpenFinLauncherBuilder()
				.runtimeVersion(this.runtimeVersion)
				.addRuntimeOption("--v=1")
				.addRuntimeOption("--no-sandbox")
				.open(null)
				.exceptionally(e -> {
					e.printStackTrace();
					return null;
				})
				.toCompletableFuture().get(120, TimeUnit.SECONDS);

	}

	@AfterClass
	public static void teardown() throws Exception {
		apiGateway.close().toCompletableFuture().get(20, TimeUnit.SECONDS);
		apiGateway = null;
	}

	@Test
	public void invokeNoArg() throws Exception {
		String invokeResult = apiGateway.invoke("fin.System.getVersion").thenApply(r -> {
			return r.getResultAsString();
		}).toCompletableFuture().get(20, TimeUnit.SECONDS);
		logger.debug("fin.System.getVersion returns: {}", invokeResult);
		assertEquals(runtimeVersion, invokeResult);
	}

	@Test
	public void invokeWithArgs() throws Exception {
		String appUuid = UUID.randomUUID().toString();
		JsonObject appOpts = Json.createObjectBuilder()
				.add("uuid", appUuid)
				.add("name", appUuid)
				.add("url", "https://www.google.com")
				.add("autoShow", true)
				.build();
		apiGateway.invoke(true, "fin.Application.start", appOpts)
				.thenCompose(result -> {
					// application object
					ProxyObject app = result.getProxyObject();
					return app.invoke(true, "getWindow");
				})
				.thenCompose(result -> {
					// window object
					ProxyObject win = result.getProxyObject();
					return win.invoke("close");
				})
				.toCompletableFuture().get(20, TimeUnit.SECONDS);
	}

	@Test
	public void addInstanceListener() throws Exception {
		CompletableFuture<?> listenerFuture = new CompletableFuture<>();
		String appUuid = UUID.randomUUID().toString();
		JsonObject appOpts = Json.createObjectBuilder()
				.add("uuid", appUuid)
				.add("name", appUuid)
				.add("url", "https://www.google.com")
				.add("autoShow", true)
				.build();
		apiGateway.invoke(true, "fin.Application.start", appOpts)
				.thenCompose(result -> {
					// application object
					ProxyObject app = result.getProxyObject();
					return app.addListener("addListener", "window-closing", e -> {
						listenerFuture.complete(null);
						return null;
					}).thenCompose(v -> {
						return app.invoke(true, "getWindow");
					});
				}).thenAccept(result -> {
					// window object
					ProxyObject win = result.getProxyObject();
					win.invoke("close");
				});
		listenerFuture.get(10, TimeUnit.SECONDS);
	}

	@Test
	public void addRemoveListeners() throws Exception {
		AtomicInteger invokeCnt = new AtomicInteger(0);
		CompletableFuture<?> listenerFuture1 = new CompletableFuture<>();
		CompletableFuture<?> listenerFuture2 = new CompletableFuture<>();
		OpenFinEventListener listener1 = (e -> {
			logger.debug("listener 1 got window-created event: {}", e);
			invokeCnt.incrementAndGet();
			listenerFuture1.complete(null);
			return null;
		});
		OpenFinEventListener listener2 = (e -> {
			logger.debug("listener 2 got window-created event: {}", e);
			invokeCnt.incrementAndGet();
			listenerFuture2.complete(null);
			return null;
		});
		apiGateway.addListener("fin.System.addListener", "window-created", listener1).thenCompose(proxyListener1 -> {
			return apiGateway.addListener("fin.System.addListener", "window-created", listener2);
		}).thenCompose(proxyListener2 -> {
			return apiGateway.removeListener("fin.System.removeListener", "window-created", proxyListener2);
		}).thenRun(() -> {
			try {
				this.invokeWithArgs();
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		}).exceptionally(e -> {
			logger.error("error running test addRemoveListeners", e);
			return null;
		});
		listenerFuture1.get(10, TimeUnit.SECONDS);
		try {
			// no need to wait long, if listener 1 is invoked, listener 2 should be also
			// invoked.
			listenerFuture2.get(2, TimeUnit.SECONDS);
		}
		catch (TimeoutException e) {
			// should have time out, and it's OK.
		}
		assertEquals(1, invokeCnt.get());
		LockSupport.park();
	}

	@Test
	public void invokeError() throws Exception {
		CompletableFuture<?> errorFuture = new CompletableFuture<>();
		apiGateway.invoke("fin.System.getVversion").exceptionally(e -> {
			logger.debug("expected error", e);
			errorFuture.complete(null);
			return null;
		});
		errorFuture.get(5, TimeUnit.SECONDS);
	}

	@Test
	public void addListenerError() throws Exception {
		CompletableFuture<?> errorFuture = new CompletableFuture<>();
		apiGateway.addListener("fin.System.dddListener", "application-closed", e -> {
			return null;
		}).exceptionally(e -> {
			logger.debug("expected error", e);
			errorFuture.complete(null);
			return null;
		});
		errorFuture.get(5, TimeUnit.SECONDS);
	}

	@Test
	public void addInstanceListenerError() throws Exception {
		CompletableFuture<?> errorFuture = new CompletableFuture<>();
		String appUuid = UUID.randomUUID().toString();
		JsonObject appOpts = Json.createObjectBuilder()
				.add("uuid", appUuid)
				.add("name", appUuid)
				.add("url", "https://www.google.com")
				.add("autoShow", true)
				.build();
		apiGateway.invoke(true, "fin.Application.start", appOpts)
				.thenCompose(result -> {
					// application object
					ProxyObject app = result.getProxyObject();
					return app.invoke(true, "getWindow");
				}).thenAccept(result -> {
					// window object
					ProxyObject win = result.getProxyObject();
					win.addListener("onn", "closed", e -> {
						return null;
					}).exceptionally(e -> {
						logger.debug("expected error", e);
						errorFuture.complete(null);
						return null;
					});
					win.invoke("close");
				});
		errorFuture.get(10, TimeUnit.SECONDS);
	}

	@Test
	public void removeInstanceListenerError() throws Exception {
		CompletableFuture<?> errorFuture = new CompletableFuture<>();
		String appUuid = UUID.randomUUID().toString();
		JsonObject appOpts = Json.createObjectBuilder()
				.add("uuid", appUuid)
				.add("name", appUuid)
				.add("url", "https://www.google.com")
				.add("autoShow", true)
				.build();
		apiGateway.invoke(true, "fin.Application.start", appOpts)
				.thenCompose(result -> {
					// application object
					ProxyObject app = result.getProxyObject();
					return app.invoke(true, "getWindow");
				}).thenAccept(result -> {
					// window object
					ProxyObject win = result.getProxyObject();
					win.addListener("on", "closed", e -> {
						return null;
					}).thenCompose(listener -> {
						return win.removeListener("rremoveListener", "closed", listener);
					})
							.exceptionally(e -> {
								logger.debug("expected error", e);
								errorFuture.complete(null);
								return null;
							});
					win.invoke("close");
				});
		errorFuture.get(10, TimeUnit.SECONDS);
	}

	@Test
	public void removeListenerError() throws Exception {
		CompletableFuture<?> errorFuture = new CompletableFuture<>();
		apiGateway.addListener("fin.System.addListener", "application-closed", e -> {
			return null;
		}).thenCompose(listener -> {
			return apiGateway.removeListener("fin.System.rremoveListener", "application-closed", listener);
		}).exceptionally(e -> {
			logger.debug("expected error", e);
			errorFuture.complete(null);
			return null;
		});
		errorFuture.get(5, TimeUnit.SECONDS);
	}

	@Test
	public void invokeInstanceMethodError() throws Exception {
		CompletableFuture<?> errorFuture = new CompletableFuture<>();
		String appUuid = UUID.randomUUID().toString();
		JsonObject appOpts = Json.createObjectBuilder()
				.add("uuid", appUuid)
				.add("name", appUuid)
				.add("url", "https://www.google.com")
				.add("autoShow", true)
				.build();
		apiGateway.invoke(true, "fin.Application.start", appOpts)
				.thenCompose(result -> {
					// application object
					ProxyObject app = result.getProxyObject();
					return app.invoke(true, "getWindow");
				}).thenAccept(result -> {
					// window object
					ProxyObject win = result.getProxyObject();
					win.invoke("ccc").exceptionally(e -> {
						logger.debug("expected error", e);
						errorFuture.complete(null);
						return null;
					});
					win.invoke("close");
				});
		errorFuture.get(10, TimeUnit.SECONDS);
	}

	@Test
	public void deleteProxyObject() throws Exception {
		CompletableFuture<?> errorFuture = new CompletableFuture<>();
		String appUuid = UUID.randomUUID().toString();
		JsonObject appOpts = Json.createObjectBuilder()
				.add("uuid", appUuid)
				.add("name", appUuid)
				.add("url", "https://www.google.com")
				.add("autoShow", true)
				.build();
		apiGateway.invoke(true, "fin.Application.start", appOpts)
				.thenAccept(result -> {
					// application object
					ProxyObject app = result.getProxyObject();
					logger.debug("got app: {}", app);
					app.invoke(true, "getWindow").thenAccept(r -> {
						// make sure this method works first.
						logger.debug("got window: {}", r.getResult());
						r.getProxyObject().invoke("close");

					}).thenCompose(v -> {
						return app.dispose();
					}).thenAccept(v -> {
						app.invoke("getWindow").exceptionally(e -> {
							logger.debug("expected error", e);
							errorFuture.complete(null);
							return null;
						});
					});
				});
		errorFuture.get(10, TimeUnit.SECONDS);
	}

	@Test
	public void getApiError() throws Exception {
		CompletableFuture<?> errorFuture = new CompletableFuture<>();
		apiGateway.invoke(true, "fin.Application.wrap", JsonValue.EMPTY_JSON_OBJECT)
				.thenAccept(result -> {
				}).exceptionally(e -> {
					logger.debug("expected error", e);
					errorFuture.complete(null);
					return null;
				});
		errorFuture.get(10, TimeUnit.SECONDS);
	}

	@Test
	public void noArgListener() throws Exception {
		String channelName = UUID.randomUUID().toString();
		CompletableFuture<?> listenerInvokedFuture = new CompletableFuture<>();
		// register noArg listener
		apiGateway.addListener("fin.InterApplicationBus.Channel.onChannelConnect", e -> {
			System.out.println("channel connected: " + e);
			listenerInvokedFuture.complete(null);
			return null;
		}).thenCompose(v -> {
			// create channel provider
			return apiGateway.invoke("fin.InterApplicationBus.Channel.create", Json.createValue(channelName));
		}).thenCompose(r -> {
			return apiGateway.invoke("fin.InterApplicationBus.Channel.connect", Json.createValue(channelName));
		});
		listenerInvokedFuture.get(5, TimeUnit.SECONDS);
	}

	@Test
	public void instanceNoArgListener() throws Exception {
		String channelName = UUID.randomUUID().toString();
		CompletableFuture<?> listenerInvokedFuture = new CompletableFuture<>();
		// create channel provider
		apiGateway.invoke(true, "fin.InterApplicationBus.Channel.create", Json.createValue(channelName))
				.thenCompose(r -> {
					// register noArg listener
					return r.getProxyObject().addListener("onConnection", e -> {
						listenerInvokedFuture.complete(null);
						return null;
					});
				}).thenCompose(r -> {
					return apiGateway.invoke("fin.InterApplicationBus.Channel.connect", Json.createValue(channelName));
				});
		listenerInvokedFuture.get(5, TimeUnit.SECONDS);
	}

	@Test
	public void actionListener() throws Exception {
		String channelName = UUID.randomUUID().toString();
		String actionName = "MyChannelAction";
		CompletableFuture<?> resultFuture = new CompletableFuture<>();
		// register noArg listener
		apiGateway.invoke(true, "fin.InterApplicationBus.Channel.create", Json.createValue(channelName))
				.thenCompose(r -> {
					return r.getProxyObject().addListener("register", actionName, e -> {
						return Json.createValue("HoHoHo:" + e.getString(0));
					});
				})
				.thenCompose(r -> {
					return apiGateway.invoke(true, "fin.InterApplicationBus.Channel.connect",
							Json.createValue(channelName));
				})
				.thenCompose(r -> {
					return r.getProxyObject().invoke("dispatch", Json.createValue(actionName), Json.createValue("GGYY"))
							.thenAccept(ar -> {
								resultFuture.complete(null);
							});
				});
		resultFuture.get(5, TimeUnit.SECONDS);
	}

}
