/*
 * Copyright 2024-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.chat;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

/**
 * @author Christian Tzolov
 */
@ExtendWith(MockitoExtension.class)
public class Main {

	@Mock
	ChatCaller modelCaller;

	@Test
	public void test() throws MalformedURLException {

		var url = new URL("https://docs.spring.io/spring-ai/reference/1.0-SNAPSHOT/_images/multimodal.test.png");

		ChatClient client = ChatClient.builder(modelCaller)
			.defaultSystem(s -> s.text("System text {music}"))
			.defaultUser(u -> u.param("music", "Jazz"))
			.defaultFunctions("function1")
			.build();

		String response = client.call()
			.user(u -> u.text("User text {music}").param("music", "Rock").media(MimeTypeUtils.IMAGE_PNG, url))
			.chat()
			.single(String.class);

	}

}
