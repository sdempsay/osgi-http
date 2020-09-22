package com.pavlovmedia.oss.osgi.http;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import com.pavlovmedia.oss.osgi.utilities.convertible.ConvertibleAsset;

/**
 *
 * @author Shawn Dempsay {@literal <sdempsay@pavlovmedia.com>}
 *
 */
public class HttpResponse {
    /**
     * The URL that creaeted this response
     */
    public final URL srcUrl;

    /**
     * The HTTP Response code
     */
    public final int responseCode;

    /**
     * A convertible InputStream that represents the underlying errorStream.
     * This is Optional and won't be populated if there is no error stream
     */
    public final Optional<ConvertibleAsset<InputStream>> errorStream;

    /**
     * A convertible InputStream that represents the underlying inputStream.
     * This is Optional and won't be populated if there is no error stream
     */
    public final Optional<ConvertibleAsset<InputStream>> responseStream;

    /**
     * This is a map of the response headers
     */
    public final Map<String,List<String>> responseHeaders;

    /**
     * Constructor for an HTTP Response
     *
     * @param responseCode
     * @param errorStream
     * @param responseStream
     * @param responseHeaders
     */
    protected HttpResponse(final URL srcUrl,
            final int responseCode,
            final Optional<ConvertibleAsset<InputStream>> errorStream,
            final Optional<ConvertibleAsset<InputStream>> responseStream,
            final Map<String,List<String>> responseHeaders) {
        this.srcUrl = srcUrl;
        this.responseCode = responseCode;
        this.errorStream = errorStream;
        this.responseStream = responseStream;
        this.responseHeaders = responseHeaders;
    }

    public boolean isGziped() {
        return this.responseHeaders.getOrDefault("Content-Encoding", Collections.emptyList())
                .contains("gzip");
    }

    private final AtomicReference<String> responseString = new AtomicReference<>();

    private String readAndCache(final Optional<ConvertibleAsset<InputStream>> stream,
            final AtomicReference<String> reference, final Consumer<Exception> onError) {
        if (!stream.isPresent()) {
            return "";
        }

        if (reference.get() != null) {
            return reference.get();
        }

        ConvertibleAsset<InputStream> working = stream.get();
        if (isGziped()) {
            working = working.convert(gunzipInputStream(onError));
        }

        reference.set(working.convert(inputStreamToUTF8StringConverter(onError)));
        return reference.get();
    }

    /**
     * Gets the response stream as text
     * returns an empty string if there is no response text
     * @param onError
     */
    public String getResponseText(final Consumer<Exception> onError) {
        return readAndCache(this.responseStream, this.responseString, onError);
    }

    /**
     * Gets the response stream as text
     * returns an empty string if there is no response text
     */
    public String getResponseText() {
        return getResponseText(HttpResponse::ignoreError);
    }

    private final AtomicReference<String> errorString = new AtomicReference<String>();

    /**
     * Gets the error stream as text
     * returns an empty string if there is no error text
     *
     * @param onError
     */
    public String getErrorText(final Consumer<Exception> onError) {
        return readAndCache(this.errorStream, this.errorString, onError);
    }

    /**
     * Gets the error stream as text
     * returns an empty string if there is no error text
     */
    public String getErrorText() {
        return this.errorStream.isPresent()
                ? this.errorStream.get().convert(inputStreamToUTF8StringConverter(HttpResponse::ignoreError))
                : "";
    }

    /**
     * This method will check to see if there is a valid response code, which
     * is between 200 and 299, if not it returns false
     *
     * @deprecated The Exception Consumer was not needed and causing issues.  Use the non-Exception Consumer method
     * @param onError
     */
    @Deprecated
    public boolean isValidResponse(final Consumer<Exception> onError) {
        return !(this.responseCode < 200 || this.responseCode >= 300);
    }

    /**
     * This method will check to see if there is a valid response code, which
     * is between 200 and 299, if not it returns false
     * @return true : 200 &gt;= x &lt; 300
     */
    public boolean isValidResponse() {
        return !(this.responseCode < 200 || this.responseCode >= 300);
    }

