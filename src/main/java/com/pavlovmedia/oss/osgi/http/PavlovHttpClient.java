package com.pavlovmedia.oss.osgi.http;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * A modern take on an HTTP client
 * @author Shawn Dempsay {@literal <sdempsay@pavlovmedia.com>}
 *
 */
public interface PavlovHttpClient {

    /**
     * Returns a complete clone of this object configuration
     */
    PavlovHttpClient clone();

    /**
     * Passes in a sentinel that if set to true will stop
     * any long-running processing and stop the request
     * with an InterruptedException
     *
     * @param interrupt
     */
    PavlovHttpClient withInterrupt(AtomicBoolean interrupt);

    /**
     * Sets the base URL to work against
     * @param url a URL
     */
    PavlovHttpClient againstUrl(URL url);

    /**
     * Appends a path to the end of the URL provided by againstUrl
     * @param path
     */
    PavlovHttpClient withUrlPath(String path);

    /**
     * Adds a query parameter to url
     * @param key
     * @param value
     */
    PavlovHttpClient withQueryParameter(String key, String value);

    /**
     * Sets the verb to use
     * @param verb
     */
    PavlovHttpClient withVerb(HttpVerbs verb);

    /**
     * Sets the content type of this request
     * @param contentType
     */
    PavlovHttpClient withContentType(String contentType);

    /**
     * Sets acceptable return types
     * @param acceptTypes
     */
    PavlovHttpClient withAcceptTypes(String...acceptTypes);

    /**
     * Gives a chance to directly modify the connection before it is used
     * @param rawConnection
     */
    PavlovHttpClient beforeConnectRaw(Consumer<HttpURLConnection> rawConnection);

    /**
     * Give a chance to directly inspect the connection results before return
     * @param rawConnection
     */
    PavlovHttpClient beforeFinishRaw(Consumer<HttpURLConnection> rawConnection);

    /**
     * Allows a modification of the headers
     * @param setHeaders
     */
    PavlovHttpClient withSimpleHeaders(Consumer<Map<String,String>> setHeaders);

    /**
     * Allows a modification of the headers
     * @param setHeaders
     */
    PavlovHttpClient withHeaders(Consumer<Map<String,List<String>>> setHeaders);

    /**
     * Adds a single header to the request
     * @param name header name
     * @param value header value
     */
    PavlovHttpClient addHeader(String name, String value);

    /**
     * Adds an authorization header with basic auth
     * @param username
     * @param password
     */
    PavlovHttpClient withBasicAuth(String username, String password);

    /**
     * Adds data passed into the stream (cannot be used with {@link #withData(String)})
     * @param handleStream
     */
    PavlovHttpClient withData(Consumer<OutputStream> handleStream);

    /**
     * Specifies a string to be used as data (cannot be used with {@link #withData(Consumer)})
     * @param data
     */
    PavlovHttpClient withData(String data);
    
    /**
     * Adds a file to be sent with a post request of content type "multipart/form-data"
     * @param fieldName the name of the field for the file in the form
     * @param file 
     * @return
     */
    PavlovHttpClient addFileFormData(String fieldName, File file);

    /**
     * Treats this connection as SSE and will dispatch events to the provided
     * consumer.
     * Note: Can't be used with {@link #asStreaming(Consumer)}
     *
     * @param sseConsumer
     */
    PavlovHttpClient asSse(Consumer<SseMessageEvent> sseConsumer);

    /**
     * Treats this connection as a stream and hands the input stream
     * off once connected to the streamConsumer
     * Note: Can't be used with {@link #asSse(Consumer)}
     *
     * @param streamConsumer
     */
    PavlovHttpClient asStreaming(Consumer<InputStream> streamConsumer);

    /**
     * Attempts to get this stream with gzip encoding
     */
    PavlovHttpClient usingGzip();

    /**
     * Allows you to ignore self signed certificates
     * @since 1.0.2
     */
    PavlovHttpClient ignoringSelfSignedCert(boolean ingoreSelfSignedCertEnabled);

    /**
     * Sets up a debugger consumer to add messages to
     * 
     * @param debugger
     * @since 1.0.2
     */
    PavlovHttpClient withDebugger(Consumer<String> debugger);
    
    /**
     * Executes this request synchronously, sending along any errors to
     * the onError handler, and only returning a response if there are no
     * errors
     * @param onError
     */
    Optional<HttpResponse> execute(Consumer<Exception> onError);

    /**
     * Executes this request asynchronously. Any exceptions will be
     * fed through the {@link CompletableFuture#exceptionally(java.util.function.Function)}
     * method.
     */
    CompletableFuture<HttpResponse> executeAsync();

    /**
     * Executes this request asynchronously using the specified pool. Any exceptions will be
     * fed through the {@link CompletableFuture#exceptionally(java.util.function.Function)}
     * method.
     * @param pool
     */
    CompletableFuture<HttpResponse> executeAsync(ExecutorService pool);
    
}
