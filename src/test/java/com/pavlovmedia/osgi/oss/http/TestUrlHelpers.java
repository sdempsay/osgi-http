package com.pavlovmedia.osgi.oss.http;

import static org.junit.Assert.assertEquals;

import java.net.URL;

import org.junit.Test;

import com.pavlovmedia.oss.osgi.http.UrlHelpers;

/**
 * 
 * @author Shawn Dempsay {@literal <sdempsay@pavlovmedia.com>}
 *
 */
public class TestUrlHelpers {
    @Test
    public void testCombineParts() {
        assertEquals("http://a/b/c", 
                UrlHelpers.combineUrlParts("http://a/", "/b/", "/c"));
    }
    
    @Test
    public void testCombinePathString() {
        assertEquals("http://a/b/c/", 
                UrlHelpers.combinePath("http://a/b/", "/c/"));
    }
    
    @Test
    public void testCombinePathURL() throws Exception {
        assertEquals("http://a/b/c/", 
                UrlHelpers.combinePath(new URL("http://a/b/"), "/c/"));
    }
    
    @Test
    public void testFromRefUseString() {
        assertEquals("http://a/b/", 
                UrlHelpers.fullUrlFromReference("http://a/b/", "http://a"));
    }
    
    @Test
    public void testFromRefAppendString() {
        assertEquals("http://a/b/", 
                UrlHelpers.fullUrlFromReference("/b/", "http://a"));
    }
    
    @Test
    public void testFromRefOptUseString() throws Exception {
        assertEquals(new URL("http://a/b/"), 
                UrlHelpers.fullUrlFromReference("http://a/b/", "http://a", System.out::println).get());
    }
    
    @Test
    public void testFromRefOptAppendString() throws Exception {
        assertEquals(new URL("http://a/b/"), 
                UrlHelpers.fullUrlFromReference("/b/", "http://a", System.out::println).get());
    }
    
    @Test
    public void testFromRefOptUseURL() throws Exception {
        assertEquals(new URL("http://a/b/"), 
                UrlHelpers.fullUrlFromReference("http://a/b/", new URL("http://a"), System.out::println).get());
    }
    
    @Test
    public void testFromRefOptAppendURL() throws Exception {
        assertEquals(new URL("http://a/b/"), 
                UrlHelpers.fullUrlFromReference("/b/", new URL("http://a"), System.out::println).get());
    }
}
