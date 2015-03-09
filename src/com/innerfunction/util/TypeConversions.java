package com.innerfunction.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.json.simple.JSONValue;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.Log;

/**
 * Standard EP value type conversions.
 * @author juliangoacher
 *
 */
public class TypeConversions {

    static final String LogTag = TypeConversions.class.getSimpleName();
    static Map<Context,TypeConversions> InstancesByContext = new HashMap<Context,TypeConversions>();
    
    private Context context;
    private Resources r;
    private Pattern jsonPattern;
    
    public TypeConversions() {}
    
    public TypeConversions(Context context) {
        this.context = context;
        this.r = context.getResources();
        this.jsonPattern = Pattern.compile("^\\s*([{\\[\"\\d]|true|false)");

    }
    
    public static TypeConversions instanceForContext(Context context) {
        TypeConversions instance = InstancesByContext.get( context );
        if( instance == null ) {
            instance = new TypeConversions( context );
            InstancesByContext.put( context, instance );
        }
        return instance;
    }

    /**
     * Convert value to a string:
     *  String -> String
     *  Number -> Number.toString()
     *  byte[] -> new String( byte[], "UTF-8") 
     *  * -> Object.toString()
     */
    public String asString(Object value) {
        String result = null;
        if( value instanceof String ) {
            result = (String)value;
        }
        else if( value instanceof Number ) {
            result = value.toString();
        }
        else if( value instanceof byte[] ) {
            try {
                result = new String( (byte[])value, "UTF-8");
            }
            catch(UnsupportedEncodingException e) {
                Log.w( LogTag, e );
            }
        }
        else if( value != null ) {
            result = value.toString();
        }
        return result;
    }
    
    /**
     * Convert value to a number:
     *  Number -> Number
     *  * -> null
     */
    public Number asNumber(Object value) {
        Number result = null;
        if( value instanceof Number ) {
            result = (Number)value;
        }
        return result;
    }
    
    /**
     * Convert value to a boolean:
     *  Number -> Boolean
     *  * -> false
     */
    public Boolean asBoolean(Object value) {
        Boolean result = null;//Boolean.FALSE;
        if( value instanceof Boolean ) {
            result = (Boolean)value;
        }
        else {
            Number nvalue = asNumber( value );
            if( nvalue != null ) {
                // Any non-zero integer value evaluates to true.
                result = nvalue.intValue() != 0;
            }
        }
        return result;
    }
    
    /**
     * Convert value to a date:
     *  Date -> Date
     *  Number -> new Date (millisecond value)
     *  * -> String -> new Date (ISO8601 value)
     */
    public Date asDate(Object value) {
        Date result = null;
        if( value instanceof Date ) {
            result = (Date)value;
        }
        else if( value instanceof Number ) {
            result = new Date( ((Number)value).longValue() ); 
        }
        else if( value != null ) {
            String svalue = asString( value );
            try {
                result = ISO8601.toDate( svalue );
            }
            catch(ParseException e) {
                Log.w(LogTag, e);
            }
        }
        return result;
    }
    
    /**
     * Convert value to a URL:
     *  * -> String -> URI
     */
    public URI asURL(Object value) {
        try {
            String uri = asString( value );
            if( uri != null ) {
                return new URI( asString( value ) );
            }
        }
        catch(URISyntaxException e) {
            Log.w(LogTag, e);
        }
        return null;
    }
    
    /**
     * Convert value to data:
     *  * -> String -> byte[]
     */
    public byte[] asData(Object value) {
        byte[] result = null;
        if( value instanceof byte[] ) {
            result = (byte[])value;
        }
        else if( value != null ) {
            String svalue = asString( value );
            try {
                result = svalue.getBytes("UTF-8");
            }
            catch(UnsupportedEncodingException e) {
                Log.w(LogTag, e);
            }
        }
        return result;
    }
    
    /**
     * Convert value to an image:
     *  * -> String -> Drawable (string interpreted as image resource name; if not found, then as asset name). 
     */
    public Drawable asImage(Object value) {
        Drawable result = null;
        if( value != null && this.context != null && this.r != null ) {
            String name = asString( value );
            if( name != null ) {
                int id = ImageUtil.imageNameToResourceID( name, this.r, this.context );
                if( id > 0 ) {
                    result = this.r.getDrawable( id );
                    Log.d(LogTag, String.format("Resolved image resource %s -> %d", name, id ));
                }
                else {
                    InputStream in = null;
                    try {
                        in = this.context.getAssets().open( name );
                        return Drawable.createFromStream( in, name );
                    }
                    catch(FileNotFoundException fnfe) {
                        Log.e(LogTag, String.format("Asset not found: %s", name ));
                    }
                    catch(IOException ioe) {
                        Log.e(LogTag, String.format("Reading image from %s", name ), ioe );
                    }
                    finally {
                        try {
                            in.close();
                        }
                        catch(Exception e) {}
                    }
                }
            }
        }
        return result;
    }
    
    /**
     * Convert a value to a color.
     */
    public int asColor(Object value) {
        int result = 0;
        if( value instanceof Number ) {
            result = ((Number)value).intValue();
        }
        else if( value != null ) {
            try {
                result = Color.parseColor( value.toString() );
            }
            catch (Exception e) {
                Log.w( LogTag, String.format("Error parsing color '%s'", value ));
            }
        }
        return result;
    }

    /**
     * Convert the value to parsed JSON data.
     * Assumes the string representation of the value is valid JSON.
     * * -> String -> <parse JSON> -> Object
     */
    public Object asJSONData(Object value) {
        Object result = null;
        if( value instanceof String ) {
            // NOTE: This is different from the iOS implementation, which
            // converts to data (i.e. NSData) before parsing the JSON, due
            // to the format of the iOS API call.
            String svalue = (String)value;
            // If the string value looks like JSON then try parsing as JSON...
            if( svalue != null && jsonPattern.matcher( svalue ).find() ) {
                try {
                    result = JSONValue.parseWithException( svalue );
                }
                catch(org.json.simple.parser.ParseException e) {
                    Log.e(LogTag, "Parsing JSON", e );
                    result = svalue;
                }
            }
            else {
                result = value;
            }
        }
        else {
            result = value;
        }
        return result;
    }
    
    /**
     * Convert to the named representation.
     * Recognized representation names are:
     * - string
     * - number
     * - boolean (returned as a number, to confirm with the return type).
     * - date
     * - url
     * - data
     * - image
     * - json
     * - default (returns the unchanged value).
     */
    public Object asRepresentation(Object value, String name) {
        if("string".equals( name ) ) {
            return asString( value );
        }
        if("number".equals( name ) ) {
            return asNumber( value );
        }
        if("boolean".equals( name ) ) {
            return asBoolean( value );
        }
        if("date".equals( name ) ) {
            return asDate( value );
        }
        if("url".equals( name ) ) {
            return asURL( value );
        }
        if("data".equals( name ) ) {
            return asData( value );
        }
        if("image".equals( name ) ) {
            return asImage( value );
        }
        if("json".equals( name ) ) {
            return asJSONData( value );
        }
        if("default".equals( name ) ) {
            return value;
        }
        return null;
    }
}
