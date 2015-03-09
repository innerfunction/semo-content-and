package com.innerfunction.util;

import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import com.innerfunction.uri.CompoundURI;
import com.innerfunction.uri.Resource;

public class Configuration extends JSONData {
    
    static final String LogTag = Configuration.class.getSimpleName();
    
    public static final Configuration EmptyConfiguration = new Configuration();
    
    /** The configuration data. */
    private Map<String,Object> data;
    /** The root configuration. Used to evaluate # value references. */
    private Configuration root = this;
    /** The resource the configuration data was read from. */
    private Resource resource;
    /** The value template context. */
    private Map<String,Object> templateContext;
    /** An Android context. */
    private Context androidContext;
    /** Functions for converting between types. */
    private TypeConversions conversions;
    /** Android resources. */
    private Resources r;

    private Configuration() {}
    
    public Configuration(Context context) {
        this( new HashMap<String,Object>(), context );
    }
    
    public Configuration(Map<String,Object> data, Context context) {
        this( data, EmptyConfiguration, context );
    }

    @SuppressWarnings("unchecked")
    public Configuration(Object data, Configuration parent, Context androidContext) {
        if( data instanceof Map ) {
            this.data = (Map<String,Object>)data;
        }
        this.resource = parent.resource;
        this.root = parent.root;
        this.templateContext = parent.templateContext;
        this.androidContext = androidContext;
        initialize();
    }
    
    private Configuration(Configuration config, Configuration parent) {
        data = new HashMap<String,Object>();
        data.putAll( config.data );
        data.putAll( parent.data );
        resource = parent.resource;
        root = parent.root;
        templateContext = new HashMap<String,Object>();
        templateContext.putAll( config.templateContext );
        templateContext.putAll( parent.templateContext );
        initialize();
    }
    
    public Configuration(Resource resource, Context context) {
        this( resource.asJSONData(), resource, context );
    }
    
    @SuppressWarnings("unchecked")
    public Configuration(Object data, Resource resource, Context context) {
        if( data instanceof Map ) {
            this.data = (Map<String,Object>)data;
        }
        this.resource = resource;
        this.root = this;
        this.androidContext = context;
        initialize();
    }
    
    @SuppressWarnings("unchecked")
    public Configuration(Resource resource, Configuration parent) {
        Object data = resource.asJSONData();
        if( data instanceof Map ) {
            this.data = (Map<String,Object>)data;
        }
        else {
            this.data = new HashMap<String,Object>();
        }
        this.resource = resource;
        // If the current resource and the parent's resource share the same scheme and name then they
        // refer to the same container, and this configuration shares the parent's root; otherwise, the
        // root is this resource.
        CompoundURI thisURI = resource.getURI();
        CompoundURI parentURI = parent.resource.getURI();
        if( thisURI.getScheme().equals( parentURI.getScheme() ) && thisURI.getName().equals( parentURI.getName() ) ) {
            this.root = parent.root;
        }
        else {
            this.root = this;
        }
        this.templateContext = parent.templateContext;
        initialize();
    }
    
    private void initialize() {
        this.conversions = TypeConversions.instanceForContext( androidContext );
        this.r = androidContext.getResources();
        // Search the configuration data for any parameter values, and move any found to a separate map.
        Map<String,Object> params = new HashMap<String,Object>();
        Map<String,Object> _data = new HashMap<String,Object>();
        for(String key : this.data.keySet() ) {
            if( key.startsWith("$") ) {
                params.put( key, this.data.get( key ) );
            }
            else {
                _data.put( key,  this.data.get( key ) );
            }
        }
        // Initialize/modify the context with parameter values, if any.
        if( params.size() > 0 ) {
            if( this.templateContext != null ) {
                this.templateContext = new HashMap<String,Object>( this.templateContext );
                this.templateContext.putAll( (Map<String,Object>)params );
            }
            else {
                this.templateContext = params;
            }
            this.data = _data;
        }
        else if( this.templateContext == null ) {
            this.templateContext = new HashMap<String,Object>();
        }
    }
    
