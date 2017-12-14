package com.pavlovmedia.oss.osgi.http;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 
 * @author shawn
 *
 */
public class Spider {
    private final PavlovHttpClient baseClient;
    private Function<HttpResponse, Set<URL>> urlParser = this::parseForUrls;
    
    private static final Pattern ANCHOR_SEARCH = Pattern.compile("(?i)<a.+href=\"(?<url>.+?)\"");
    
    public Spider(final PavlovHttpClient baseClient) {
        this.baseClient = baseClient;
    }
    
    public Spider setUrlParser(final Function<HttpResponse, Set<URL>> urlParser) {
        this.urlParser = urlParser;
        return this;
    }
    
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
                urlParser.apply(response.get())
                    .forEach(u -> doSpider(u, follow, save, accumulator, onError));
            }
        }
    }
    
    private Set<URL> parseForUrls(final HttpResponse response) {
        if (response.isValidResponse((e) -> { }) && response.responseHeaders.getOrDefault("Content-Type", 
                Arrays.asList("text/html"))
                .contains("text/html")) {
            // We scan for href links
            ArrayList<String> links = new ArrayList<>();
            Matcher matcher = ANCHOR_SEARCH.matcher(response.getResponseText());
            while (matcher.find()) {
                links.add(matcher.group("url"));
            }
            
            return links.stream()
                   .filter(s -> !(s.startsWith(".") || s.startsWith("..")))
                   .map(s -> fromRef(s, response.srcUrl))
                   .filter(Optional::isPresent)
                   .map(Optional::get)
                   .collect(Collectors.toSet());
        }
        return Collections.emptySet();
    }
    
    private Optional<URL> fromRef(final String ref, final URL srcUrl) {
        try {
            if (ref.matches("~http.+")) {
                    return Optional.of(new URL(ref));
            } else {
                return Optional.of(PavlovHttpClientImpl.combinePath(srcUrl, ref));
            }
        } catch (MalformedURLException e) {
            return Optional.empty();
        }
    }
}
