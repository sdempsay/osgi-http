package com.pavlovmedia.oss.osgi.http;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.pavlovmedia.oss.osgi.utilities.convertible.ConvertibleAsset;

/**
 * Implementation of the {@link PavlovHttpClient} interface
 * 
 * @author Shawn Dempsay {@literal <sdempsay@pavlovmedia.com>}
 *
 */
public class PavlovHttpClientImpl implements PavlovHttpClient {
    public static final String ACCEPT_TYPE_HEADER = "Accept";
    public static final String CONTENT_TYPE_HEADER = "Content-type";
    private static final int TIMEOUT = 5000; // XXX: Should this be settable?
    private static final Pattern SSE_ENTRY = Pattern.compile("(?<field>\\w+):(?<data>.+)");
    
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
    
    private URL validatedUrl;
    
    public PavlovHttpClient clone() {
        PavlovHttpClientImpl ret = new PavlovHttpClientImpl();
        this.httpUrl.ifPresent(ret::againstUrl);
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
    public PavlovHttpClientImpl againstUrl(final URL url) {
        this.httpUrl = Optional.of(url);
        return this;
    }

    @Override
    public PavlovHttpClientImpl withUrlPath(final String path) {
        this.httpPath = Optional.of(path);
        return this;
    }

    @Override
    public PavlovHttpClientImpl withQueryParameter(final String key, final String value) {
        this.queryParams.put(key, value);
        return this;
    }

    @Override
    public PavlovHttpClientImpl withVerb(final HttpVerbs verb) {
        this.verb = Optional.of(verb);
        return this;
    }

    public PavlovHttpClientImpl withContentType(final String contentType) {
        if (!additionalHeaders.containsKey(CONTENT_TYPE_HEADER)) {
            additionalHeaders.put(CONTENT_TYPE_HEADER, new ArrayList<String>());
        }
        additionalHeaders.get(CONTENT_TYPE_HEADER).add(contentType);
        return this;
    }
    
    public PavlovHttpClientImpl withAcceptTypes(final String...acceptTypes) {
        if (!additionalHeaders.containsKey(ACCEPT_TYPE_HEADER)) {
            additionalHeaders.put(ACCEPT_TYPE_HEADER, new ArrayList<String>());
        }
        additionalHeaders.get(ACCEPT_TYPE_HEADER).addAll(Arrays.asList(acceptTypes));
        return this;
    }
    
    @Override
    public PavlovHttpClientImpl withInterrupt(final AtomicBoolean interrupt) {
        this.interrupt = Optional.of(interrupt);
        return this;
    }
    
    @Override
    public PavlovHttpClientImpl beforeConnectRaw(final Consumer<HttpURLConnection> rawConnection) {
        this.beforeConnect = Optional.of(rawConnection);
        return this;
    }

    @Override
    public PavlovHttpClientImpl beforeFinishRaw(final Consumer<HttpURLConnection> rawConnection) {
        this.beforeFinish = Optional.of(rawConnection);
        return this;
    }

    @Override
    public PavlovHttpClientImpl withSimpleHeaders(final Consumer<Map<String, String>> setSimpleHeaders) {
        this.setSimpleHeaders = Optional.of(setSimpleHeaders);
        return this;
    }

    @Override
    public PavlovHttpClientImpl withHeaders(final Consumer<Map<String, List<String>>> setHeaders) {
        this.setHeaders = Optional.of(setHeaders);
        return this;
    }
    
    @Override
    public PavlovHttpClientImpl addHeader(final String name, final String value) {
        if (!additionalHeaders.containsKey(name)) {
            additionalHeaders.put(name, new ArrayList<String>());
        }
        
        additionalHeaders.get(name).add(value);
        return this;
    }

    @Override
    public PavlovHttpClient withBasicAuth(final String username, final String password) {
        Objects.requireNonNull(username);
        Objects.requireNonNull(password);
        byte[] credentials = String.format("%s:%s", username, password).getBytes();
        String header = String.format("Basic %s",
                Base64.getEncoder().encodeToString(credentials));
        return addHeader("Authorization", header);
    }
    
    @Override
    public PavlovHttpClientImpl withData(final Consumer<OutputStream> handleStream) {
        this.handleStream = Optional.of(handleStream);
        return this;
    }

    @Override
    public PavlovHttpClientImpl withData(final String data) {
        this.data = Optional.of(data);
        return this;
    }

    @Override
    public PavlovHttpClientImpl asSse(final Consumer<SseMessageEvent> sseConsumer) {
        this.sseConsumer = Optional.of(sseConsumer);
        return this;
    }

    @Override
    public PavlovHttpClientImpl asStreaming(final Consumer<InputStream> streamConsumer) {
        this.streamConsumer = Optional.of(streamConsumer);
        return this;
    }

    @Override
    public PavlovHttpClientImpl usingGzip() {
        addHeader("Accept-Encoding", "gzip");
        return this;
    }
    
    @Override
    public Optional<HttpResponse> execute(final Consumer<Exception> onError) {
        List<Exception> validationErrors = validate();
        if (!validationErrors.isEmpty()) {
            onError.accept(new HttpExceptionCollection("execute failed", validationErrors));
            return Optional.empty();
        }
        
        
        System.out.println("Final url is: "+validatedUrl.toExternalForm());
        
        try {
            HttpURLConnection connection = (HttpURLConnection) validatedUrl.openConnection();
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
            int responseCode = -1;
            try {
                responseCode = connection.getResponseCode();
            } catch (FileNotFoundException e) {
                responseCode = 404;
            }
            
            if (responseCode >= 200 && responseCode < 300) {
                Optional<ConvertibleAsset<InputStream>> inputStream = Optional.empty();
                if (sseConsumer.isPresent()) {
                    handleSse(connection);
                } else if (streamConsumer.isPresent()) {
                    streamConsumer.get().accept(connection.getInputStream());
                } else {
                    inputStream = Optional.of(new ConvertibleAsset<>(connection.getInputStream()));
                }
                return Optional.of(new HttpResponse(validatedUrl, responseCode, Optional.empty(), inputStream,
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
                    
            return Optional.of(new HttpResponse(
                    validatedUrl,
                    responseCode,
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
        
        AtomicReference<URL> url = new AtomicReference<>(httpUrl.get());
        
        // Check to see if we can make a new URL from the passed in path, if it exists
        httpPath.ifPresent(path ->
            // There was a check for this, but it can still be empty here
            httpUrl.ifPresent(u -> {
                try {
                    url.set(combinePath(httpUrl.get(), httpPath.get()));
                } catch (MalformedURLException e) {
                    errors.add(e);
                }
            }));
        
        if (!queryParams.isEmpty()) {
            String queryString = queryParams.keySet().stream()
                .map(k -> convertQueryParameter(k, queryParams.get(k), e -> errors.add(e)))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .reduce((x,y) -> String.format("%s&%s", x,y)).orElse("");
            try {
                url.set(new URL(String.format("%s?%s", url.get().toExternalForm(), queryString)));
            } catch (MalformedURLException e) {
                errors.add(e);
            }
        }
        
        // We built up the final URL here as well
        validatedUrl = url.get();
        
        return errors;
    }
    
    private Optional<String> convertQueryParameter(final String key, final String value, final Consumer<Exception> onError) {
        try {
            String enc = URLEncoder.encode(value, "UTF-8");
            return Optional.of(String.format("%s=%s", key, enc));
        } catch (UnsupportedEncodingException e) {
            onError.accept(e);
            return Optional.empty();
        }
    }
    
    private void ifNotPresent(final Optional<?> optional, final Runnable action) {
        if (!optional.isPresent()) {
            action.run();
        }
    }
    
    public static URL combinePath(final URL original, final String path) throws MalformedURLException {
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
        AtomicBoolean isFalse = new AtomicBoolean();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            while (!interrupt.orElse(isFalse).get()) {
                Optional<String> id = Optional.empty();
                Optional<ConvertibleAsset<String>> event = Optional.empty();
                Optional<ConvertibleAsset<String>> data = Optional.empty();
                while (!interrupt.orElse(isFalse).get()) {
                    // Empty line is the end of an event
                    String line = reader.readLine();
                    if (line.trim().isEmpty()) {
                        // If we have at least data, emit an sse event
                        if (data.isPresent()) {
                            SseMessageEvent currentEvent = new SseMessageEvent(id, event, data);
                            sseConsumer.get().accept(currentEvent);
                        }
                        break; // Next message
                    }

                    // This ignores comment lines
                    if (!line.trim().startsWith(":")) {
                        Matcher lineMatcher = SSE_ENTRY.matcher(line.trim());
                        if (lineMatcher.matches()) {
                            switch (lineMatcher.group("field")) {
                                case "id":
                                    id = Optional.of(lineMatcher.group("data"));
                                    break;
                                case "event":
                                    event = Optional.of(new ConvertibleAsset<>(lineMatcher.group("data")));
                                    break;
                                case "data":
                                    data = Optional.of(new ConvertibleAsset<>(lineMatcher.group("data")));
                                    break;
                                default:
                                    // Do nothing
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            // TODO: Is there any point in logging this somehow?
            e.printStackTrace();
        }
    }
}