    public Map<String,Object> getData() {
        return this.data;
    }
    
    public Resource getBaseResource() {
        return this.resource;
    }
    
    public void setContext(Map<String,Object> context) {
        this.templateContext = context;
    }
    
    public Object getValueAs(String name, final String repr) {
        Object value = resolveJSONReference( name, data, new PropertyHandler() {
            /**
             * Resolve a named property against an object.
             * Handles the conversion of intermediate Resource values, before delegating to the default
             * implementation.
             */
            @Override
            public Object resolve(String name, Object object) {
                if( object instanceof Resource ) {
                    object = ((Resource)object).asJSONData();
                }
                return super.resolve( name, object );
            }
            /**
             * Modify intermediate and final configuration properties as they are resolved along the property path.
             */
            @Override
            public Object modify(String name, Object value) {
                if( value instanceof String ) {
                    // Treat all string values as potential text templates, unless prefixed with `
                    String svalue = (String)value;
                    if( svalue.length() > 0 ) {
                        
                        char prefix = svalue.charAt( 0 );
                        
                        // First, attempt resolving any context references. If these in turn resolve to a
                        // $ or # prefixed value, then they will be resolved in the following code.
                        if( prefix == '$' ) {
                            value = templateContext.get( svalue );
                            if( value instanceof String ) {
                                svalue = (String)value;
                                if( svalue.length() > 0 ) {
                                    prefix = svalue.charAt( 0 );
                                }
                                else return value;
                            }
                            else return value;
                        }
                        
                        if( prefix != '`' ) {
                            svalue = StringTemplate.render( svalue, templateContext );
                            if( svalue.length() > 0 ) {
                                prefix = svalue.charAt( 0 );
                            }
                            else {
                                prefix = 0x00;
                            }
                        }
                        
                        // Any string values starting with a '@' are potentially internal URI references.
                        // Normalize to URI references with a default representation qualifier.
                        // If a dispatcher is also set on this configuration object then attempt to resolve
                        // the URI and return its value instead.
                        if( prefix == '@' ) {
                            String uri = svalue.substring( 1 );
                            value = resource.resolveURIFromString( uri );
                        }
                        // Any string values starting with a '#' are potential path references to other
                        // properties in the same configuration. Attempt to resolve them against the configuration
                        // root; if they don't resolve then return the original value.
                        else if( prefix == '#' ) {
                            value = root.getValueAs( svalue.substring( 1 ), repr );
                            if( value == null ) {
                                value = svalue;
                            }
                        }
                        else if( prefix == '`' ) {
                            value = svalue.substring( 1 );
                        }
                        else if( svalue != null ) {
                            value = svalue;
                        }
                    }
                    else {
                        value = svalue;
                    }
                }
                return value;
            }
        });
        // Perform type conversions according to the requested representation.
        // These are pretty basic:
        // * If the requested representation is 'resource', and the value isn't already a result, then
        //   construct a new resource with the current value. Note that the new resource URI is the same
        //   as this configuration's base resource.
        // Otherwise:
        // * A Resource can be converted to anything its getRepresentation method supports;
        // * A String can be converted to a URL and is valid JSON data;
        // * A Number can be converted to a String and is valid JSON data;
        // * Anything else is only valid JSON data.
        if("resource".equals( repr ) ) {
            if( !(value instanceof Resource || value == null) && androidContext != null ) {
                CompoundURI uri = resource.getURI().copyOfWithFragment( name );
                value = new Resource( androidContext, value, uri, resource );
            }
        }
        else if("configuration".equals( repr ) ) {
            if( !(value instanceof Configuration) ) {
                // If value isn't already a configuration, but is a dictionary then construct a new config using
                // the values in that dictionary...
                if( value instanceof Map && androidContext != null ) {
                    CompoundURI uri = resource.getURI().copyOfWithFragment( name );
                    Resource r = new Resource( androidContext, value, uri, resource );
                    value = new Configuration( r, this );
                }
                // Else if value is a resource, then construct a new config using the resource...
                else if( value instanceof Resource ) {
                    value = new Configuration( (Resource)value, this );
                }
                // Else the value can't be resolved to a resource, so return null.
                else {
                    value = null;
                }
            }
        }
        else if( value instanceof Resource ) {
            value = ((Resource)value).getRepresentation( repr );
        }
        else if(!"json".equals( repr ) ) {
            value = conversions.asRepresentation( value, repr );
        }
        return value;
    }
    
