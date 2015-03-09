package com.innerfunction.util;

import java.util.List;
import java.util.Map;

/**
 * Abstract base class for classes which read data from parsed JSON.
 * Assumes a JSON parser which maps objects to Map instances and arrays to List instances.
 * See for example https://code.google.com/p/json-simple/
 */
public abstract class JSONData {

    /**
     * An object describing how properties are resolved and modified as a JSON reference is resolved.
     * @author juliangoacher
     *
     */
    public static class PropertyHandler {
        /** Resolve a named property against an object. */
        @SuppressWarnings("rawtypes")
        public Object resolve(String name, Object object) {
            if( object instanceof Map ) {
                object = modify( name, ((Map)object).get( name ) );
            }
            else if( object instanceof List ) {
                try {
                    // Attempt to read the next value using 'name' as the list offset.
                    int i = Integer.valueOf( name );
                    object = modify( name, ((List)object).get( i ) );
                }
                catch(NumberFormatException e) {
                    object = null;
                }
            }
            else {
                object = null;
            }
            return object;
        }
        /** Modify a property value after resolution. */
        public Object modify(String name, Object value) {
            return value;
        }
    }

    /** A non-modifying property modifier. Returns properties unmodified. */
    public static final PropertyHandler DefaultPropertyHandler = new PropertyHandler();
    
    public Object resolveJSONReference(String ref, Object data) {
        return resolveJSONReference( ref, data, DefaultPropertyHandler );
    }

    /**
     * Resolve a dotted path reference on the specified data.
     */
    public Object resolveJSONReference(String ref, Object data, PropertyHandler handler) {
        Object result = data;
        String[] path = ref.split("\\.");
        for( int i = 0; i < path.length && result != null; i++ ) {
            String name = path[i];
            result = handler.resolve( name, result );
        }
        return result;
    }

}
