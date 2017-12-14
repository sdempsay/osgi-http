package com.pavlovmedia.osgi.oss.http;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/**
 * 
 * @author shawn
 *
 */
public class StrTest {

    @Test
    public void convertStrArray() {
        List<String> strList = Arrays.asList("hello", "world");
        
        
        CharSequence cs = strList.stream()
            .reduce((a,b) -> a.concat(b))
            .get();
        
        System.out.println(cs);
    }
}
