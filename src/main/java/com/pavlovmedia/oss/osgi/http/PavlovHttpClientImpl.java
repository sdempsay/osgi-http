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
import java.security.GeneralSecurityException;
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

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

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
    private Optional<Consumer<String>> debugger = Optional.empty();
    private Optional<String> data = Optional.empty();
    private boolean ignoreSelfSignedCertEnabled;

    private URL validatedUrl;

    @Override
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

    private static SSLSocketFactory SELF_SIGNED_SOCKET_FACTORY;
    static {
        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, new TrustManager[] { new SelfSignedTrustManager() }, new java.security.SecureRandom());
            SELF_SIGNED_SOCKET_FACTORY = sc.getSocketFactory();
        } catch (GeneralSecurityException e) {
            // This happens before we are running in osgi
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public PavlovHttpClientImpl againstUrl(final URL url) {
        this.httpUrl = Optional.of(url);
        return this;
    }

    @Override
    public PavlovHttpClient ignoringSelfSignedCert(final boolean ignoringSelfSignedCertEnabled) {
        this.ignoreSelfSignedCertEnabled = ignoringSelfSignedCertEnabled;
        return this;
    }

    @Override
    public PavlovHttpClient withDebugger(final Consumer<String> debugger) {
        this.debugger = Optional.ofNullable(debugger);
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

    @Override
    public PavlovHttpClientImpl withContentType(final String contentType) {
        if (!this.additionalHeaders.containsKey(CONTENT_TYPE_HEADER)) {
            this.additionalHeaders.put(CONTENT_TYPE_HEADER, new ArrayList<String>());
        }
        this.additionalHeaders.get(CONTENT_TYPE_HEADER).add(contentType);
        return this;
    }

    @Override
    public PavlovHttpClientImpl withAcceptTypes(final String...acceptTypes) {
        if (!this.additionalHeaders.containsKey(ACCEPT_TYPE_HEADER)) {
            this.additionalHeaders.put(ACCEPT_TYPE_HEADER, new ArrayList<String>());
        }
        this.additionalHeaders.get(ACCEPT_TYPE_HEADER).addAll(Arrays.asList(acceptTypes));
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
        if (!this.additionalHeaders.containsKey(name)) {
            this.additionalHeaders.put(name, new ArrayList<String>());
        }

        this.additionalHeaders.get(name).add(value);
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
        Objects.requireNonNull(onError, "Error handler is required");
        List<Exception> validationErrors = validate();
        if (!validationErrors.isEmpty()) {
            validationErrors.forEach(onError::accept);
            return Optional.empty();
        }

        debugger.ifPresent(c -> c.accept("Final url is: "+this.validatedUrl.toExternalForm()));

        try {
            HttpURLConnection connection = (HttpURLConnection) this.validatedUrl.openConnection();
            connection.setConnectTimeout(TIMEOUT);

            if (this.ignoreSelfSignedCertEnabled && connection instanceof HttpsURLConnection) {
                debugger.ifPresent(c -> c.accept("Ignorning self signed certificate"));
                ((HttpsURLConnection) connection).setSSLSocketFactory(SELF_SIGNED_SOCKET_FACTORY);
            }

            this.handleHeaders(connection);
            this.setVerb(connection);

            this.beforeConnect.ifPresent(c -> c.accept(connection));

            if (this.data.isPresent()) {
                connection.setDoOutput(true);
                try (OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream())) {
                    writer.write(this.data.get());
                }
            } else if (this.handleStream.isPresent()) {
                connection.setDoOutput(true);
                this.handleStream.get().accept(connection.getOutputStream());
            } else {
                connection.connect();
            }

            this.beforeFinish.ifPresent(f -> f.accept(connection));
            int responseCode = -1;
            try {
                responseCode = connection.getResponseCode();
            } catch (FileNotFoundException e) {
                responseCode = 404;
            }

            final int debugCode = responseCode; // Need this for the logging lambda
            debugger.ifPresent(d -> d.accept("Response code is "+debugCode));
            
            if (responseCode >= 200 && responseCode < 300) {
                Optional<ConvertibleAsset<InputStream>> inputStream = Optional.empty();
                if (this.sseConsumer.isPresent()) {
                    handleSse(connection);
                } else if (this.streamConsumer.isPresent()) {
                    this.streamConsumer.get().accept(connection.getInputStream());
                } else {
                    inputStream = Optional.of(new ConvertibleAsset<>(connection.getInputStream()));
                }
                return Optional.of(new HttpResponse(this.validatedUrl, responseCode, Optional.empty(), inputStream,
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
                    this.validatedUrl,
                    responseCode,
                    error,
                    response,
                    connection.getHeaderFields()));

        } catch (IOException e) {
            debugger.ifPresent(d -> d.accept("Got exception "+e));
            onError.accept(e);
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
            IllegalStateException stacked = stackExceptions(validationErrors);
            future.completeExceptionally(stacked);
            return future;
        }
        
        CompletableFuture<HttpResponse> ret = new CompletableFuture<>();
        pool.submit(() -> {
            AtomicReference<Exception> error = new AtomicReference<>();
            Optional<HttpResponse> response = this.execute(error::set);
            if (Objects.isNull(error)) {
                ret.completeExceptionally(error.get());
            } else if (response.isPresent()) {
                ret.complete(response.get());
            } else {
                ret.completeExceptionally(new IllegalStateException("Http execution failed"));
            }
        });
        return ret;
    }

    private List<Exception> validate() {
        ArrayList<Exception> errors = new ArrayList<>();

        // We need, at a minimum, a url and verb
        ifNotPresent(this.httpUrl, () -> errors.add(new IllegalStateException("A URL must be set")));
        ifNotPresent(this.verb, () -> errors.add(new IllegalStateException("A verb must be set")));

        // Now check for things that can't both be set
        this.sseConsumer.ifPresent(s -> this.streamConsumer.ifPresent(t ->
            errors.add(new IllegalStateException("Cannot be SSE and streaming at the same time"))));

        this.data.ifPresent(d -> this.handleStream.ifPresent(s ->
            errors.add(new IllegalStateException("Cannot have data and a data handler at the same time"))));

        AtomicReference<URL> url = new AtomicReference<>(this.httpUrl.get());

        // Check to see if we can make a new URL from the passed in path, if it exists
        this.httpPath.ifPresent(path ->
            // There was a check for this, but it can still be empty here
            this.httpUrl.ifPresent(u -> {
                try {
                    url.set(combinePath(this.httpUrl.get(), this.httpPath.get()));
                } catch (MalformedURLException e) {
                    errors.add(e);
                }
            }));

        if (!this.queryParams.isEmpty()) {
            String queryString = this.queryParams.keySet().stream()
                .map(k -> convertQueryParameter(k, this.queryParams.get(k), e -> errors.add(e)))
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
        this.validatedUrl = url.get();

        if (!errors.isEmpty() && debugger.isPresent()) {
            errors.forEach(e -> debugger.ifPresent(d -> d.accept(e.toString())));
        }
        
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

    protected void handleHeaders(final HttpURLConnection connection) {
        // We are going to be using a map, based off of what has already been set
        HashMap<String,List<String>> headers = new HashMap<>(this.additionalHeaders);
        // Now pass along to any modification routines
        this.setHeaders.ifPresent(sh -> sh.accept(headers));
        // Next we do simple headers, which is a touch more complex
        this.setSimpleHeaders.ifPresent(setter -> {
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
        switch (this.verb.get()) {
            case PATCH:
                connection.setRequestProperty("X-HTTP-Method-Override", "PATCH");
                connection.setRequestMethod("POST");
                break;
            default:
                connection.setRequestMethod(this.verb.get().toString());
        }
    }

    private void handleSse(final HttpURLConnection connection) {
        AtomicBoolean isFalse = new AtomicBoolean();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            while (!this.interrupt.orElse(isFalse).get()) {
                Optional<String> id = Optional.empty();
                Optional<ConvertibleAsset<String>> event = Optional.empty();
                Optional<ConvertibleAsset<String>> data = Optional.empty();
                while (!this.interrupt.orElse(isFalse).get()) {
                    // Empty line is the end of an event
                    String line = reader.readLine();
                    if (line.trim().isEmpty()) {
                        // If we have at least data, emit an sse event
                        if (data.isPresent()) {
                            SseMessageEvent currentEvent = new SseMessageEvent(id, event, data);
                            this.sseConsumer.get().accept(currentEvent);
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
    
    private static IllegalStateException stackExceptions(final List<Exception> exceptions) {
        IllegalStateException last = null;
        for (final Exception e: exceptions) {
            last = new IllegalStateException(e.getMessage(), last);
        }
        return last;
    }
}
