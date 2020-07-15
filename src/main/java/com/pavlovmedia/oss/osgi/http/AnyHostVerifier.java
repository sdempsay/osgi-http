package com.pavlovmedia.oss.osgi.http;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

/**
 * Deals with ignoring alternative names in self-signed certs and IP Address urls
 * 
 * @author Shawn Dempsay {@literal <sdempsay@pavlovmedia.com>}
 *
 */
public class AnyHostVerifier implements HostnameVerifier {

    @Override
    public boolean verify(final String hostname, final SSLSession session) {
        return true;
    }

}
