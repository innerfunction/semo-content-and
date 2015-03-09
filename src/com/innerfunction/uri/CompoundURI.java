package com.innerfunction.uri;

import android.net.Uri;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.net.URISyntaxException;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * A class for representing compound URIs.
 * A compound URI is a URI with a scheme, name, fragment and parameters. Each parameter is a name
 * assigned to a compound URI value.
 * The general form of a compound URI is:
 *
 *      scheme:name#fragment+param1@value1+param2@value2
 * 
 * All parts other than the scheme are optional. Square brackets are used to delineate parameter values,
 * to avoid ambiguity about where nested URIs end and their parent URIs continue.
 */
public class CompoundURI {

    /** The URI scheme. */
    private String scheme;
    /** The URI name part. */
    private String name;
    /** The URI fragment part. */
    private String fragment;
    /** A map of URI parameter names onto URI values. */
    private Map<String,CompoundURI> parameters = new HashMap<String,CompoundURI>();
    /** Trailing characters appearing after the URI's end. Used when parsing nested URIs. */
    private String trailing;

    /** Create a new URI by cloning the argument. */
    public CompoundURI(CompoundURI uri) {
        this.scheme = uri.scheme;
        this.name = uri.name;
        this.fragment = uri.fragment;
        this.parameters = new HashMap<String,CompoundURI>( uri.parameters );
    }

    /** Create a URI by parsing the argument. */
    public CompoundURI(String uri) throws URISyntaxException {
        this( uri, false );
    }

    /** Create a URI with the specified scheme and name parts. */
    public CompoundURI(String scheme, String name) {
        this.scheme = scheme;
        this.name = name;
    }

    /** Create a new URI by copying an existing URI into a new scheme. */
    public CompoundURI(String scheme, CompoundURI uri) {
        this( uri );
        this.scheme = scheme;
    }

    /**
     * Create a URI by parsing the argument.
     */
    public CompoundURI(String uri, boolean nested) throws URISyntaxException {
        //                            |---| Optional [ at start of URI
        //                            |   | |---------| Optional URI scheme
        //                            |   | |         | |-------------| URI name
        //                            |   | |         | |             ||-------------| URI fragment
        //                            v   v v         v v             vv             v |--| URI parameters
        Pattern p = Pattern.compile("^(\\[)?(?:(\\w+):)?([\\w.,/%_~-]*)(#[\\w./%_~-]*)?(.*)$");
        Matcher m = p.matcher( uri );
        if( m.find() ) {
            String g1 = m.group( 1 );
            boolean bracketed = g1 != null && g1.equals("[");
            boolean parseParams = true;
            this.scheme = m.group( 2 );
            if( this.scheme == null ) {
                this.scheme = "s";
                parseParams = false;
            }
            this.name = m.group( 3 );

            String frag = m.group( 4 );
            this.fragment = frag != null && frag.length() > 0 ? frag.substring( 1 ) : null;

            String params = m.group( 5 );
            this.trailing = params;

            if( parseParams ) {

                Pattern paramPattern = Pattern.compile("^\\+(\\w+)@(.*)$");
                while( params != null && params.length() > 0 ) {
                    m = paramPattern.matcher( params );
                    if( m.find() ) {
                        String pname = m.group( 1 );
                        String pvalue = m.group( 2 );
                        CompoundURI puri = new CompoundURI( pvalue, true );
                        this.parameters.put( pname, puri );
                        params = this.trailing = puri.trailing;
                    }
                    else if( params.charAt( 0 ) == ']' ) {
                        if( bracketed ) {
                            // If URI is bracketed - i.e. starts with [ - then this is the closing bracket.
                            this.trailing = params.substring( 1 );
                            // If this URI isn't nested and there are trailing chars after the ] then parse error.
                            if( this.trailing.length() > 0 && !nested ) {
                                throw new URISyntaxException( uri, "Parse error: Unmatched ] in "+uri);
                            }
                        }
                        else if( nested ) {
                            // The URI is nested so the ] might belong to the parent URI.
                            this.trailing = params;
                        }
                        else {
                            // URI isn't bracketed or nested, so parse error.
                            throw new URISyntaxException( uri, "Parse error: Unmatched ] in "+uri);
                        }
                        // Whatever the situation, a ] indicates the end of this URI so break out of the parse loop.
                        break;
                    }
                    else {
                        throw new URISyntaxException( uri, "Parse error: "+params );
                    }
                }
            }
        }
        else {
            throw new URISyntaxException( uri, "Invalid URI: "+uri );
        }
        // URI decode the name.
        this.name = Uri.decode( this.name );
    }

    public String getScheme() {
        return this.scheme;
    }

    public String getName() {
        return this.name;
    }

    protected void setName(String name) {
        this.name = name;
    }

    public String getFragment() {
        return this.fragment;
    }

    public Map<String,CompoundURI> getParameters() {
        return this.parameters;
    }

    public CompoundURI getParameter(String name) {
        return this.parameters.get( name );
    }

    /** Add additional parameters to the URI. */
    public void addParameters(Map<String,CompoundURI> params) {
        this.parameters.putAll( params );
    }

    /**
     * Return a string containing this URIs canonical form.
     * Different URI strings may map to the same canonical form if they represent the same URI at
     * the semantic level.
     * In a canonical URI:
     * - All parameters are in name alphabetical order;
     * - All parameter URI values are nested in enclosing square brackets;
     * - All parameter string values are represented as string (s:) URIs.
     */
    public String canonicalForm() {
        String[] pnames = new String[ this.parameters.size() ];
        pnames = this.parameters.keySet().toArray( pnames );
        Arrays.sort( pnames );
        StringBuilder params = new StringBuilder();
        for( String name : pnames ) {
            CompoundURI puri = this.parameters.get( name );
            params.append('+');
            params.append( Uri.encode( name ));
            params.append("@[");
            params.append( puri.canonicalForm());
            params.append("]");
        }
        return String.format("%s:%s%s%s",
                Uri.encode( this.scheme ), Uri.encode( this.name ),
                this.fragment != null ? "#"+this.fragment : "",
                params.toString());
    }

    public CompoundURI copyOf() {
        return new CompoundURI( this );
    }

    public CompoundURI copyOfWithFragment(String fragment) {
        CompoundURI uri = copyOf();
        if( uri.fragment != null ) {
            uri.fragment = String.format("%s.%s", uri.fragment, fragment );
        }
        else {
            uri.fragment = fragment;
        }
        return uri;
    }

    @Override
    public String toString() {
        return this.canonicalForm();
    }

    @Override
    public int hashCode() {
        return this.canonicalForm().hashCode();
    }
    
    @Override
    public boolean equals(Object obj) {
        return obj instanceof CompoundURI && obj.toString().equals( toString() );
    }
}