    public boolean hasValue(String name) {
        return resolveJSONReference( name, this.data ) != null;
    }
    
    public String getValueAsString(String name) {
        return getValueAsString( name, null );
    }
    
    public String getValueAsString(String name, String defaultValue) {
        String value = (String)getValueAs( name, "string");
        return value == null ? defaultValue : value;
    }
    
    public String getValueAsLocalizedString(String name) {
        String result = null;
        String value = getValueAsString( name );
        if( value != null ) {
            String packageName = androidContext.getPackageName();
            int rid = r.getIdentifier( value, "string", packageName );
            if( rid > 0 ) {
                result = r.getString( rid );
            }
        }
        return result;
    }
    
    public Number getValueAsNumber(String name) {
        return getValueAsNumber( name, null );
    }
    
    public Number getValueAsNumber(String name, Number defaultValue) {
        Number value = (Number)getValueAs( name, "number");
        return value == null ? defaultValue : value;
    }
    
    public Boolean getValueAsBoolean(String name) {
        return getValueAsBoolean( name, Boolean.FALSE );
    }
    
    public Boolean getValueAsBoolean(String name, Boolean defaultValue) {
        Boolean value = (Boolean)getValueAs( name, "boolean");
        return value == null ? defaultValue : value;
    }
    
    public Date getValueAsDate(String name) {
        return getValueAsDate( name, null );
    }
    
    public Date getValueAsDate(String name, Date defaultValue) {
        Date value = (Date)getValueAs( name, "date");
        return value == null ? defaultValue : value;
    }
    
    public URI getValueAsURL(String name) {
        return (URI)getValueAs( name, "url");
    }
    
    public byte[] getValueAsData(String name) {
        return (byte[])getValueAs( name, "data");
    }
    
    public Drawable getValueAsImage(String name) {
        return (Drawable)getValueAs( name, "image");
    }
    
    public int getValueAsColor(String name) {
        return getValueAsColor( name, 0 );
    }
    
    public int getValueAsColor(String name, Object defaultValue) {
        Object value = getValueAs( name, "default");
        if( value == null ) {
            value = defaultValue;
        }
        return conversions.asColor( value );
    }
    
    public Resource getValueAsResource(String name) {
        return (Resource)getValueAs( name, "resource");
    }
    
    public Object getValue(String name) {
        return getValueAs( name, "default");
    }
    
    public List<String> getValueNames() {
        List<String> names = new ArrayList<String>();
        if( data instanceof Map ) {
            for( String name : ((Map<String,Object>)data).keySet() ) {
                names.add( name );
            }
        }
        return names;
    }
    
    public static enum ValueType { Object, List, String, Number, Boolean, Undefined };
    
    public ValueType getValueType(String name) {
        Object value = getValueAs( name, "json");
        if( value == null )             return ValueType.Undefined;
        if( value instanceof Boolean )  return ValueType.Boolean;
        if( value instanceof Number )   return ValueType.Number;
        if( value instanceof String )   return ValueType.String;
        if( value instanceof List )     return ValueType.List;
        return ValueType.Object;
    }
    
    public Configuration getValueAsConfiguration(String name) {
        return (Configuration)getValueAs( name, "configuration");
    }
    
    public Configuration getValueAsConfiguration(String name, Configuration defaultValue) {
        Configuration result = getValueAsConfiguration( name );
        return result == null ? defaultValue : result;
    }
    
