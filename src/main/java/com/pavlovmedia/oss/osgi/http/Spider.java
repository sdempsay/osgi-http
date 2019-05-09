package com.pavlovmedia.oss.osgi.http;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * This is a spider class you can use to spider out to other websites.
 * 
 * @author Shawn Dempsay {@literal <sdempsay@pavlovmedia.com>}
 *
 */
public class Spider {
    /** A pre-set client that can have headers like authentication attached */
    private final PavlovHttpClient baseClient;
    
    /** This is a function that can look at an HTTP Response and turn it into a set of unique URLs */
    private BiFunction<HttpResponse, Consumer<Exception>, Set<URL>> urlParser = this::parseForUrls;
    
    /**
     * This is a pattern to find URLs in an HTML page
     * @since 1.0.6
     */
    public static final Pattern ANCHOR_SEARCH = Pattern.compile("(?i)<a.+href=\"(?<url>.+?)\"");
    
    /**
     * Sets up a spider using a client that can have additional behaviors
     * like custom headers and authentication.
     * 
     * @since 1.0.0
     * @param baseClient
     */
    public Spider(final PavlovHttpClient baseClient) {
        Objects.requireNonNull(baseClient);
        
        this.baseClient = baseClient;
    }
    
    /**
     * Sets a new parser for taking an HTML response and getting URLs
     * 
     * @deprecated Use {@link #setUrlParser(BiFunction)} instead
     * @since 1.0.0
     * @param urlParser
     */
    public Spider setUrlParser(final Function<HttpResponse, Set<URL>> urlParser) {
        Objects.requireNonNull(urlParser);
        
        this.urlParser = (r, e) -> urlParser.apply(r);
        return this;
    }
    
    /**
     * Sets a new parser for taking an HTML response and getting URLs
     * 
     * @since 1.0.6
     * @param urlParser
     */
    public Spider setUrlParser(final BiFunction<HttpResponse, Consumer<Exception>, Set<URL>> urlParser) {
        Objects.requireNonNull(urlParser);
        
        this.urlParser = urlParser;
        return this;
    }
    
    /**
     * Executes a spider operation with a URL to start from, and Predicate to know when to follow a link,
     * a predicate for when to save a response and an accumulator to keep the URL to Response mapping.
     * 
     * @since 1.0.0
     * @param startUrl The URL to start from
     * @param follow Should a link parsed from the page be followed
     * @param save Should this page be saved
     * @param accumulator A place to save pages
     * @param onError
     */
    public void doSpider(final URL startUrl, final Predicate<URL> follow, final Predicate<URL> save, 
            final Map<URL,HttpResponse> accumulator, 
            final Consumer<Exception> onError) {
        if (accumulator.containsKey(startUrl)) {
            return;
        }
        
        if (save.test(startUrl)) {
            Optional<HttpResponse> response = baseClient.clone()
                    .againstUrl(startUrl)
                    .withVerb(HttpVerbs.GET)
                    .execute(onError);
            response.ifPresent(r -> accumulator.put(startUrl, r));
        } else if (follow.test(startUrl)) {
            Optional<HttpResponse> response = baseClient.clone()
                .againstUrl(startUrl)
                .withVerb(HttpVerbs.GET)
                .execute(onError);
            if (response.isPresent()) {
                urlParser.apply(response.get(), onError)
                    .forEach(u -> doSpider(u, follow, save, accumulator, onError));
            }
        }
    }
    
    /**
     * A built-in version of a url parser. This one covers our common cases when scanning
     * general http sites.
     * 
     * @param response
     */
    private Set<URL> parseForUrls(final HttpResponse response, final Consumer<Exception> onError) {
        if (response.isValidResponse() && response.responseHeaders.getOrDefault("Content-Type", 
                Arrays.asList("text/html")).stream()
                    .anyMatch(s -> s.contains("text/html"))) {
            // We scan for href links
            ArrayList<String> links = new ArrayList<>();
            Matcher matcher = ANCHOR_SEARCH.matcher(response.getResponseText());
            while (matcher.find()) {
                links.add(matcher.group("url"));
            }
            
            return links.stream()
                   .filter(s -> !s.startsWith("."))
                   .map(s -> UrlHelpers.fullUrlFromReference(s, response.srcUrl, e -> { })) // XXX: Don't use onError here?
                   .filter(Optional::isPresent)
                   .map(Optional::get)
                   .collect(Collectors.toSet());
        }
        return Collections.emptySet();
    }
}
