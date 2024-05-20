/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.chat;

import org.springframework.ai.chat.messages.Media;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackWrapper;
import org.springframework.ai.model.function.FunctionCallingOptions;
import org.springframework.ai.model.function.FunctionCallingOptionsBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;
import java.util.function.Consumer;

// todo support plugging in a outputConverter at runtime
// todo figure out stream and list methods

/*
 * @author Mark Pollack
 * @author Christian Tzolov
 * @author Josh Long
 * @author Arjen Poutsma
 */
public interface ChatClient {

	static ChatClientBuilder builder(ChatCaller caller) {
		return new ChatClientBuilder(caller);
	}

	ChatResponse call(Prompt prompt);

	ChatClientRequest call();

	interface PromptSpec<T> {

		T text(String text);

		T text(Resource text, Charset charset);

		T text(Resource text);

		T params(Map<String, Object> p);

		T param(String k, String v);

	}

	abstract class AbstractPromptSpec<T extends AbstractPromptSpec<T>> implements PromptSpec<T> {

		private String text = "";

		private final Map<String, Object> params = new HashMap<>();

		@Override
		public T text(String text) {
			this.text = text;
			return self();
		}

		@Override
		public T text(Resource text, Charset charset) {
			try {
				this.text(text.getContentAsString(charset));
			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}
			return self();
		}

		@Override
		public T text(Resource text) {
			this.text(text, Charset.defaultCharset());
			return self();
		}

		@Override
		public T param(String k, String v) {
			this.params.put(k, v);
			return self();
		}

		@Override
		public T params(Map<String, Object> p) {
			this.params.putAll(p);
			return self();
		}

		protected abstract T self();

		protected String text() {
			return this.text;
		}

		protected Map<String, Object> params() {
			return this.params;
		}

	}

	class UserSpec extends AbstractPromptSpec<UserSpec> implements PromptSpec<UserSpec> {

		private final List<Media> media = new ArrayList<>();

		public UserSpec media(Media... media) {
			this.media.addAll(Arrays.asList(media));
			return self();
		}

		public UserSpec media(MimeType mimeType, URL url) {
			this.media.add(new Media(mimeType, url));
			return self();
		}

		public UserSpec media(MimeType mimeType, Resource resource) {
			this.media.add(new Media(mimeType, resource));
			return self();
		}

		protected List<Media> media() {
			return this.media;
		}

		@Override
		protected UserSpec self() {
			return this;
		}

	}

	class SystemSpec extends AbstractPromptSpec<SystemSpec> implements PromptSpec<SystemSpec> {

		@Override
		protected SystemSpec self() {
			return this;
		}

	}

	class ChatClientRequest {

		private final ChatCaller caller;

		private String userText = "";

		private String systemText = "";

		private ChatOptions chatOptions;

		private FunctionCallingOptions functionCallingOptions;

		private final List<Media> media = new ArrayList<>();

		private final List<String> functionNames = new ArrayList<>();

		private final List<FunctionCallback> functionCallbacks = new ArrayList<>();

		private final List<Message> messages = new ArrayList<>();

		private final Map<String, Object> userParams = new HashMap<>();

		private final Map<String, Object> systemParams = new HashMap<>();

		/* copy constructor */
		ChatClientRequest(ChatClientRequest ccr) {
			this(ccr.caller, ccr.userText, ccr.systemText, ccr.functionCallbacks, ccr.functionNames, ccr.media,
					ccr.chatOptions);
		}

		public ChatClientRequest(ChatCaller caller, String userText, String systemText,
				List<FunctionCallback> functionCallbacks, List<String> functionNames, List<Media> media,
				ChatOptions chatOptions) {

			this.caller = caller;
			this.chatOptions = chatOptions;

			this.userText = userText;
			this.systemText = systemText;

			this.functionNames.addAll(functionNames);
			this.functionCallbacks.addAll(functionCallbacks);
			this.media.addAll(media);
		}

		public ChatClientRequest messages(Message... messages) {
			this.messages.addAll(List.of(messages));
			return this;
		}

		public <T extends ChatOptions> ChatClientRequest options(T options) {
			this.chatOptions = options;
			return this;
		}

		public <I, O> ChatClientRequest function(String name, String description,
				java.util.function.Function<I, O> function) {
			var fcw = FunctionCallbackWrapper.builder(function)
				.withDescription(description)
				.withName(name)
				.withResponseConverter(Object::toString)
				.build();
			this.functionCallbacks.add(fcw);
			return this;
		}

		public ChatClientRequest functions(String... functions) {
			this.functionNames.addAll(List.of(functions));
			return this;
		}

		public ChatClientRequest chatOptions(ChatOptions chatOptions) {
			this.chatOptions = chatOptions;
			return this;
		}

		public ChatClientRequest system(Consumer<SystemSpec> consumer) {
			var ss = new SystemSpec();
			consumer.accept(ss);
			this.systemText = ss.text();
			this.systemParams.putAll(ss.params());
			return this;
		}

		public ChatClientRequest user(Consumer<UserSpec> consumer) {
			var us = new UserSpec();
			consumer.accept(us);
			this.userText = us.text();
			this.userParams.putAll(us.params());
			this.media.addAll(us.media());
			return this;
		}

		public static class ChatResponseSpec {

			private final ChatClientRequest request;

			private final ChatCaller modelCall;

			public ChatResponseSpec(ChatCaller modelCall, ChatClientRequest request) {
				this.modelCall = modelCall;
				this.request = request;
			}

