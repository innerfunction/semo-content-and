package com.innerfunction.uri;

import com.innerfunction.util.Paths;

import java.util.Map;
import android.content.Context;
import android.content.res.Resources;
import android.util.Log;

/**
 * URI scheme handler for resources loaded from the app's assets or res directories.
 * @author juliangoacher
 *
 */
public class AnRBasedSchemeHandler extends AbstractSchemeHandler {

    static final String LogTag = AnRBasedSchemeHandler.class.getSimpleName();
    private Context context;
    private Resources r;
    private String packageName;
    private IFAssetManager assetManager;
    private String basePath;
    
    public AnRBasedSchemeHandler(Context context, IFAssetManager assetManager) {
        this( context, null, assetManager );
    }
    
    public AnRBasedSchemeHandler(Context context, String basePath, IFAssetManager assetManager) {
        this.context = context;
        this.r = context.getResources();
        this.packageName = context.getPackageName();
        this.assetManager = assetManager;
        this.basePath = basePath;
    }

    @Override
    public CompoundURI resolveToAbsoluteURI(CompoundURI uri, CompoundURI context) {
        // If URI name doesn't begin with / then it is a relative URI.
        String name = uri.getName();
        if( name.charAt( 0 ) != '/' ) {
            uri = uri.copyOf();
            String contextPath = Paths.dirname( context.getName() );
            uri.setName( Paths.join( contextPath, name ) );
        }
        return uri;
    }

    @Override
    public Resource handle(CompoundURI uri, Map<String, Resource> params, Resource parent) {
        Resource result = null;
        // Leading slashes on an asset name will cause problems when resolving the asset file, so strip them from the name.
        String name = uri.getName();
        if( name.charAt( 0 ) == '/' ) {
            name = name.substring( 1 );
        }
        int resourceID = getResourceIDForName( name );
        if( resourceID != 0 ) {
            Log.d(LogTag,String.format("Accessing %s from resources using ID %d", name, resourceID ));
            result = new AnRResource.AndroidResource( this.context, resourceID, uri, parent );
        }
        else {
            String assetName = Paths.join( this.basePath, name );
            if( assetManager.assetExists( assetName ) ) {
                // Asset name found at specified location, so return an asset resource.
                result = new AnRResource.Asset( this.context, assetName, assetManager, uri, parent );
            }
        }
        return result;
    }

    /** Get an Android resource ID for a URI name. */
    public int getResourceIDForName(String name) {
        String resourceType = "string";
        // Derive the resource type from the asset name file extension.
        String ext = Paths.extname( name );
        if( ".png".equals( ext ) || ".jpg".equals( ext ) || ".jpeg".equals( ext ) ) {
            resourceType = "drawable";
        }
        // TODO: Should other resource types be supported?
        
        // Build a resource ID by -
        // * stripping any file extension from the asset name;
        // * converting / to __
        // * converting - to _
        // This will convert a name like ep/icons/icon-schedule.png to ep__icons__icon_schedule
        String resourceID = Paths.stripext( name ).replace("/","__").replace("-","_");
        return this.r.getIdentifier( resourceID, resourceType, this.packageName );
    }
}
