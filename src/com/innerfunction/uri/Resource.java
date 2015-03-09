package com.innerfunction.uri;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Log;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import com.innerfunction.util.TypeConversions;

/**
 * Class representing a resource referenced by an internal compound URI.
 */
public class Resource implements URIResolver {

    private static final String LogTag = Resource.class.getSimpleName();
    /** The resource item. */
    private Object item;
    /** A URI resolver. */
    protected URIResolver resolver;
    /** The URI used to reference the resource. */
    protected CompoundURI uri;
    /** Scheme context for resolving relative URIs against this resource. */
    private Map<String,CompoundURI> schemeContext;
    /** The app context. */
    protected Context context;
    /** Type conversions. */
    protected TypeConversions conversions;
    /** Whether the data encapsulated by this resource is updateable. */
    protected boolean updateable;
    
    // This constructor intended for NullResource.
    private Resource() {
        this.conversions = new TypeConversions();
    }

    // This constructor intended for use only by Core.
    protected Resource(Context context) {
        this.context = context;
        this.conversions = TypeConversions.instanceForContext( context );
        this.schemeContext = new HashMap<String,CompoundURI>();
    }

    public Resource(Context context, Object item, CompoundURI uri, Resource parent) {
        this.context = context;
        this.item = item;
        this.uri = uri;
        this.conversions = TypeConversions.instanceForContext( context );
        this.schemeContext = new HashMap<String,CompoundURI>( parent.schemeContext );
        // Set the scheme context for this resource. Copies each uri by scheme into the context, before
        // adding the resource's uri by scheme.
        Map<String,CompoundURI> parameters = uri.getParameters();
        for( String name : parameters.keySet() ) {
            CompoundURI puri = parameters.get( name );
            this.schemeContext.put( puri.getScheme(), puri );
        }
        this.schemeContext.put( uri.getScheme(),  uri );
        this.resolver = parent.resolver;
    }

    protected void setItem(Object item) {
        this.item = item;
    }

    public Object getItem() {
        return item;
    }

    public Map<String,CompoundURI> getSchemeContext() {
        return this.schemeContext;
    }

    public CompoundURI getURI() {
        return this.uri;
    }

    public Context getContext() {
        return this.context;
    }

    public Object asDefault() {
        return item;
    }

    public String asString() {
        return conversions.asString( this.item );
    }

    public Number asNumber() {
        return conversions.asNumber( this.item );
    }

    public Boolean asBoolean() {
        return conversions.asBoolean( this.item );
    }

    public Object asJSONData() {
        return conversions.asJSONData( this.item );
    }

    public URI asURL() {
        return conversions.asURL( this.item );
    }

    public Drawable asImage() {
        return conversions.asImage( this.item );
    }

    public Object getRepresentation(String name) {
        if("json".equals( name ) ) {
            return asJSONData();
        }
        return conversions.asRepresentation( this.item, name );
    }

    /**
     * Refresh the resource by resolving its URI again and returning the result.
     */
    public Resource refresh() {
        return resolveURI( uri );
    }

    @Override
    public Resource resolveURIFromString(String uri) {
        return resolveURIFromString( uri, this );
    }

    @Override
    public Resource resolveURIFromString(String uri, Resource context) {
        Resource result = null;
        CompoundURI curi;
        try {
            curi = new CompoundURI( uri );
            result = resolveURI( curi, context );
        }
        catch(URISyntaxException e) {
            Log.e( LogTag, String.format("Parsing '%s'", uri ), e );
        }
        return result;
    }

    @Override
    public Resource resolveURI(CompoundURI uri) {
        return resolveURI( uri, this );
    }

    @Override
    public Resource resolveURI(CompoundURI uri, Resource context) {
        return this.resolver.resolveURI( uri, context );
    }

    @Override
    public boolean dispatchURI(String uri) {
        return dispatchURI( uri, this );
    }
    
    // TODO
    @Override
    public boolean dispatchURI(String uri, Resource context) {
        return false;
    }
    
    public boolean isUpdateable() {
        return this.updateable;
    }
    
    public void setUpdateable(boolean updateable) {
        this.updateable = updateable;
    }

    @Override
    public int hashCode() {
        return this.uri != null ? this.uri.hashCode() : super.hashCode();
    }
    
    @Override
    public boolean equals(Object obj) {
        // Two resources are equal if they have the same URI.
        return (obj instanceof Resource) && this.uri != null && this.uri.equals( ((Resource)obj).uri );
    }

    @Override
    public String toString() {
        return asString();
    }

    static final Resource NullResource = new Resource();

    /**
     * Utility method for resolving resource values from resources in a map.
     * Will always return a non-null resource value. When the map doesn't contain the specified key
     * then returns a special 'null resource' instance, which will return null for all its asXXX()
     * value methods.
     * Useful e.g. when accessing values from an EP event's arguments.
     * @param map   A map of resources.
     * @param name  The name of a resource key in the map.
     * @return A resource instance.
     */
    public static Resource fromMap(Map<String,Resource> map, String name) {
        Resource r = map.get( name );
        return r == null ? NullResource : r;
    }

}
