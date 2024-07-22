package me.moonways.bridgenet.rest.server.repository;

import me.moonways.bridgenet.rest.api.HttpListener;
import me.moonways.bridgenet.rest.model.HttpMethod;
import me.moonways.bridgenet.rest.model.HttpRequest;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.Objects;

/**
 * Класс, представляющий обработчик репозитория HTTP-запросов.
 * Содержит информацию о пути URI, методе HTTP, вызове обработчика и флаге асинхронности.
 */
@Getter
@Builder
@ToString
public class HttpRequestHandler {

    private final String uri;
    private final HttpMethod method;
    private final HttpListener invocation;
    private final boolean notAuthorized;
    private final boolean isAsynchronous;

    /**
     * Проверяет, может ли текущий обработчик обработать данный HTTP-запрос.
     *
     * @param httpRequest HTTP-запрос
     * @return {@code true}, если обработчик может обработать запрос, иначе {@code false}
     */
    public boolean canProcess(HttpRequest httpRequest) {
        return (Objects.equals(method, HttpMethod.ALL) || Objects.equals(method, httpRequest.getMethod()));
    }
}