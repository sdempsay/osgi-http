package com.pavlovmedia.osgi.oss.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.URL;
import java.util.Optional;

import org.junit.Test;

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
}
