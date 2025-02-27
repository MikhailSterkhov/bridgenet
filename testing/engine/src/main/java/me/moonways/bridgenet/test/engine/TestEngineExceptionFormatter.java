package me.moonways.bridgenet.test.engine;

import me.moonways.bridgenet.assembly.OverridenProperty;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TestEngineExceptionFormatter {

    private static final String RESOURCE_PATH = "stacktrace.pattern";
    private static final String ELEMENT_LINE_PREFIX = "!@";
    private static final String
            PH_EXCEPTION_MESSAGE = "${exception_message}",
            PH_EXCEPTION_CLASS = "${exception_class}",
            PH_ELEMENT_LINE = "${element_line}",
            PH_ELEMENT_CLASS = "${element_class}",
            PH_ELEMENT_METHOD = "${element_method}";

    private InputStream getInputStream() {
        return TestEngineExceptionFormatter.class.getClassLoader().getResourceAsStream(RESOURCE_PATH);
    }

    private String readPatternFile() {
        try (InputStreamReader inputStreamReader = new InputStreamReader(getInputStream());
             BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {

            return bufferedReader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String replaceExceptionPlaceholders(Throwable exception, String pattern) {
        return pattern
                .replace(PH_EXCEPTION_CLASS, exception.getClass().getName())
                .replace(PH_EXCEPTION_MESSAGE, Optional.ofNullable(exception.getMessage()).orElse(""));
    }

    private String replaceElementPlaceholders(StackTraceElement element, String pattern) {
        return pattern.substring(2)
                .replace(PH_ELEMENT_LINE, Integer.toString(element.getLineNumber()))
                .replace(PH_ELEMENT_METHOD, element.getMethodName())
                .replace(PH_ELEMENT_CLASS, element.getClassName());
    }

    private String findElementLinePattern(String pattern) {
        return Stream.of(pattern.split("\n"))
                .filter(line -> line.startsWith(ELEMENT_LINE_PREFIX))
                .collect(Collectors.joining("\n"));
    }

    private String getDuplicatedElementsLines(Throwable exception, String pattern) {
        boolean exceptionsPackagesFilter = Boolean.parseBoolean(System.getProperty("testing.exceptions.packagesFilter"));

        String elementLinePattern = findElementLinePattern(pattern);
        Stream<String> stringStream = Stream.of(exception.getStackTrace())
                .filter(stackTraceElement -> !stackTraceElement.isNativeMethod())
                .filter(stackTraceElement -> !exceptionsPackagesFilter || stackTraceElement.getClassName().contains(OverridenProperty.BEANS_PACKAGE.get()))
                .map(stackTraceElement -> replaceElementPlaceholders(stackTraceElement, elementLinePattern));

        boolean stacktraceLimitEnabled = Boolean.parseBoolean(System.getProperty("testing.exceptions.stacktraceLimitEnabled"));
        if (stacktraceLimitEnabled) {
            stringStream = stringStream.limit(Long.parseLong(System.getProperty("testing.exceptions.stacktraceLimit")));
        }

        return stringStream
                .collect(Collectors.joining("\n"));
    }

    private Throwable getFirstCause(Throwable exception) {
        if (exception.getCause() != null) {
            return getFirstCause(exception.getCause());
        }
        return exception;
    }

    public String formatToString(Throwable exception) {
        exception = getFirstCause(exception);

        String stacktracePattern = readPatternFile();
        String duplicatedElementsLines = getDuplicatedElementsLines(exception, stacktracePattern);

        String currentExceptionMessage = replaceExceptionPlaceholders(exception,
                stacktracePattern.replace(findElementLinePattern(stacktracePattern), duplicatedElementsLines));

        Throwable cause = exception.getCause();
        if (cause != null) {
            currentExceptionMessage += formatToString(cause);
        }

        return currentExceptionMessage;
    }
}
