var openFinApiGateway = (function() {
	let proxyObjectMap = new Map();
	let objIdSequence = 0;
	let debug = false;

	function addProxyObject(obj) {
		let proxyObjId = 'proxy-' + objIdSequence++;
		proxyObjectMap.set(proxyObjId, obj);
		return proxyObjId;
	}

	function getProxyObject(id) {
		return id ? proxyObjectMap.get(id) : null;	
	}
	
	function removeProxyObject(id) {
		proxyObjectMap.delete(id);
	}
	
	function isFunction(obj) {
		return !!(obj && obj.constructor && obj.call && obj.apply);
	}
	
	function stringify(obj) {
		var replaceCircular = function(val, cache) {
			cache = cache || new WeakSet();
			if (val && typeof(val) === 'object') {
				if (cache.has(val)) return '[Circular]';
				cache.add(val);
				var obj = (Array.isArray(val) ? [] : {});
				for(var idx in val) {
					obj[idx] = replaceCircular(val[idx], cache);
				}
				cache.delete(val);
				return obj;
			}
			return val;
		};
		return JSON.stringify(replaceCircular(obj));
	}

	function invokeMethod(obj, method, ...args) {
		if (!obj) {
			var nObj = method.substring(0, method.lastIndexOf('.'));
			obj = eval(nObj);
			let m = eval(method)
			if (!obj || !m) {
				throw new Error('invalid method: ' + method);
			}
			return m.call(obj, ...args);
		} 
		else {
			let evalResult = eval('obj.' + method);
			if (isFunction(evalResult)) {
				return evalResult.call(obj, ...args);
			}
			else {
				throw new Error('invalid instance method: ' + method);
				// return evalResult;
			}
		}
	}
	
	function invokePromise(obj, method, ...args) {
		return new Promise((resolve, reject)=>{
			resolve(invokeMethod(obj, method, ...args));
		});
	}
	
	function sendMessage(dest, topic, payload) {
		if (debug) {
			console.debug('sending message: ' + JSON.stringify(payload));
		}
		fin.InterApplicationBus.send(dest, topic, payload);
	}

	if (typeof fin !== 'undefined') {
		console.info('running in OpenFin runtime');
		fin.Application.getCurrent().then( gatewayApp => {
			let gatewayAppUuid = gatewayApp.identity.uuid;
			let gatewayTopicExec = gatewayAppUuid + '-exec';
			fin.InterApplicationBus.subscribe({uuid: '*'}, gatewayTopicExec, (msg, srcIdentity) => {
				if (debug) {
					console.debug('received message: ' + JSON.stringify(msg));
				}
				let action = msg.action;
				let messageId = msg.messageId;
				let payload = msg.payload;
				
				var sendError = function(errorMessage) {
					console.debug('sendError', errorMessage);
					let errorPayload = {messageId, action: 'error', payload: {error: errorMessage, requestPayload: payload}};
					sendMessage(srcIdentity, gatewayTopicExec, errorPayload);
				};
				
				if (action == 'ping') {
					sendMessage(srcIdentity, gatewayTopicExec, {action: 'pong', messageId, payload:{}});
				}
				else if (action == 'delete') {
					// payload is proxyObjId
					if (getProxyObject(payload)) {
						removeProxyObject(payload);
						sendMessage(srcIdentity, gatewayTopicExec, {action: 'delete-result', messageId, payload: {}});
					}
					else {
						sendError('delete error, proxyObject removed already');
					}
				}
				else if (action == 'quit') {
					sendMessage(srcIdentity, gatewayTopicExec, {action: 'quit-received', messageId, payload: {}});
					fin.Application.getCurrent().then(app => {
						app.quit(true);
					});
				}
				else if (action == 'invoke') {
					let targetObject = getProxyObject(payload.proxyObjId);
					let args = payload.args || [];
					if (payload.proxyObjId && !targetObject) {
						sendError('invoke error, proxyObject removed already');
					}
					else {
						Promise.resolve(invokePromise(targetObject, payload.method, ...args)).then(result =>{
							let resultPayload = {messageId, action: 'invoke-result', payload: {}};
							if (typeof result !== 'undefined') {
								let stringifiedObj = stringify(result);
								let resultPayloadResultObj = JSON.parse(stringifiedObj);
								if (debug) {
									console.debug('invokeMethod: ' + payload.method + ', got result: ' + stringifiedObj);
								}
								resultPayload.payload.result = resultPayloadResultObj
							}
							else {
								if (debug) {
									console.debug('invokeMethod: ' + payload.method + ', got result: ' + result);
								}
							}
							if (payload.proxyResult) {
								resultPayload.payload.proxyObjId = addProxyObject(result);
							}
							sendMessage(srcIdentity, gatewayTopicExec, resultPayload);
						}).catch(e=>{
							console.error('invoke error ', e);
							sendError('invoke error, ' + e.message);
						});
					}
				}
				else if (action == 'add-listener') {
					let iabTopic = payload.iabTopic;
					let targetObject = getProxyObject(payload.proxyObjId);
					if (payload.proxyObjId && !targetObject) {
						sendError('add-listener error, proxyObject removed already');
					}
					else {
						let listener = function() {
							return new Promise(resolve=>{
								let eventPayload = Object.assign([], arguments);
								if (debug) {
									console.debug(iabTopic + ': listener invoked, arguments: ', arguments);
								}
								fin.InterApplicationBus.subscribe(srcIdentity, iabTopic, e =>{
									if (debug) {
										console.debug(iabTopic + ': got listener response: ', e);
									}
									resolve(e);
								});
								fin.InterApplicationBus.send(srcIdentity, iabTopic, eventPayload);
							});
						};
						
						let args = payload.args || [];
						let listenerArgIdx = payload.listenerArgIdx;
						args.splice(listenerArgIdx, 0, listener);

						Promise.resolve(invokePromise(targetObject, payload.method, ...args)).then(result =>{
							let resultPayload = {messageId, action: 'add-listener-result', payload: {}};
							if (payload.proxyResult) {
								resultPayload.payload.proxyObjId = addProxyObject(listener);
							}
							sendMessage(srcIdentity, gatewayTopicExec, resultPayload);
						}).catch(e=>{
							console.error('add-listener error ', e);
							sendError('add-listener error, ' + e.message); 
						});
					}
				}
				else if (action == 'remove-listener') {
					let listener = getProxyObject(payload.proxyListenerId);
					let targetObject = getProxyObject(payload.proxyObjId);
					if (payload.proxyObjId && !targetObject) {
						sendError('remove-listener error, proxyObject removed already');
					}
					else if (payload.proxyListenerId && !listener) {
						sendError('remove-listener error, proxyListener removed already');
					}
					else {
						Promise.resolve(invokePromise(targetObject, payload.method, payload.event, listener)).then(result =>{
							let resultPayload = {messageId, action: 'remove-listener-result', payload};
							sendMessage(srcIdentity, gatewayTopicExec, resultPayload);
						}).catch(e=>{
							console.error('remove-listener error ', e);
							sendError('remove-listener error, ' + e.message); 
						});
					}
				}
			});
		});
	}
	else {
		console.error('not running in OpenFin runtime.');
	}
}());