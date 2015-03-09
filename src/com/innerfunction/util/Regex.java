package com.innerfunction.util;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility regex methods.
 * Presents a cleaner API for working with regular expressions.
 * @author juliangoacher
 *
 */
public class Regex {

    private String p;
    
    public Regex(String p) {
        this.p = p;
    }
    
    public boolean test(String s) {
        return Regex.test( p, s );
    }
    
    public String[] matches(String s) {
        return Regex.matches( p, s );
    }

    /**
     * A map of previously compiled regexs, keyed by pattern.
     * Patterns are thread safe.
     */
    static final Map<String,Pattern> Patterns = new HashMap<String,Pattern>();
    
    static Matcher getMatcher(String p, String s) {
        Pattern pattern = Patterns.get( p );
        if( pattern == null ) {
            pattern = Pattern.compile( p );
            Patterns.put( p, pattern );
        }
        return pattern.matcher( s );
    }
    
    /**
     * Test if a string matches the specified regex.
     * @param p
     * @param s
     * @return
     */
    public static boolean test(String p, String s) {
        return getMatcher( p, s ).find();
    }
    
    /**
     * Return match groups for the specified regex against the specified string.
     * @param p
     * @param s
     * @return
     */
    public static String[] matches(String p, String s) {
        String[] result = null;
        Matcher matcher = getMatcher( p, s );
        if( matcher.matches() ) {
            int gc = matcher.groupCount() + 1;
            result = new String[gc];
            int i = 0;
            try {
                for( i = 0; i < gc; i++ ) {
                    result[i] = matcher.group( i );
                }
            }
            catch(IllegalStateException e) {
                // Useless exception, but can none the less be thrown (despite matcher.matches() returning true).
                // Resize the result array to reflect this error.
                String[] _result = new String[i];
                for( int j = 0; j < i; j++ ) {
                    _result[j] = result[j];
                }
                result = _result;
            }
        }
        return result;
    }

}
