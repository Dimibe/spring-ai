package org.springframework.ai.chat;

import org.springframework.ai.chat.prompt.Prompt;

/**
 * @author Mark Pollack
 * @author Christian Tzolov
 * @author Josh Long
 * @author Arjen Poutsma
 */
class DefaultChatClient implements ChatClient {

	private final ChatCaller modelCall;

	private final ChatClientRequest defaultChatClientRequest;

	public DefaultChatClient(ChatCaller modelCall, ChatClientRequest defaultChatClientRequest) {
		this.modelCall = modelCall;
		this.defaultChatClientRequest = defaultChatClientRequest;
	}

	@Override
	public ChatClientRequest call() {
		return new ChatClientRequest(this.defaultChatClientRequest);
	}

	/**
	 * use the new fluid DSL starting in {@link #call()}
	 * @param prompt the {@link Prompt prompt} object
	 * @return a {@link ChatResponse chat response}
	 */
	@Deprecated(forRemoval = true, since = "1.0.0 M1")
	@Override
	public ChatResponse call(Prompt prompt) {
		return this.modelCall.call(prompt);
	}

}