			public <T> T single(ParameterizedTypeReference<T> t) {
				return doSingleWithBeanOutputConverter(new BeanOutputConverter<T>(new ParameterizedTypeReference<>() {
				}));
			}

			private <T> T doSingleWithBeanOutputConverter(BeanOutputConverter<T> boc) {
				var processedUserText = this.request.userText + System.lineSeparator() + System.lineSeparator()
						+ "{format}";
				var chatResponse = doGetChatResponse(processedUserText, boc.getFormat());
				var stringResponse = chatResponse.getResult().getOutput().getContent();
				return boc.convert(stringResponse);
			}

			public <T> T single(Class<T> clzz) {
				Assert.notNull(clzz, "the class must be non-null");
				var boc = new BeanOutputConverter<T>(clzz);
				return doSingleWithBeanOutputConverter(boc);
			}

			private ChatResponse doGetChatResponse(String processedUserText) {
				return this.doGetChatResponse(processedUserText, "");
			}

			private ChatResponse doGetChatResponse(String processedUserText, String formatParam) {
				Map<String, Object> userParams = new HashMap<>(this.request.userParams);
				if (StringUtils.hasText(formatParam)) {
					userParams.put("format", formatParam);
				}

				var messages = new ArrayList<Message>();
				var textsAreValid = (StringUtils.hasText(processedUserText)
						|| StringUtils.hasText(this.request.systemText));
				var messagesAreValid = !this.request.messages.isEmpty();
				Assert.state(!(messagesAreValid && textsAreValid), "you must specify either " + Message.class.getName()
						+ " instances or user/system texts, but not both");
				if (textsAreValid) {
					UserMessage userMessage = null;
					if (!CollectionUtils.isEmpty(userParams)) {
						userMessage = new UserMessage(new PromptTemplate(processedUserText, userParams).render(),
								this.request.media);
					}
					else {
						userMessage = new UserMessage(processedUserText, this.request.media);
					}
					if (StringUtils.hasText(this.request.systemText) || !this.request.systemParams.isEmpty()) {
						var systemMessage = new SystemMessage(
								new PromptTemplate(this.request.systemText, this.request.systemParams).render());
						messages.add(systemMessage);
					}
					messages.add(userMessage);
				}
				else {
					messages.addAll(this.request.messages);
				}
				if (this.request.chatOptions instanceof FunctionCallingOptions functionCallingOptions) {
					// if (this.request.chatOptions instanceof
					// FunctionCallingOptionsBuilder.PortableFunctionCallingOptions
					// functionCallingOptions) {
					if (!this.request.functionNames.isEmpty()) {
						functionCallingOptions.setFunctions(new HashSet<>(this.request.functionNames));
					}
					if (!this.request.functionCallbacks.isEmpty()) {
						functionCallingOptions.setFunctionCallbacks(this.request.functionCallbacks);
					}
				}
				var prompt = new Prompt(messages, this.request.chatOptions);
				return this.modelCall.call(prompt);
			}

			public ChatResponse chatResponse() {
				return doGetChatResponse(this.request.userText);
			}

			public String content() {
				return doGetChatResponse(this.request.userText).getResult().getOutput().getContent();
			}

			@SuppressWarnings("unused")
			public <T> Collection<T> list(Class<T> clzz) {
				return single(new ParameterizedTypeReference<List<T>>() {
				});
			}

			public <T> Collection<T> list(ParameterizedTypeReference<List<T>> ptr) {
				return single(ptr);
			}

		}

		public ChatResponseSpec chat() {
			return new ChatResponseSpec(this.caller, this);
		}

	}

	class ChatClientBuilder {

		private final ChatClientRequest defaultRequest;

		private final ChatCaller modelCall;

		ChatClientBuilder(ChatCaller modelCall) {
			Assert.notNull(modelCall, "the " + ChatCaller.class.getName() + " must be non-null");
			this.modelCall = modelCall;
			this.defaultRequest = new ChatClientRequest(modelCall, "", "", List.of(), List.of(), List.of(), null);
		}

		public ChatClient build() {
			return new DefaultChatClient(this.modelCall, this.defaultRequest);
		}

		public ChatClientBuilder defaultChatOptions(ChatOptions chatOptions) {
			this.defaultRequest.chatOptions(chatOptions);
			return this;
		}

		public ChatClientBuilder defaultUser(Consumer<UserSpec> userSpecConsumer) {
			this.defaultRequest.user(userSpecConsumer);
			return this;
		}

		public ChatClientBuilder defaultSystem(Consumer<SystemSpec> systemSpecConsumer) {
			this.defaultRequest.system(systemSpecConsumer);
			return this;
		}

		public <I, O> ChatClientBuilder defaultFunctionWrappers(String name, String description,
				java.util.function.Function<I, O> function) {
			this.defaultRequest.function(name, description, function);
			return this;
		}

		public ChatClientBuilder defaultFunctions(String... functionNames) {
			this.defaultRequest.functions(functionNames);
			return this;
		}

	}

	@Deprecated(since = "1.0.0 M1", forRemoval = true)
	default String call(String message) {
		var prompt = new Prompt(new UserMessage(message));
		var generation = call(prompt).getResult();
		return (generation != null) ? generation.getOutput().getContent() : "";
	}

	@Deprecated(since = "1.0.0 M1", forRemoval = true)
	default String call(Message... messages) {
		var prompt = new Prompt(Arrays.asList(messages));
		var generation = call(prompt).getResult();
		return (generation != null) ? generation.getOutput().getContent() : "";
	}

}
