package com.pavlovmedia.osgi.http;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 
 * @author Shawn Dempsay {@literal <sdempsay@pavlovmedia.com>}
 *
 */
public interface PavlovHttpClient {
    
    /**
     * Returns a complete clone of this object configuration
     * 
     * @return
     */
    PavlovHttpClient clone();
    
    /**
     * Passes in a sentinel that if set to true will stop
     * any long-running processing and stop the request
     * with an InterruptedException
     * 
     * @param interrupt
     * @return
     */
    PavlovHttpClient withInterrupt(AtomicBoolean interrupt);
    
    /**
     * Sets the base URL to work against
     * @param url a URL
     * @return
     */
    PavlovHttpClient againstUrl(URL url);
    
    /**
     * Appends a path to the end of the URL provided by againstUrl
     * @param path
     * @return
     */
    PavlovHttpClient withUrlPath(String path);
    
    /**
     * Adds a query parameter to url
     * @param key
     * @param value
     * @return
     */
    PavlovHttpClient withQueryParameter(String key, String value);
    
    /**
     * Sets the verb to use
     * @param verb
     * @return
     */
    PavlovHttpClient withVerb(HttpVerbs verb);
    
    PavlovHttpClient withContentType(String contentType);
    
    PavlovHttpClient withAcceptTypes(String...acceptTypes);
    
    /**
     * Gives a chance to directly modify the connection before it is used
     * @param rawConnection
     * @return
     */
    PavlovHttpClient beforeConnectRaw(Consumer<HttpURLConnection> rawConnection);
    
    /**
     * Give a chance to directly inspect the connection results before return
     * @param rawConnection
     * @return
     */
    PavlovHttpClient beforeFinishRaw(Consumer<HttpURLConnection> rawConnection);
    
    /**
     * Allows a modification of the headers
     * @param setHeaders
     * @return
     */
    PavlovHttpClient withSimpleHeaders(Consumer<Map<String,String>> setHeaders);
    
    /**
     * Allows a modification of the headers
     * @param setHeaders
     * @return
     */
    PavlovHttpClient withHeaders(Consumer<Map<String,List<String>>> setHeaders);
    
    /**
     * Adds a single header to the request
     * @param name header name
     * @param value header value
     * @return
     */
    PavlovHttpClient addHeader(String name, String value);
    
    /**
     * Adds data passed into the stream (cannot be used with {@link #withData(String)})
     * @param handleStream
     * @return
     */
    PavlovHttpClient withData(Consumer<OutputStream> handleStream);
    
    /**
     * Specifies a string to be used as data (cannot be used with {@link #withData(Consumer)})
     * @param data
     * @return
     */
    PavlovHttpClient withData(String data);
    
    /**
     * Treats this connection as SSE and will dispatch events to the provided
     * consumer.
     * Note: Can't be used with {@link #asStreaming(Consumer)}
     * 
     * @param sseConsumer
     * @return
     */
    PavlovHttpClient asSse(Consumer<SseMessageEvent> sseConsumer);
    
    /**
     * Treats this connection as a stream and hands the input stream
     * off once connected to the streamConsumer
     * Note: Can't be used with {@link #asSse(Consumer)}
     * 
     * @param streamConsumer
     * @return
     */
    PavlovHttpClient asStreaming(Consumer<InputStream> streamConsumer);
    
    /**
     * Executes this request synchronously, sending along any errors to
     * the onError handler, and only returning a response if there are no
     * errors
     * @param onError
     * @return
     */
    Optional<HttpResponse> execute(Consumer<Exception> onError);
    
    /**
     * Executes this request asynchronously. Any exceptions will be
     * fed through the {@link CompletableFuture#exceptionally(java.util.function.Function)}
     * method.
     * 
     * @return
     */
    CompletableFuture<HttpResponse> executeAsync();
    
    /**
     * Executes this request asynchronously using the specified pool. Any exceptions will be
     * fed through the {@link CompletableFuture#exceptionally(java.util.function.Function)}
     * method.
     * @param pool
     * @param onError
     * @return
     */
    CompletableFuture<HttpResponse> executeAsync(ExecutorService pool);
}
