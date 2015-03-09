package com.innerfunction.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class StringTemplate {

    static abstract class Block extends JSONData {
        abstract String eval(Object context);
    }

    /** Regex pattern for matching var references within a template string. */
    static private Pattern p = Pattern.compile("^([^{]*)[{]([-a-zA-Z0-9_$.]+)[}](.*)$");
    /** An array of parsed template blocks. */
    private List<Block> blocks = new ArrayList<Block>();

    public StringTemplate(String s) {
        while( s.length() > 0 ) {
            Matcher m = p.matcher( s );
            if( m.find() ) {
                this.blocks.add( this.newTextBlock( m.group( 1 )));
                this.blocks.add( this.newRefBlock( m.group( 2 )));
                s = m.group( 3 );
            }
            else {
                int i = s.indexOf('}') + 1;
                if( i > 0 ) {
                    this.blocks.add( this.newTextBlock( s.substring( 0, i )));
                    s = s.substring( i++ );
                }
                else {
                    this.blocks.add( this.newTextBlock( s ));
                    break;
                }
            }
        }
    }

    private Block newTextBlock(final String text) {
        return new Block() {
            @Override
            String eval(Object context) {
                return text;
            }
            @Override
            public String toString() {
                return text;
            }
        };
    }

    private Block newRefBlock(final String ref) {
        return new Block() {
            @Override
            String eval(Object context) {
                Object value = super.resolveJSONReference( ref, context );
                return value == null ? "" : value.toString();
            }
            @Override
            public String toString() {
                return "{"+ref+"}";
            }
        };
    }

    public String render(Object context) {
        StringBuilder sb = new StringBuilder();
        for( Block b : this.blocks ) {
            sb.append( b.eval( context ));
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        for( Block b : blocks ) sb.append( b );
        return sb.toString();
    }

    public static StringTemplate templateWithString(String s) {
        return new StringTemplate( s );
    }

    public static String render(String s, Object context) {
        return new StringTemplate( s ).render( context );
    }

}
