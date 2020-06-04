package com.mijibox.openfin.gateway.gui;

import java.awt.Canvas;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mijibox.openfin.gateway.OpenFinConnection;
import com.mijibox.openfin.gateway.OpenFinGateway;
import com.mijibox.openfin.gateway.ProxyObject;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinDef.HWND;

public class OpenFinCanvas extends Canvas {
	private static Logger logger = LoggerFactory.getLogger(OpenFinCanvas.class);

	private static final long serialVersionUID = 1900507991613110454L;
	private OpenFinGateway gateway;
	private ProxyObject windowProxy;
	private int originalWinStyle;

	private HWND openFinHwnd;
	private HWND previousParent;
	private JsonValue originalWinOpt;
	private JsonObject originalBounds;

	public OpenFinCanvas(OpenFinGateway gateway) {
		this.setVisible(true);
		this.gateway = gateway;
		this.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				if (windowProxy != null) {
					setEmbeddedWindowBounds(0, 0, getWidth(), getHeight());
				}
			}
		});
	}

	private HWND getHwnd(long hwnd) {
		return new WinDef.HWND(Pointer.createConstant(hwnd));
	}

	private CompletionStage<Void> setEmbeddedWindowBounds(int x, int y, int width, int height) {
		JsonObject bounds = Json.createObjectBuilder()
				.add("top", y)
				.add("left", x)
				.add("width", width)
				.add("height", height).build();
		return windowProxy.invoke("setBounds", bounds).thenAccept(v -> {

		});
	}

	public CompletionStage<Void> embedWithManifest(String manifest) {
		return this.gateway.invoke(true, "fin.Application.startFromManifest", Json.createValue(manifest))
				.thenCompose(result -> {
					// application object
					ProxyObject app = result.getProxyObject();
					return app.invoke("getWindow").thenCompose(winResult -> {
						JsonObject winIdentity = ((JsonObject) winResult.getResult()).getJsonObject("identity");
						return embed(winIdentity);
					}).thenCompose(v -> {
						return app.dispose();
					});
				});
	}

	public CompletionStage<Void> embedWithAppOptions(JsonObject appOpts) {
		return this.gateway.invoke(true, "fin.Application.start", appOpts)
				.thenCompose(result -> {
					// application object
					ProxyObject app = result.getProxyObject();
					return app.invoke("getWindow").thenCompose(winResult -> {
						JsonObject winIdentity = ((JsonObject) winResult.getResult()).getJsonObject("identity");
						return embed(winIdentity);
					}).thenCompose(v -> {
						return app.dispose();
					});
				});
	}

	public CompletionStage<Void> embed(JsonObject targetIdentity) {
		if (Platform.isWindows()) {
			long canvasId = Native.getComponentID(this);
			return this.gateway.invoke(true, "fin.Window.wrap", targetIdentity).thenCompose(r -> {
				//create windowProxy from targetIdentity
				logger.debug("will embed: {} into {}", targetIdentity, canvasId);
				this.windowProxy = r.getProxyObject();
				//get original bounds
				return this.windowProxy.invoke("getBounds");
			}).thenCompose(b->{
				this.originalBounds = (JsonObject)b.getResult();
				//get original options
				return this.windowProxy.invoke("getOptions");
			}).thenCompose(optResult -> {
				this.originalWinOpt = optResult.getResult();
				JsonObject newOpts = Json.createObjectBuilder()
						.add("maxHeight", -1)
						.add("minHeight", -1)
						.add("maxWidth", -1)
						.add("minWidth", -1)
						.add("resizable", false)
						.add("frame", false).build();
				//set options when embedded
				return this.windowProxy.invoke("updateOptions", newOpts);
			}).thenCompose(v -> {
				//get native window id
				return this.windowProxy.invoke("getNativeId");
			}).thenCompose(r2 -> {
				JsonString v = (JsonString) r2.getResult();
				long openFinWinId = Long.decode(v.getString());
				this.openFinHwnd = this.getHwnd(openFinWinId);
				this.originalWinStyle = User32.INSTANCE.GetWindowLong(openFinHwnd, User32.GWL_EXSTYLE);
				User32.INSTANCE.ShowWindow(openFinHwnd, User32.SW_HIDE);
				int embeddedStyle = this.originalWinStyle & ~(User32.WS_POPUPWINDOW);
				embeddedStyle = embeddedStyle | User32.WS_CHILD;
				User32.INSTANCE.SetWindowLong(openFinHwnd, User32.GWL_EXSTYLE, embeddedStyle);
				this.previousParent = User32.INSTANCE.SetParent(openFinHwnd, this.getHwnd(canvasId));
				//match the window location and size as the canvas
				return setEmbeddedWindowBounds(0, 0, this.getWidth(), this.getHeight());
			}).thenCompose(rEmbed -> {
				//tell openfin runtime that we have embedded the window.
				OpenFinConnection conn = gateway.getOpenFinInterApplicationBus().getConnection();
				JsonObject payload = Json.createObjectBuilder(targetIdentity)
						.add("parentHwnd", Long.toHexString(canvasId)).build();
				return conn.sendMessage("window-embedded", payload);
			}).thenCompose(a -> {
				//if not already shown, make it visible.
				return windowProxy.invoke("show");
			}).thenAccept(a -> {

			});
		}
		else {
			return CompletableFuture.failedStage(new RuntimeException("Not implemented on this platform"));
		}
	}

	public CompletionStage<Void> unembed() {
		if (this.windowProxy != null) {
			User32.INSTANCE.ShowWindow(openFinHwnd, User32.SW_HIDE);
			User32.INSTANCE.SetParent(openFinHwnd, this.previousParent);
			User32.INSTANCE.SetWindowLong(openFinHwnd, User32.GWL_EXSTYLE, this.originalWinStyle);
			return this.windowProxy.invoke("setBounds", this.originalBounds).thenCompose(r -> {
				return this.windowProxy.invoke("updateOptions", this.originalWinOpt);
			}).thenAccept(v->{
				User32.INSTANCE.ShowWindow(openFinHwnd, User32.SW_SHOW);
			});
		}
		else {
			return CompletableFuture.completedStage(null);
		}
	}

	public ProxyObject getEmbeddedWindow() {
		return this.windowProxy;
	}

}
