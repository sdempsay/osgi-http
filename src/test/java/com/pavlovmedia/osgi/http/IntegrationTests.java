package com.pavlovmedia.osgi.http;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Optional;

import org.junit.Test;

import com.pavlovmedia.osgi.http.impl.PavlovHttpClientImpl;

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
                .execute(System.err::println);
        
        assertTrue(response.isPresent());
        assertEquals(200, response.get().responseCode);
        assertTrue(response.get().responseStream.isPresent());
        System.out.println(response.get().responseStream.get().convert(IntegrationTests::convert));
    }
    
    @Test
    public void testEmptyPost() throws Exception {
        Optional<HttpResponse> response = new PavlovHttpClientImpl()
                .againstUrl(new URL("http://172.16.46.28:2375/containers/nginx/start"))
                .withVerb(HttpVerbs.POST)
                .execute(System.err::println);
        assertTrue(response.isPresent());
        assertTrue(response.get().responseStream.isPresent());
        System.out.println(response.get().responseStream.get().convert(IntegrationTests::convert));
    }
    
    @Test
    public void testPost() throws Exception {
        Optional<HttpResponse> response = new PavlovHttpClientImpl()
                .againstUrl(new URL("http://172.16.46.28:2375/volumes/create"))
                .withVerb(HttpVerbs.POST)
                .withContentType("application/json")
                .withData("{ \"name\": \"testPost\" }")
                .execute(System.err::println);
        response.get().errorStream.ifPresent(e -> System.out.println(e.convert(IntegrationTests::convert)));
        assertTrue(response.isPresent());
        assertTrue(response.get().responseStream.isPresent());
        System.out.println(response.get().responseStream.get().convert(IntegrationTests::convert));
    }
    
    public static String convert(final InputStream is) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            return reader.lines().reduce((t, u) -> String.format("%s%s%s", t, System.lineSeparator(), u))
                    .orElse("No data");
        } catch (IOException e) {
            return e.getMessage();
        }
    }
}