    /**
     * Will process the result of a response branching between valid responses and invalid responses
     * @since 1.0.11
     * 
     * @param onValidResponse calculates a response based on a valid status
     * @param onInvalidResponse calculates a response based on an invalid status
     */
    public <T> Optional<T> process(final Function<HttpResponse, Optional<T>> onValidResponse, final Function<HttpResponse, Optional<T>> onInvalidResponse) {
        return isValidResponse()
            ? onValidResponse.apply(this)
            : onInvalidResponse.apply(this);
    }

    /**
     * A static version of {@link #process(Function, Function)} that is useful in mapping functions
     * @since 1.0.11
     * 
     * @param onValidResponse
     * @param onInvalidResponse
     */
    public static final <T> Function<HttpResponse,Optional<T>> processResponse(final Function<HttpResponse, Optional<T>> onValidResponse, final Function<HttpResponse, Optional<T>> onInvalidResponse) {
        return httpResponse -> httpResponse.process(onValidResponse, onInvalidResponse);
    }
    
    /**
     * Will process the result of a response returning the value of onValidResponse for valid, and calling onInvalidResponse and returning {@link Optional#empty()}
     * if the response is invalid
     * @since 1.0.11
     * 
     * @param onValidResponse
     * @param onInvalidResponse
     */
    public <T> Optional<T> process(final Function<HttpResponse, Optional<T>> onValidResponse, final Consumer<HttpResponse> onInvalidResponse) {
        return process(onValidResponse, r -> {
            onInvalidResponse.accept(this);
            return Optional.empty();
        });
    }

    /**
     * A static version of {@link #process(Function, Consumer)} that is useful in mapping functions
     * @since 1.0.11
     * 
     * @param onValidResponse
     * @param onInvalidResponse
     */
    public static final <T> Function<HttpResponse,Optional<T>> processResponse(final Function<HttpResponse, Optional<T>> onValidResponse, final Consumer<HttpResponse> onInvalidResponse) {
        return httpResponse -> httpResponse.process(onValidResponse, onInvalidResponse);
    }
    
    /**
     * Static method that will give a converter to decode an UTF-8 input stream into a java string
     * @param onError called if there is an error decoding the string
     * @return the converted string, or an empty string if there is an error.
     */
    public static Function<InputStream, String> inputStreamToUTF8StringConverter(final Consumer<Exception> onError) {
        return is -> {
            final InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
            final Vector<Byte> byteVector = new Vector<>(1024);
            
            try (BufferedReader reader = new BufferedReader(isr)) {
                while (true) {
                    final int ch = reader.read();
                    if (-1 == ch) {
                        break;
                    }
                    byteVector.add((byte) ch);
                }
                
                return Stream.of(byteVectorToByteArray(byteVector))
                        .map(String::new)
                        .reduce((t, u) -> String.format("%s%s%s", t, System.lineSeparator(), u))
                        .orElse("No data");
            } catch (final IOException e) {
                onError.accept(e);
                return "";
            }
            
        };
    }

    public static Function<InputStream, ConvertibleAsset<InputStream>> gunzipInputStream(final Consumer<Exception> onError) {
        return in -> gunzipInputStream(in, onError);
    }

    public static ConvertibleAsset<InputStream> gunzipInputStream(final InputStream in, final Consumer<Exception> onError) {
        try {
            final Reader reader = new InputStreamReader(new GZIPInputStream(in));
            final Vector<Byte> byteVector = new Vector<>(1024);

            while (true) {
                final int ch = reader.read();
                if (-1 == ch) {
                    break;
                }
                byteVector.add((byte) ch);
            }

            return new ConvertibleAsset<>(new ByteArrayInputStream(byteVectorToByteArray(byteVector)));

        } catch (final IOException e) {
            onError.accept(e);
            return new ConvertibleAsset<>(new ByteArrayInputStream(new byte[ ] { }));
        }
    }
    
    public static byte[] byteVectorToByteArray(final Vector<Byte> byteVector) {
        Objects.requireNonNull(byteVector);
        
        final byte[] byteArray = new byte[byteVector.size()];
        for (int i = 0; i < byteVector.size(); i++) {
            byteArray[i] = byteVector.get(i).byteValue();
        }
        
        return byteArray;
    }

    /**
     * A simple method used to just eat errors
     * @param e
     */
    private static void ignoreError(final Exception e) { }
}
