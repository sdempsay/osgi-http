package com.pavlovmedia.osgi.http.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.pavlovmedia.osgi.http.HttpExceptionCollection;
import com.pavlovmedia.osgi.http.HttpResponse;
import com.pavlovmedia.osgi.http.HttpVerbs;
import com.pavlovmedia.osgi.http.PavlovHttpClient;
import com.pavlovmedia.osgi.http.SseMessageEvent;
import com.pavlovmedia.osgi.utilities.convertable.ConvertibleAsset;

/**
 * 
 * @author Shawn Dempsay {@literal <sdempsay@pavlovmedia.com>}
 *
 */
public class PavlovHttpClientImpl implements PavlovHttpClient {
    public static final String ACCEPT_TYPE_HEADER = "Accept";
    public static final String CONTENT_TYPE_HEADER = "Content-type";
    private static final int TIMEOUT = 5000; // XXX: Should this be settable?
    
    private Optional<URL> httpUrl = Optional.empty();
    private Optional<String> httpPath = Optional.empty();
    private Optional<HttpVerbs> verb = Optional.empty();
    private HashMap<String,String> queryParams = new HashMap<>();
    private HashMap<String, List<String>> additionalHeaders = new HashMap<>();
    private Optional<Consumer<Map<String, String>>> setSimpleHeaders = Optional.empty();
    private Optional<Consumer<Map<String, List<String>>>> setHeaders = Optional.empty();
    private Optional<AtomicBoolean> interrupt = Optional.empty();
    private Optional<Consumer<HttpURLConnection>> beforeConnect = Optional.empty();
    private Optional<Consumer<HttpURLConnection>> beforeFinish = Optional.empty();
    private Optional<Consumer<SseMessageEvent>> sseConsumer = Optional.empty();
    private Optional<Consumer<InputStream>> streamConsumer = Optional.empty();
    private Optional<Consumer<OutputStream>> handleStream = Optional.empty();
    private Optional<String> data = Optional.empty();
    
    public PavlovHttpClient clone() {
        PavlovHttpClientImpl ret = new PavlovHttpClientImpl();
        this.httpUrl.ifPresent(this::againstUrl);
        this.httpPath.ifPresent(ret::withUrlPath);
        this.verb.ifPresent(ret::withVerb);
        ret.queryParams = new HashMap<>(this.queryParams);
        ret.additionalHeaders = new HashMap<>(this.additionalHeaders);
        this.setSimpleHeaders.ifPresent(ret::withSimpleHeaders);
        this.setHeaders.ifPresent(ret::withHeaders);
        this.interrupt.ifPresent(ret::withInterrupt);
        this.beforeConnect.ifPresent(ret::beforeConnectRaw);
        this.beforeFinish.ifPresent(ret::beforeFinishRaw);
        this.sseConsumer.ifPresent(ret::asSse);
        this.streamConsumer.ifPresent(ret::asStreaming);
        this.handleStream.ifPresent(ret::withData);
        this.data.ifPresent(ret::withData);
        return ret;
    }
    
    @Override
    public PavlovHttpClient againstUrl(final URL url) {
        this.httpUrl = Optional.of(url);
        return this;
    }

    @Override
    public PavlovHttpClient withUrlPath(final String path) {
        this.httpPath = Optional.of(path);
        return this;
    }

    @Override
    public PavlovHttpClient withQueryParameter(final String key, final String value) {
        this.queryParams.put(key, value);
        return this;
    }

    @Override
    public PavlovHttpClient withVerb(final HttpVerbs verb) {
        this.verb = Optional.of(verb);
        return this;
    }

    public PavlovHttpClient withContentType(final String contentType) {
        if (!additionalHeaders.containsKey(CONTENT_TYPE_HEADER)) {
            additionalHeaders.put(CONTENT_TYPE_HEADER, new ArrayList<String>());
        }
        additionalHeaders.get(CONTENT_TYPE_HEADER).add(contentType);
        return this;
    }
    
    public PavlovHttpClient withAcceptTypes(final String...acceptTypes) {
        if (!additionalHeaders.containsKey(ACCEPT_TYPE_HEADER)) {
            additionalHeaders.put(ACCEPT_TYPE_HEADER, new ArrayList<String>());
        }
        additionalHeaders.get(ACCEPT_TYPE_HEADER).addAll(Arrays.asList(acceptTypes));
        return this;
    }
    
