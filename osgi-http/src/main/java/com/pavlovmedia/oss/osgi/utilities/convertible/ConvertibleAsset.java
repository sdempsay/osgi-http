package com.pavlovmedia.oss.osgi.utilities.convertible;

import java.util.Objects;
import java.util.function.Function;

/**
 * This is an asset that can be converted
 * @author Shawn Dempsay {@literal <sdempsay@pavlovmedia.com>}
 * @param <T>
 */
public class ConvertibleAsset<T> {
    private final T asset;
    
    public ConvertibleAsset(final T asset) {
        Objects.requireNonNull(asset);
        this.asset = asset;
    }
    
    /**
     * Gets the asset contained in this convertible
     */
    public T get() {
        return asset;
    }
    
    /**
     * Converts this asset from one type to another (or
     * even the same time)
     * 
     * @param converter The converter to run
     */
    public <V> V convert(final Function<T,V> converter) {
        return converter.apply(asset);
    }
    
    /**
     * Creates a convertible chain so that you can apply multiple
     * conversions in a row.
     * 
     * @param converter a converter that changes the asset
     * @return the response from the converter wrapped in a convertible
     */
    public <V> ConvertibleAsset<V> chain(final Function<T,V> converter) {
        return new ConvertibleAsset<V>(converter.apply(asset));
    }
}
