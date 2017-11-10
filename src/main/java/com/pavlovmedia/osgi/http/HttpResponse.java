package com.pavlovmedia.osgi.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import com.pavlovmedia.osgi.utilities.convertible.ConvertibleAsset;

/**
 * 
 * @author Shawn Dempsay {@literal <sdempsay@pavlovmedia.com>}
 *
 */
public class HttpResponse {
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
    protected HttpResponse(final int responseCode, 
            final Optional<ConvertibleAsset<InputStream>> errorStream,
            final Optional<ConvertibleAsset<InputStream>> responseStream,
            final Map<String,List<String>> responseHeaders) {
        this.responseCode = responseCode;
        this.errorStream = errorStream;
        this.responseStream = responseStream;
        this.responseHeaders = responseHeaders;
    }
    
    /**
     * Gets the response stream as text
     * returns an empty string if there is no response text
     * @param onError
     */
    public String getResponseText(final Consumer<Exception> onError) {
        return responseStream.isPresent()
                ? responseStream.get().convert(inputStreamToUTF8StringConverter(onError))
                : "";
    }
    
    /**
     * Gets the response stream as text
     * returns an empty string if there is no response text
     */
    public String getResponseText() {
        return responseStream.isPresent()
                ? responseStream.get().convert(inputStreamToUTF8StringConverter(HttpResponse::ignoreError))
                : "";
    }
    
    /**
     * Gets the error stream as text
     * returns an empty string if there is no error text
     * 
     * @param onError
     */
    public String getErrorText(final Consumer<Exception> onError) {
        return errorStream.isPresent()
                ? errorStream.get().convert(inputStreamToUTF8StringConverter(onError))
                : "";
    }
    
    /**
     * Gets the error stream as text
     * returns an empty string if there is no error text
     */
    public String getErrorText() {
        return errorStream.isPresent()
                ? errorStream.get().convert(inputStreamToUTF8StringConverter(HttpResponse::ignoreError))
                : "";
    }
    
    /**
     * Static method that will give a converter to decode an UTF-8 input stream into a java string
     * @param onError called if there is an error decoding the string
     * @return the converted string, or an empty string if there is an error.
     */
    public static Function<InputStream, String> inputStreamToUTF8StringConverter(final Consumer<Exception> onError) {
        return (is) -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().reduce((t, u) -> String.format("%s%s%s", t, System.lineSeparator(), u))
                        .orElse("No data");
            } catch (IOException e) {
                onError.accept(e);
                return "";
            }
        };
    }
    
    /**
     * A simple method used to just eat errors
     * @param e
     */
    private static void ignoreError(final Exception e) { }
}
