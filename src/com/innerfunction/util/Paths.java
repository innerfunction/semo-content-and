package com.innerfunction.util;

/**
 * Class providing functionality similar to node.js's path module.
 */
public class Paths {

    /** Split a path string into its constituent parts. */
    public static String[] parts(String path) {
        return path.split("/");
    }

    /** Return the path of the parent directory of the specified path. */
    public static String dirname(String path) {
        String[] ps = parts( path );
        StringBuilder sb = new StringBuilder();
        if( path.charAt( 0 ) == '/' ) {
            sb.append('/');
        }
        for( int i = 0, c = 0; i < ps.length - 1; i++ ) {
            if( ps[i].length() > 0 ) {
                if( c > 0 ) {
                    sb.append('/');
                }
                sb.append( ps[i] );
                c++;
            }
        }
        return sb.toString();
    }

    /** Return the base name - e.g. filename of the specified path. */
    public static String basename(String path) {
        String[] ps = parts( path );
        return ps.length > 0 ? ps[ps.length - 1] : "";
    }

    /** Return the extension suffix of the specified path. */
    public static String extname(String path) {
        String base = basename( path );
        if( base != null ) {
            int i = base.lastIndexOf('.');
            if( i > 0 ) {
                return base.substring( i );
            }
        }
        return "";
    }

    /** Return a path name with the extension suffix stripped. */
    public static String stripext(String path) {
        int i = path.lastIndexOf('.');
        return i > -1 ? path.substring( 0, i ) : path;
    }

    /** Join a series of paths into a single path. */
    public static String join(String... paths) {
        StringBuilder sb = new StringBuilder();
        for( String path : paths ) {
            if( path != null ) {
                path = path.trim();
                if( path.length() > 0 ) {
                    int len = sb.length();
                    // Prepend a path separator before the next path component if the path to this
                    // point doesn't already end with a path separator, and path doesn't begin with /.
                    if( len > 0 && sb.charAt( len - 1 ) != '/' && path.charAt( 0 ) != '/' ) {
                        sb.append('/');
                    }
                    sb.append( path );
                }
            }
        }
        return sb.toString();
    }

}