    @Override
    public PavlovHttpClient withInterrupt(final AtomicBoolean interrupt) {
        this.interrupt = Optional.of(interrupt);
        return this;
    }
    
    @Override
    public PavlovHttpClient beforeConnectRaw(final Consumer<HttpURLConnection> rawConnection) {
        this.beforeConnect = Optional.of(rawConnection);
        return this;
    }

    @Override
    public PavlovHttpClient beforeFinishRaw(final Consumer<HttpURLConnection> rawConnection) {
        this.beforeFinish = Optional.of(rawConnection);
        return this;
    }

    @Override
    public PavlovHttpClient withSimpleHeaders(final Consumer<Map<String, String>> setSimpleHeaders) {
        this.setSimpleHeaders = Optional.of(setSimpleHeaders);
        return this;
    }

    @Override
    public PavlovHttpClient withHeaders(final Consumer<Map<String, List<String>>> setHeaders) {
        this.setHeaders = Optional.of(setHeaders);
        return this;
    }
    
    @Override
    public PavlovHttpClient addHeader(final String name, final String value) {
        if (!additionalHeaders.containsKey(name)) {
            additionalHeaders.put(name, new ArrayList<String>());
        }
        
        additionalHeaders.get(name).add(value);
        return this;
    }

    @Override
    public PavlovHttpClient withData(final Consumer<OutputStream> handleStream) {
        this.handleStream = Optional.of(handleStream);
        return this;
    }

    @Override
    public PavlovHttpClient withData(final String data) {
        this.data = Optional.of(data);
        return this;
    }

    @Override
    public PavlovHttpClient asSse(final Consumer<SseMessageEvent> sseConsumer) {
        this.sseConsumer = Optional.of(sseConsumer);
        return this;
    }

    @Override
    public PavlovHttpClient asStreaming(final Consumer<InputStream> streamConsumer) {
        this.streamConsumer = Optional.of(streamConsumer);
        return this;
    }

    @Override
    public Optional<HttpResponse> execute(final Consumer<Exception> onError) {
        List<Exception> validationErrors = validate();
        if (!validationErrors.isEmpty()) {
            onError.accept(new HttpExceptionCollection("execute failed", validationErrors));
            return Optional.empty();
        }
        
        URL url;
        try {
        url = httpPath.isPresent()
                ? combinePath(httpUrl.get(), httpPath.get())
                : httpUrl.get();
        } catch (MalformedURLException e) {
            // This should never occur after validate
            onError.accept(e);
            return Optional.empty();
        }
        
        try {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(TIMEOUT);
            
            this.handleHeaders(connection);
            this.setVerb(connection);

            beforeConnect.ifPresent(c -> c.accept(connection));
            
            if (data.isPresent()) {
                connection.setDoOutput(true);
                try (OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream())) {
                    writer.write(data.get());
                }
            } else if (handleStream.isPresent()) {
                connection.setDoOutput(true);
                handleStream.get().accept(connection.getOutputStream());
            } else {
                connection.connect();
            }
            
            beforeFinish.ifPresent(f -> f.accept(connection));
            int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                Optional<ConvertibleAsset<InputStream>> inputStream = Optional.empty();
                if (sseConsumer.isPresent()) {
                    handleSse(connection);
                } else if (streamConsumer.isPresent()) {
                    streamConsumer.get().accept(connection.getInputStream());
                } else {
                    inputStream = Optional.of(new ConvertibleAsset<>(connection.getInputStream()));
                }
                return Optional.of(new HttpResponse(responseCode, Optional.empty(), inputStream,
                        connection.getHeaderFields()));
            }
            
            Optional<ConvertibleAsset<InputStream>> response = Optional.empty();
            try {
                    response = connection.getInputStream() != null 
                        ? Optional.of(new ConvertibleAsset<>(connection.getInputStream()))
                        : Optional.empty();
            } catch (IOException e) {
                onError.accept(e);
            }
            
            Optional<ConvertibleAsset<InputStream>> error =
                    connection.getErrorStream() != null 
                    ? Optional.of(new ConvertibleAsset<>(connection.getErrorStream()))
                    : Optional.empty();
                    
