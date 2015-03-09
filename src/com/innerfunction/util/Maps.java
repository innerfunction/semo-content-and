package com.innerfunction.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Utililty map functions.
 * @author juliangoacher
 *
 */
public class Maps {

    /**
     * Return a new modifiable version of a map containing all the original's entries.
     * New entries can be added to the new version without modifying the original.
     * Note that item deletion is only partly supported. Items can be added and removed
     * from the set of modified entries; but removing an item that only exists in the
     * original map will not have any effect.
     * @param map
     * @return
     */
    public static <K, V> Map<K, V> extend(final Map<K, V> map) {
        return new HashMap<K,V>() {
            private static final long serialVersionUID = 1L;
            @Override
            public V get(Object key) {
                return super.containsKey( key ) ? super.get( key ) : map.get( key );
            }
            @Override
            public boolean containsKey(Object key) {
                return super.containsKey( key ) || map.containsKey( key );
            }
            @Override
            public V remove(Object key) {
                if( super.containsKey( key ) ) {
                    return super.remove( key );
                }
                return map.remove( key );
            }
            @Override
            public Set<K> keySet() {
                HashSet<K> keys = new HashSet<K>();
                keys.addAll( super.keySet() );
                keys.addAll( map.keySet() );
                return keys;
            }
            @Override
            public Set<Entry<K,V>> entrySet() {
                HashSet<Entry<K,V>> entries = new HashSet<Entry<K,V>>();
                entries.addAll( super.entrySet() );
                entries.addAll( map.entrySet() );
                return entries;
            }
            @Override
            public String toString() {
                StringBuffer sb = new StringBuffer("{ ");
                boolean delimit = false;
                for( Object k : keySet() ) {
                    if( delimit ) {
                        sb.append(", ");
                    }
                    else {
                        delimit = true;
                    }
                    sb.append( k );
                    sb.append(" : ");
                    sb.append( get( k ) );
                }
                sb.append(" }");
                return sb.toString();
            }
        };
    }
    
    /**
     * Extend a map and add the specified key/value pair.
     * The original map will not be modified.
     * @param map
     * @param key
     * @param value
     * @return
     */
    public static <K, V> Map<K, V> extend(Map<K,V> map, K key, V value) {
        Map<K, V> result = extend( map );
        result.put( key, value );
        return result;
    }
    
    /**
     * Join two maps into a single map.
     * @param map0 The first map to join.
     * @param map1 The second map to join. Values in this map will overwrite values with the same key in the first map.
     * @return
     */
    public static <K, V> Map<K, V> join(Map<K, V> map0, Map<K, V> map1) {
        Map<K, V> result = extend( map0 );
        result.putAll( map1 );
        return result;
    }

    /**
     * Return a map populated with the specified name/value.
     */
    public static <K, V> Map<K, V> mapWithEntry(K name, V value) {
        Map<K, V> result = new HashMap<K, V>( 1 );
        result.put( name, value );
        return result;
    }

}
