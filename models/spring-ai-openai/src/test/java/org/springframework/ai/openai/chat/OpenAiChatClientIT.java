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
package org.springframework.ai.openai.chat;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Media;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.model.function.FunctionCallbackWrapper;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiTestConfiguration;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.tool.MockWeatherService;
import org.springframework.ai.openai.testutils.AbstractIT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = OpenAiTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiChatClientIT extends AbstractIT {

	private static final Logger logger = LoggerFactory.getLogger(OpenAiModelCallerIT.class);

	@Value("classpath:/prompts/system-message.st")
	private Resource systemTextResource;

	@Test
	void roleTest() {

		ChatResponse response = ChatClient.builder(modelCaller).build().call()
				.system(s -> s.text(systemTextResource).param("name", "Bob").param("voice", "pirate"))
				.user(u -> u.text("Tell me about 3 famous pirates from the Golden Age of Piracy and what they did"))
				.chat().chatResponse();

		System.out.println(response);
		// UserMessage userMessage = new UserMessage(
		// "Tell me about 3 famous pirates from the Golden Age of Piracy and what they
		// did.");
		// SystemPromptTemplate systemPromptTemplate = new
		// SystemPromptTemplate(systemResource);
		// Message systemMessage = systemPromptTemplate.createMessage(Map.of("name",
		// "Bob", "voice", "pirate"));
		// Prompt prompt = new Prompt(List.of(userMessage, systemMessage));
		// ChatResponse response = modelCaller.call(prompt);
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResults().get(0).getOutput().getContent()).contains("Blackbeard");
		// needs fine tuning... evaluateQuestionAndAnswer(request, response, false);
	}

	@Test
	void listOutputConverter() {

		// TODO: there is a problem here.
		Collection<String> list = ChatClient.builder(modelCaller).build().call()
				.user(u -> u.text("List five {subject}").param("subject", "ice cream flavors"))
				.chat().list(String.class);

		// DefaultConversionService conversionService = new DefaultConversionService();
		// ListOutputConverter outputConverter = new
		// ListOutputConverter(conversionService);

		// String format = outputConverter.getFormat();
		// String template = """
		// List five {subject}
		// {format}
		// """;
		// PromptTemplate promptTemplate = new PromptTemplate(template,
		// Map.of("subject", "ice cream flavors", "format", format));
		// Prompt prompt = new Prompt(promptTemplate.createMessage());
		// Generation generation = this.modelCaller.call(prompt).getResult();

		// List<String> list =
		// outputConverter.convert(generation.getOutput().getContent());
		assertThat(list).hasSize(5);

	}

	@Test
	void mapOutputConverter() {

		Map<String, Object> result = ChatClient.builder(modelCaller).build().call()
				.user(u -> u.text("Provide me a List of {subject}")
						.param("subject", "an array of numbers from 1 to 9 under they key name 'numbers'"))
				.chat().single(new ParameterizedTypeReference<Map<String, Object>>() {
				});

		// MapOutputConverter outputConverter = new MapOutputConverter();

		// String format = outputConverter.getFormat();
		// String template = """
		// Provide me a List of {subject}
		// {format}
		// """;
		// PromptTemplate promptTemplate = new PromptTemplate(template,
		// Map.of("subject", "an array of numbers from 1 to 9 under they key name
		// 'numbers'", "format", format));
		// Prompt prompt = new Prompt(promptTemplate.createMessage());
		// Generation generation = modelCaller.call(prompt).getResult();

		// Map<String, Object> result =
		// outputConverter.convert(generation.getOutput().getContent());
		assertThat(result.get("numbers")).isEqualTo(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9));
	}

	@Test
	void beanOutputConverter() {

		ActorsFilms actorsFilms = ChatClient.builder(modelCaller).build().call()
				.user(u -> u.text("Generate the filmography for a random actor."))
				.chat().single(ActorsFilms.class);

		// BeanOutputConverter<ActorsFilms> outputConverter = new
		// BeanOutputConverter<>(ActorsFilms.class);

		// String format = outputConverter.getFormat();
		// String template = """
		// Generate the filmography for a random actor.
		// {format}
		// """;
		// PromptTemplate promptTemplate = new PromptTemplate(template, Map.of("format",
		// format));
		// Prompt prompt = new Prompt(promptTemplate.createMessage());
		// Generation generation = modelCaller.call(prompt).getResult();

		// ActorsFilms actorsFilms =
		// outputConverter.convert(generation.getOutput().getContent());
		logger.info("" + actorsFilms);
		assertThat(actorsFilms.getActor()).isNotBlank();
	}

	record ActorsFilmsRecord(String actor, List<String> movies) {
	}

	@Test
	void beanOutputConverterRecords() {

		ActorsFilmsRecord actorsFilms = ChatClient.builder(modelCaller)
				.build().call()
				.user(u -> u.text("Generate the filmography of 5 movies for Tom Hanks."))
				.chat().single(ActorsFilmsRecord.class);

		// BeanOutputConverter<ActorsFilmsRecord> outputConverter = new
		// BeanOutputConverter<>(ActorsFilmsRecord.class);

		// String format = outputConverter.getFormat();
		// String template = """
		// Generate the filmography of 5 movies for Tom Hanks.
		// {format}
		// """;
		// PromptTemplate promptTemplate = new PromptTemplate(template, Map.of("format",
		// format));
		// Prompt prompt = new Prompt(promptTemplate.createMessage());
		// Generation generation = modelCaller.call(prompt).getResult();

		// ActorsFilmsRecord actorsFilms =
		// outputConverter.convert(generation.getOutput().getContent());
		logger.info("" + actorsFilms);
		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	@Test
	void beanStreamOutputConverterRecords() {

		BeanOutputConverter<ActorsFilmsRecord> outputConverter = new BeanOutputConverter<>(ActorsFilmsRecord.class);

		String format = outputConverter.getFormat();
		String template = """
				Generate the filmography of 5 movies for Tom Hanks.
				{format}
				""";
		PromptTemplate promptTemplate = new PromptTemplate(template, Map.of("format", format));
		Prompt prompt = new Prompt(promptTemplate.createMessage());

		String generationTextFromStream = streamingChatClient.stream(prompt)
				.collectList()
				.block()
				.stream()
				.map(ChatResponse::getResults)
				.flatMap(List::stream)
				.map(Generation::getOutput)
				.map(AssistantMessage::getContent)
				.collect(Collectors.joining());

		ActorsFilmsRecord actorsFilms = outputConverter.convert(generationTextFromStream);
		logger.info("" + actorsFilms);
		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	@Test
	void functionCallTest() {

		ChatResponse response = ChatClient.builder(modelCaller)
				.build()
				.call()
				.user(u -> u.text("What's the weather like in San Francisco, Tokyo, and Paris?"))
				// TODO how to use the protable function calling options internally.
				// Perhaps the ModelCaller a emptyOptions() method needs to be provided.
				.options(OpenAiChatOptions.builder().build())
				.function("getCurrentWeather", "Get the weather in location", new MockWeatherService())
				.chat()
				.chatResponse();

		// UserMessage userMessage = new UserMessage("What's the weather like in San
		// Francisco, Tokyo, and Paris?");

		// List<Message> messages = new ArrayList<>(List.of(userMessage));

		// var promptOptions = OpenAiChatOptions.builder()
		// .withModel(OpenAiApi.ChatModel.GPT_4_TURBO_PREVIEW.getValue())
		// .withFunctionCallbacks(List.of(FunctionCallbackWrapper.builder(new
		// MockWeatherService())
		// .withName("getCurrentWeather")
		// .withDescription("Get the weather in location")
		// .withResponseConverter((response) -> "" + response.temp() + response.unit())
		// .build()))
		// .build();

		// ChatResponse response = modelCaller.call(new Prompt(messages, promptOptions));

		logger.info("Response: {}", response);

		assertThat(response.getResult().getOutput().getContent()).containsAnyOf("30.0", "30");
		assertThat(response.getResult().getOutput().getContent()).containsAnyOf("10.0", "10");
		assertThat(response.getResult().getOutput().getContent()).containsAnyOf("15.0", "15");
	}

	@Test
	void streamFunctionCallTest() {

		UserMessage userMessage = new UserMessage("What's the weather like in San Francisco, Tokyo, and Paris?");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = OpenAiChatOptions.builder()
				// .withModel(OpenAiApi.ChatModel.GPT_4_TURBO_PREVIEW.getValue())
				.withFunctionCallbacks(List.of(FunctionCallbackWrapper.builder(new MockWeatherService())
						.withName("getCurrentWeather")
						.withDescription("Get the weather in location")
						.withResponseConverter((response) -> "" + response.temp() + response.unit())
						.build()))
				.build();

		Flux<ChatResponse> response = streamingChatClient.stream(new Prompt(messages, promptOptions));

		String content = response.collectList()
				.block()
				.stream()
				.map(ChatResponse::getResults)
				.flatMap(List::stream)
				.map(Generation::getOutput)
				.map(AssistantMessage::getContent)
				.collect(Collectors.joining());
		logger.info("Response: {}", content);

		assertThat(content).containsAnyOf("30.0", "30");
		assertThat(content).containsAnyOf("10.0", "10");
		assertThat(content).containsAnyOf("15.0", "15");
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "gpt-4-vision-preview", "gpt-4o" })
	void multiModalityEmbeddedImage(String modelName) throws IOException {

		ChatResponse response = ChatClient.builder(modelCaller)
				.build()
				.call()
				// TODO consider adding model(...) method to ChatClient as a shortcut to
				// OpenAiChatOptions.builder().withModel(modelName).build()
				.options(OpenAiChatOptions.builder().withModel(modelName).build())
				.user(u -> u.text("Explain what do you see on this picture?")
						.media(MimeTypeUtils.IMAGE_PNG, new ClassPathResource("/test.png")))
				.chat()
				.chatResponse();

		// var imageData = new ClassPathResource("/test.png");

		// var userMessage = new UserMessage("Explain what do you see on this picture?",
		// List.of(new Media(MimeTypeUtils.IMAGE_PNG, imageData)));

		// var response = modelCaller
		// .call(new Prompt(List.of(userMessage),
		// OpenAiChatOptions.builder().withModel(modelName).build()));

		logger.info(response.getResult().getOutput().getContent());
		assertThat(response.getResult().getOutput().getContent()).contains("bananas", "apple");
		assertThat(response.getResult().getOutput().getContent()).containsAnyOf("bowl", "basket");
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "gpt-4-vision-preview", "gpt-4o" })
	void multiModalityImageUrl(String modelName) throws IOException {

		// TODO: add url method that wrapps the checked exception.
		URL url = new URL("https://docs.spring.io/spring-ai/reference/1.0-SNAPSHOT/_images/multimodal.test.png");

		ChatResponse response = ChatClient.builder(modelCaller)
				.build()
				.call()
				// TODO consider adding model(...) method to ChatClient as a shortcut to
				// OpenAiChatOptions.builder().withModel(modelName).build()
				.options(OpenAiChatOptions.builder().withModel(modelName).build())
				.user(u -> u.text("Explain what do you see on this picture?").media(MimeTypeUtils.IMAGE_PNG, url))
				.chat()
				.chatResponse();

		// var userMessage = new UserMessage("Explain what do you see on this picture?",
		// List
		// .of(new Media(MimeTypeUtils.IMAGE_PNG,
		// new
		// URL("https://docs.spring.io/spring-ai/reference/1.0-SNAPSHOT/_images/multimodal.test.png"))));

		// ChatResponse response = modelCaller
		// .call(new Prompt(List.of(userMessage),
		// OpenAiChatOptions.builder().withModel(modelName).build()));

		logger.info(response.getResult().getOutput().getContent());
		assertThat(response.getResult().getOutput().getContent()).contains("bananas", "apple");
		assertThat(response.getResult().getOutput().getContent()).containsAnyOf("bowl", "basket");
	}

	@Test
	void streamingMultiModalityImageUrl() throws IOException {

		var userMessage = new UserMessage("Explain what do you see on this picture?", List
				.of(new Media(MimeTypeUtils.IMAGE_PNG,
						new URL("https://docs.spring.io/spring-ai/reference/1.0-SNAPSHOT/_images/multimodal.test.png"))));

		Flux<ChatResponse> response = streamingChatClient.stream(new Prompt(List.of(userMessage),
				OpenAiChatOptions.builder().withModel(OpenAiApi.ChatModel.GPT_4_VISION_PREVIEW.getValue()).build()));

		String content = response.collectList()
				.block()
				.stream()
				.map(ChatResponse::getResults)
				.flatMap(List::stream)
				.map(Generation::getOutput)
				.map(AssistantMessage::getContent)
				.collect(Collectors.joining());
		logger.info("Response: {}", content);
		assertThat(content).contains("bananas", "apple");
		assertThat(content).containsAnyOf("bowl", "basket");
	}

}