            return Optional.of(new HttpResponse(responseCode,
                    error,
                    response,
                    connection.getHeaderFields()));
        
        } catch (IOException e) {
            onError.accept(new HttpExceptionCollection("execute failed", validationErrors));
            return Optional.empty();
        }
    }
    
    @Override
    public CompletableFuture<HttpResponse> executeAsync() {
        return executeAsync(ForkJoinPool.commonPool());
    }

    @Override
    public CompletableFuture<HttpResponse> executeAsync(final ExecutorService pool) {
        List<Exception> validationErrors = validate();
        
        if (!validationErrors.isEmpty()) {
            CompletableFuture<HttpResponse> future = new CompletableFuture<>();
            HttpExceptionCollection ex = new HttpExceptionCollection("ExecuteAsync failed", validationErrors);
            future.completeExceptionally(ex);
            return future;
        }
        
        return CompletableFuture.supplyAsync(this::execute);
    }
    
    private List<Exception> validate() {
        ArrayList<Exception> errors = new ArrayList<>();
        
        // We need, at a minimum, a url and verb
        ifNotPresent(httpUrl, () -> errors.add(new IllegalStateException("A URL must be set")));
        ifNotPresent(verb, () -> errors.add(new IllegalStateException("A verb must be set")));
        
        // Now check for things that can't both be set
        sseConsumer.ifPresent(s -> streamConsumer.ifPresent(t -> 
            errors.add(new IllegalStateException("Cannot be SSE and streaming at the same time"))));
        
        data.ifPresent(d -> handleStream.ifPresent(s -> 
            errors.add(new IllegalStateException("Cannot have data and a data handler at the same time"))));
        
        // One last check to see if we can make a new URL from the passed in path, if it exists
        httpPath.ifPresent(path ->
            // There was a check for this, but it can still be empty here
            httpUrl.ifPresent(u -> {
                try {
                    combinePath(httpUrl.get(), httpPath.get());
                } catch (MalformedURLException e) {
                    errors.add(e);
                }
            }));
        return errors;
    }
    
    private void ifNotPresent(final Optional<?> optional, final Runnable action) {
        if (!optional.isPresent()) {
            action.run();
        }
    }
    
    private URL combinePath(final URL original, final String path) throws MalformedURLException {
        String base = original.toExternalForm();
        String finalUrl = "";
        if (base.endsWith("/") && path.startsWith("/")) {
            finalUrl = String.format("%s%s", base, path.substring(1));
          } else if (!base.endsWith("/") && !path.startsWith("/")) {
            finalUrl = String.format("%s/%s", base, path);
          } else {
            finalUrl = String.format("%s%s", base, path);
          }
        return new URL(finalUrl);
    }

    private HttpResponse execute() throws HttpExceptionCollection {
        ArrayList<Exception> exceptions = new ArrayList<>();
        
        Optional<HttpResponse> ret = execute(exceptions::add);

        if (!exceptions.isEmpty()) {
            throw new HttpExceptionCollection("Execute failed", exceptions);
        }
        return ret.get();
    }
    
    protected void handleHeaders(final HttpURLConnection connection) {
        // We are going to be using a map, based off of what has already been set
        HashMap<String,List<String>> headers = new HashMap<>(additionalHeaders);
        // Now pass along to any modification routines
        setHeaders.ifPresent(sh -> sh.accept(headers));
        // Next we do simple headers, which is a touch more complex
        setSimpleHeaders.ifPresent(setter -> {
            HashMap<String,String> simpleHeaders = new HashMap<>();
            setter.accept(simpleHeaders);
            simpleHeaders.forEach((key, value) -> {
                if (!headers.containsKey(key)) {
                    headers.put(key, new ArrayList<>());
                }
                headers.get(key).add(value);
            });
        });
        
        // Now do the setting
        headers.forEach((key, valueList) -> {
            valueList.forEach(value -> connection.setRequestProperty(key, value));
        });
    }
    
    protected void setVerb(final HttpURLConnection connection) throws ProtocolException {
        switch (verb.get()) {
            case PATCH:
                connection.setRequestProperty("X-HTTP-Method-Override", "PATCH");
                connection.setRequestMethod("POST");
                break;
            default:
                connection.setRequestMethod(verb.get().toString());
        }
    }
    
    private void handleSse(final HttpURLConnection connection) {
        
    }
}