    @SuppressLint("DefaultLocale")
    public List<Configuration> getValueAsConfigurationList(String name) {
        List<Configuration> result = new ArrayList<Configuration>();
        Object _value = getValue( name );
        if( !(_value instanceof List) ) {
            _value = getValueAs( name, "json");
        }
        if( _value instanceof List ) {
            @SuppressWarnings("rawtypes")
            List values = (List)_value;
            for( int i = 0; i < values.size(); i++ ) {
                String itemName = String.format("%s.%d", name, i );
                Configuration item = getValueAsConfiguration( itemName );
                result.add( item );
            }
        }
        return result;
    }
    
    public Map<String,Configuration> getValueAsConfigurationMap(String name) {
        Map<String,Configuration> result = new HashMap<String,Configuration>();
        Object _value = getValue( name );
        if( _value instanceof Map ) {
            @SuppressWarnings("rawtypes")
            Map values = (Map)_value;
            for( Object key : values.keySet() ) {
                String itemName = String.format("%s.%s", name, key );
                Configuration item = getValueAsConfiguration( itemName );
                result.put( key.toString(), item );
            }
        }
        return result;
    }
    
    public Configuration mergeConfiguration(Configuration otherConfig) {
        // NOTE: 'otherConfig' is used as the parent config here in order to pass it's root property
        // to the new config. This suits the particular use cases for mergeConfiguration, and matches
        // the priority order of this method (i.e. otherConfig's properties overwrite properties of 
        // this). In cases where this.root should be preserved, use the extend() method instead.
        return new Configuration( this, otherConfig );
    }
    
    /**
     * Extend this configuration with the specified set of parameters.
     * As well as being added to the current configuration (see Configuration.extend), the parameters are
     * added to the current template scope with a $ prefix before each parameter name. This allows parameters
     * to then be used in two ways from within a configuration:
     * - As direct parameter references, by using a value with the $ prefix, e.g. "$param1";
     * - As parameter references from within template strings, e.g. "view:X+id@{$param1}"
     */
    public Configuration extendWithParameters(Map<String,Resource> params) {
        Configuration result = this;
        if( params.size() > 0 ) {
            result = new Configuration( data, this, androidContext );
            result.templateContext = new HashMap<String,Object>( result.templateContext );
            for( String name : params.keySet() ) {
                result.templateContext.put("$"+name, params.get( name ) );
            }
        }
        return result;
    }
    
    /** Modify this configuration with a set of new values. */
    public void modify(Map<String,Object> data) {
        // Create a copy of the config's data and then add the new data.
        this.data = new HashMap<String,Object>( this.data );
        this.data.putAll( data );
    }
    
    /** Modify a single value in this configuration's data. */
    public void modify(String name, Object value) {
        modify( Maps.mapWithEntry( name, value ) );
    }
    
    /** Normalize this configuration by flattening "config" properties and resolving "extends" properties. */
    public Configuration normalize() {
        // Start by flattening this configuration (i.e. merging its "config" property into the top level).
        Configuration result = flatten();
        // Next, start processing the "extends" chain...
        Configuration current = result;
        // A set of previously visited parent configurations, to detect dependency loops.
        Set<Configuration> visited = new HashSet<Configuration>();
        while( current.getValueType("extends") == ValueType.Object ) {
            current = current.getValueAsConfiguration("extends");
            if( visited.contains( current ) ) {
                // Dependency loop detected, stop extending the config.
                break;
            }
            visited.add( current );
            result = current.flatten().mergeConfiguration( result );
        }
        return result;
    }
    
    /**
     * Flatten the configuration by merging its "config" property (if any) into the top level properties.
     */
    public Configuration flatten() {
        Configuration result = this;
        if( getValueType("config") == ValueType.Object ) {
            result = mergeConfiguration( getValueAsConfiguration("config") );
        }
        return result;
    }
    
    @Override
    public int hashCode() {
        return this.resource != null ? this.resource.hashCode() : super.hashCode();
    }
    
    @Override
    public boolean equals(Object obj) {
        // Two configurations are equal if they have the same source resource.
        return obj instanceof Configuration && this.resource != null && this.resource.equals( ((Configuration)obj).resource );
    }
    
    @Override
    public String toString() {
        return data == null ? "<null data>" : data.toString();
    }
}
