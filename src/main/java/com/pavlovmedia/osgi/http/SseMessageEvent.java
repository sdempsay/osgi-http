package com.pavlovmedia.osgi.http;

import java.util.Optional;

import com.pavlovmedia.osgi.utilities.convertable.ConvertibleAsset;

/**
 * 
 * @author Shawn Dempsay {@literal <sdempsay@pavlovmedia.com>}
 *
 */
public class SseMessageEvent {
    final Optional<String> id;
    final Optional<ConvertibleAsset<String>> event;
    final Optional<ConvertibleAsset<String>> data;
    
    public SseMessageEvent(final Optional<String> id,
            final Optional<ConvertibleAsset<String>> event,
            final Optional<ConvertibleAsset<String>> data) {
        this.id = id;
        this.event = event;
        this.data = data;
    }
}
