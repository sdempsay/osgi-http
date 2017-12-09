package com.pavlovmedia.osgi.oss.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Optional;

import org.junit.Test;

import com.pavlovmedia.oss.osgi.http.HttpResponse;
import com.pavlovmedia.oss.osgi.http.HttpVerbs;
import com.pavlovmedia.oss.osgi.http.PavlovHttpClientImpl;
import com.pavlovmedia.oss.osgi.http.Spider;

/**
 * 
 * @author Shawn Dempsay {@literal <sdempsay@pavlovmedia.com>}
 *
 */
public class IntegrationTests {
    @Test
    public void testPavlovgo() throws Exception {
        Optional<HttpResponse> response = new PavlovHttpClientImpl()
                .againstUrl(new URL("http://www.pavlovgo.com"))
                .withVerb(HttpVerbs.GET)
                .usingGzip()
                .execute(System.err::println);
        
        assertTrue(response.isPresent());
        System.out.println(response.get().responseHeaders);
        assertEquals(200, response.get().responseCode);
        assertTrue(response.get().responseStream.isPresent());
        System.out.println(response.get().getResponseText(System.err::println));
    }
    
    @Test
    public void testSpider() throws Exception {
        PavlovHttpClientImpl client = new PavlovHttpClientImpl();
        HashMap<URL, HttpResponse> accumulator = new HashMap<>(); 
        Spider spider = new Spider(client);
        spider.doSpider(new URL("https://repo1.maven.org/maven2/com/pavlovmedia/oss/osgi/"), 
                this::filterWalker, 
                u -> u.toExternalForm().endsWith("maven-metadata.xml"), accumulator, 
                System.err::println);
        accumulator.forEach((u,v) -> System.out.println(String.format("%s -> \n%s\n\n", u, v.getResponseText())));
        assertEquals(5, accumulator.size());
    }
    
    private boolean filterWalker(final URL url) {
        return !Arrays.asList(".pom", ".asc", ".jar", ".md5", "sha1").stream()
            .anyMatch(e -> url.toExternalForm().endsWith(e));
    }
}
