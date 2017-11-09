package com.pavlovmedia.osgi.http;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A container for a set of exceptions.
 * 
 * @author Shawn Dempsay {@literal <sdempsay@pavlovmedia.com>}
 *
 */
public class HttpExceptionCollection extends RuntimeException {
    private static final long serialVersionUID = -2752741093973059572L;
    public final List<Exception> exceptions;
    
    public HttpExceptionCollection(final String message, final List<Exception> exceptions) {
        super(message);
        this.exceptions = Collections.unmodifiableList(exceptions);
    }
    
    public HttpExceptionCollection(final String message, final Exception...exceptions) {
        super(message);
        this.exceptions = Collections.unmodifiableList(Arrays.asList(exceptions));
    }
    
    public HttpExceptionCollection(final String message) {
        super(message);
        this.exceptions = Collections.emptyList();
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("HttpCollectionException: "+getMessage());
        exceptions.forEach(e -> sb.append(String.format("\n\t %s", e.getMessage())));
        return sb.toString();
    }
}
