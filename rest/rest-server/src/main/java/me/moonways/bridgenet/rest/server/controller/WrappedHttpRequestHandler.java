package me.moonways.bridgenet.rest.server.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.profiler.BridgenetDataLogger;
import me.moonways.bridgenet.profiler.ProfilerType;
import me.moonways.bridgenet.rest.server.HttpServerConfig;
import me.moonways.bridgenet.rest.server.controller.undefined.UndefinedHttpController;
import me.moonways.bridgenet.rest.server.controller.verify.VerificationConfig;
import me.moonways.bridgenet.rest.server.controller.verify.VerifyHelper;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.RequestLine;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;

import java.io.IOException;
import java.net.HttpURLConnection;

@Log4j2
@RequiredArgsConstructor
public class WrappedHttpRequestHandler implements HttpRequestHandler {

    private static final String PUBLIC_API = "PUBLIC";
    private static final String PRIVATE_API = "PRIVATE";

    private static final String REQUEST_HAS_RECEIVED_LOG = "§9HTTP Server has received new request (method='{}', uri='{}')";
    private static final String REQUEST_REDIRECTION_LOG = "§7HTTP Request redirection process -> §f[api='{}', controller='{}']";
    private static final String REQUEST_NOT_VERIFIED_LOG = "§4HTTP Request is not verified -> [api={}, request='{}']";


    private final UndefinedHttpController undefinedController;

    private final HttpServerConfig config;
    private final HttpContextPattern pattern;

    private final VerifyHelper verifyHelper;

    @Inject
    private BridgenetDataLogger bridgenetDataLogger;

    @Override
    public void handle(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException, IOException {
        VerificationConfig verificationConfig = verifyHelper.process(request, response);

        RequestLine requestLine = request.getRequestLine();
        HttpController controller = findController(requestLine.getMethod(), requestLine.getUri());

        String apiType = (verificationConfig.isPublic() ? PUBLIC_API : PRIVATE_API);

        if (!verificationConfig.isVerified() || (!verificationConfig.isPublic() && !verificationConfig.isCredentialsVerified())) {
            response.setStatusCode(HttpURLConnection.HTTP_UNAUTHORIZED);
            log.error(REQUEST_NOT_VERIFIED_LOG, apiType, requestLine);
            return;
        }

        if (!pattern.getName().isEmpty()) {
            String controllerName = controller.getClass().getSimpleName();

            log.debug(REQUEST_REDIRECTION_LOG, apiType, controllerName);

        } else {
            controller = undefinedController;
        }

        processController(controller, verificationConfig, request, response);
    }

    private void processController(HttpController controller, VerificationConfig verificationConfig,
                                   HttpRequest httpRequest, HttpResponse httpResponse)
            throws HttpException, IOException {

        bridgenetDataLogger.logConnectionOpen(ProfilerType.HTTP_REST);
        bridgenetDataLogger.logReadsCount(ProfilerType.HTTP_REST, httpResponse.getEntity().getContentLength());

        controller.process(httpRequest, verificationConfig);

        httpResponse.setStatusCode(HttpURLConnection.HTTP_OK);
        controller.processCallback(httpResponse, verificationConfig);

        if (httpResponse.getEntity().getContentLength() > 0) {
            bridgenetDataLogger.logReadsCount(ProfilerType.HTTP_REST, httpResponse.getEntity().getContentLength());
        }
        bridgenetDataLogger.logConnectionClose(ProfilerType.HTTP_REST);
    }

    private HttpController findController(String method, String uri) {
        HttpContextPattern pattern = config.find(method, uri);
        if (pattern == null) {
            return undefinedController;
        }

        HttpController controller = pattern.getController();
        if (controller == null) {
            return undefinedController;
        }

        log.debug(REQUEST_HAS_RECEIVED_LOG, method, uri);
        return controller;
    }
}
