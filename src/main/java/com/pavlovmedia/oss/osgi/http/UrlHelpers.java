package com.pavlovmedia.oss.osgi.http;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * 
 * @author Shawn Dempsay {@literal <sdempsay@pavlovmedia.com>}
 * @since 1.0.6
 */
public final class UrlHelpers {
    private UrlHelpers() { }
    
    /**
     * This will combine a set of url parts into a string version of
     * a url. Note there is no correctness check here for formatting
     * and to get a final {@link URL} object you will need to call
     * {@link #urlFromString(String, Consumer)}
     * 
     * @since 1.0.6
     * @param parts
     */
    public static String combineUrlParts(final String...parts) {
        Objects.requireNonNull(parts);
        
        if (parts.length < 0) {
            return "";
        }
        AtomicReference<String> finalUrl = new AtomicReference<String>(parts[0]);
        Stream.of(parts)
            .skip(1)
            .forEach(part -> finalUrl.set(combinePath(finalUrl.get(), part)));
        return finalUrl.get();
    }
    
    /**
     * Combines 2 url segements ensuring that you don't get multiple instances of / (like http://foo//bar/baz//)
     * 
     * @since 1.0.6
     * @param original The URL path to start from
     * @param path The additional path to add
     */
    public static String combinePath(final String original, final String path) {
        Objects.requireNonNull(original);
        Objects.requireNonNull(path);
        
        final String base = original;
        String finalUrl = "";
        if (base.endsWith("/") && path.startsWith("/")) {
            finalUrl = String.format("%s%s", base, path.substring(1));
          } else if (!base.endsWith("/") && !path.startsWith("/")) {
            finalUrl = String.format("%s/%s", base, path);
          } else {
            finalUrl = String.format("%s%s", base, path);
          }
        return finalUrl;
    }
    
    /**
     * Same as {@link #combinePath(String, String)} but converts in incoming {@link URL} to its string format
     * 
     * @since 1.0.6
     * @param original
     * @param path
     */
    public static String combinePath(final URL original, final String path) {
        Objects.requireNonNull(original);
        Objects.requireNonNull(path);
        
        return combinePath(original.toExternalForm(), path);
    }
    
    /**
     * Converts a string representation of a url to a {@link URL} object
     * @param url The string to convert
     * @param onError a consumer that gets the {@link MalformedURLException} if it is correct
     * 
     * @since 1.0.6
     * @return Optional of a {@link URL} if successful and {@link Optional#empty()} otherwise
     */
    public static Optional<URL> urlFromString(final String url, final Consumer<Exception> onError) {
        Objects.requireNonNull(url);
        Objects.requireNonNull(onError);
        
        try {
            return Optional.ofNullable(new URL(url));
        } catch (MalformedURLException e) {
            onError.accept(e);
        }
        return Optional.empty();
    }
    
    /**
     * This method takes a source from a spidered page and then a reference
     * found on that page and returns a proper url from it. If the link is
     * relative, it will prepend the source url
     * 
     * @since 1.0.6
     * @param ref
     * @param srcUrl
     * @param onError a consumer that gets the {@link MalformedURLException} if it is correct
     */
    public static Optional<URL> fullUrlFromReference(final String ref, final String srcUrl, final Consumer<Exception> onError) {
        Objects.requireNonNull(ref);
        Objects.requireNonNull(srcUrl);
        Objects.requireNonNull(onError);
        
        return urlFromString(fullUrlFromReference(ref, srcUrl), onError);
    }
    
    /**
     * This method takes a source from a spidered page and then a reference
     * found on that page and returns a proper url from it. If the link is
     * relative, it will prepend the source url
     * 
     * @since 1.0.6
     * @param ref
     * @param srcUrl
     * @param onError a consumer that gets the {@link MalformedURLException} if it is correct
     */
    public static Optional<URL> fullUrlFromReference(final String ref, final URL srcUrl, final Consumer<Exception> onError) {
        Objects.requireNonNull(ref);
        Objects.requireNonNull(srcUrl);
        Objects.requireNonNull(onError);
        
        return urlFromString(fullUrlFromReference(ref, srcUrl.toExternalForm()), onError);
    }
    
    /**
     * This method takes a source from a spidered page and then a reference
     * found on that page and returns a proper url from it. If the link is
     * relative, it will prepend the source url
     * 
     * @since 1.0.6
     * @param ref
     * @param srcUrl
     */
    public static String fullUrlFromReference(final String ref, final String srcUrl) {
        Objects.requireNonNull(ref);
        Objects.requireNonNull(srcUrl);
        
        return ref.matches("^http[s]*:\\/\\/.+")
                ? ref
                : combinePath(srcUrl, ref);
    }
}
