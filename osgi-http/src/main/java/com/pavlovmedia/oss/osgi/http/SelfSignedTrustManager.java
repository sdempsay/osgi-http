package com.pavlovmedia.oss.osgi.http;
import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;

/**
 * This is an "answer yet" trust manager for cowboys
 * @author Shawn Dempsay {@literal <sdempsay@pavlovmedia.com>}
 *
 */
public class SelfSignedTrustManager implements X509TrustManager {
    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }

    @Override
    public void checkClientTrusted(final X509Certificate[] certs, final String authType) {
    }

    @Override
    public void checkServerTrusted(final X509Certificate[] certs, final String authType) {
    }


}