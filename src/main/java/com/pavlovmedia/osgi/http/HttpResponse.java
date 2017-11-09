package com.pavlovmedia.osgi.http;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.pavlovmedia.osgi.utilities.convertable.ConvertibleAsset;

/**
 * 
 * @author Shawn Dempsay {@literal <sdempsay@pavlovmedia.com>}
 *
 */
public class HttpResponse {
    public final int responseCode;
    
    public final Optional<ConvertibleAsset<InputStream>> errorStream;
    
    public final Optional<ConvertibleAsset<InputStream>> responseStream;
    
    public final Map<String,List<String>> responseHeaders;
    
    public HttpResponse(final int responseCode, 
            final Optional<ConvertibleAsset<InputStream>> errorStream,
            final Optional<ConvertibleAsset<InputStream>> responseStream,
            final Map<String,List<String>> responseHeaders) {
        this.responseCode = responseCode;
        this.errorStream = errorStream;
        this.responseStream = responseStream;
        this.responseHeaders = responseHeaders;
    }
}
