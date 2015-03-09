package com.innerfunction.uri;

import android.content.Context;
import android.util.Log;

import com.innerfunction.util.FileIO;
//import com.innerfunction.util.NotificationCenter;
import java.util.HashMap;
import java.util.Map;
//import java.util.Observer;
import java.util.Set;
import java.net.URISyntaxException;

/**
 * A class for resolving internal URIs.
 */
public class StandardURIResolver implements URIResolver {

    private static final String LogTag = "URIResolver";

    /** A map of the registered URI scheme handlers, keyed by scheme name. */
    private Map<String,SchemeHandler> schemeHandlers = new HashMap<String,SchemeHandler>();
    /** The default parent resource. */
    private Resource parentResource;

    /** Create a resolver with the default scheme handlers. */
    public StandardURIResolver(Context context, Resource parent, IFAssetManager assetManager) {
        this.parentResource = parent;
        this.schemeHandlers.put("s", new StringSchemeHandler( context ) );
        this.schemeHandlers.put("app", new AnRBasedSchemeHandler( context, assetManager ) );
        this.schemeHandlers.put("cache", new FileBasedSchemeHandler( context, FileIO.getCacheDir( context )));
        this.schemeHandlers.put("local", new LocalSchemeHandler( context ));
    }

    /** Test if a URI scheme has a registered handler with this resolver. */
    public boolean hasHandlerForURIScheme(String scheme) {
        return this.schemeHandlers.containsKey( scheme );
    }

    /** Register a new scheme handler. */
    public void addHandler(String scheme, SchemeHandler handler) {
        this.schemeHandlers.put( scheme, handler );
    }

    /** Return the set of registered URI scheme names. */
    public Set<String> getURISchemeNames() {
        return this.schemeHandlers.keySet();
    }

    /** Return the URI handler for the specified scheme. */
    public SchemeHandler getHandlerForURIScheme(String scheme) {
        return this.schemeHandlers.get( scheme );
    }

    @Override
    public Resource resolveURIFromString(String uri) {
        return resolveURIFromString( uri, parentResource );
    }

    /**
     * Resolve a URI string in the specified context.
     * Any relative URIs within the string will be resolved to absolute URIs against the context.
     */
    @Override
    public Resource resolveURIFromString(String uri, Resource parent) {
        Resource resource = null;
        try {
            resource = this.resolveURI( new CompoundURI( uri ), parent );
        }
        catch(URISyntaxException e) {
            Log.e( LogTag, String.format("Parsing '%s'", uri ), e );
        }
        return resource;
    }

    @Override
    public Resource resolveURI(CompoundURI uri) {
        return resolveURI( uri, parentResource );
    }

    /**
     * Resolve a URI string in the specified parent resource context.
     * Any relative URIs within the URI will be resolved to absolute URIs against the parent.
     */
    @Override
    public Resource resolveURI(CompoundURI uri, Resource parent) {
        if( parent == null ) {
            throw new IllegalArgumentException("Parent resource can't be null");
        }
        Resource resource = null;
        SchemeHandler handler = this.schemeHandlers.get( uri.getScheme() );
        if( handler != null ) {
            // Resolve parameter values.
            Map<String,CompoundURI> params = uri.getParameters();
            Map<String,Resource> paramValues = new HashMap<String,Resource>( params.size() );
            for( String name : params.keySet() ) {
                Resource value = this.resolveURI( params.get( name ), parent );
                if( value != null ) {
                    paramValues.put( name, value );
                }
            }
            // Ensure current URI is an absolute URI.
            CompoundURI referenceURI = parent.getSchemeContext().get( uri.getScheme() );
            if( referenceURI != null ) {
                uri = handler.resolveToAbsoluteURI( uri, referenceURI );
            }
            // Handle the URI.
            resource = handler.handle( uri, paramValues, parent );
        }
        else {
            Log.e( LogTag, String.format("Handler not found for scheme '%s'", uri.getScheme() ) );
        }
        return resource;
    }

    // TODO
    @Override
    public boolean dispatchURI(String uri, Resource parent) {
        return false;
    }

    @Override
    public boolean dispatchURI(String uri) {
        return dispatchURI( uri, parentResource );
    }

    /**
     * Add a observer of the specified resource.
     * The observer will be notified whenever the specified resource is updated.
     */
    /*
    public Observer observeResource(Resource resource, ResourceObserver observer) {
        Observer result = null;
        CompoundURI uri = resource.getURI();
        SchemeHandler schemeHandler = this.schemeHandlers.get( uri.getScheme() );
        if( schemeHandler != null ) {
            result = schemeHandler.observeResource( resource, observer );
        }
        return result;
    }

    public void removeResourceObserver(Observer observer) {
        if( observer != null ) {
            NotificationCenter.getInstance().removeObserver( observer );
        }
    }
    */
}
