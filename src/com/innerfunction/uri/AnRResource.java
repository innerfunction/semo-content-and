package com.innerfunction.uri;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.Log;

import com.innerfunction.util.FileIO;
import com.innerfunction.util.Paths;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Class representing a resource in the app's assets or res directories.
 */
public class AnRResource extends FileResource {

    private static final String LogTag = AnRResource.class.getSimpleName();
    /** The asset name. */
    private String name;

    public AnRResource(Context context, String name, CompoundURI uri, Resource parent) {
        super( context, name, uri, parent );
        this.name = name;
    }
    
    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public boolean exists() {
        return true; // This resource object won't be created unless the asset exists.
    }

    @Override
    public byte[] asData() {
        try {
            return FileIO.readData( openInputStream(), this.name );
        }
        catch(IOException e) {
            Log.e(LogTag, String.format("Reading data from %s", this.name), e );
        }
        return null;
    }

    /**
     * A resource in the app's res directory.
     * @author juliangoacher
     *
     */
    public static class AndroidResource extends AnRResource {
        
        /** Application resources. */
        private Resources r;
        /** The resource ID. */
        private int resourceID;

        public AndroidResource(Context context, int resourceID, CompoundURI uri, Resource parent) {
            super( context, Integer.toString( resourceID ), uri, parent );
            this.r = context.getResources();
            this.resourceID = resourceID;
        }
    
        @Override
        public InputStream openInputStream() throws IOException {
            return this.r.openRawResource( this.resourceID );
        }

        @Override
        public String asString() {
            return this.r.getString( this.resourceID );
        }

        @Override
        public URI asURL() {
            // NOTE resources can only be loaded by URL from the assets folder.
            return null;
        }

        /**
         * Fetch this resource's image representation.
         * Assumes that the resource's string representation is the name of an image to be found in the
         * app's resource bundle.
         */
        @Override
        public Drawable asImage() {
            return this.r.getDrawable( this.resourceID );
        }

    }

    /**
     * A resource under the app's assets dir.
     * @author juliangoacher
     *
     */
    public static class Asset extends AnRResource {
        
        /** Application asset manager. */
        private IFAssetManager assetManager;

        public Asset(Context context, String name, IFAssetManager assetManager, CompoundURI uri, Resource parent) {
            super( context, name, uri, parent );
            this.assetManager = assetManager;
        }

        @Override
        public InputStream openInputStream() throws IOException {
            return this.assetManager.openInputStream( getName() );
        }

        @Override
        public String asString() {
            String s = null;
            try {
                s = FileIO.readString( openInputStream(), getName(), "UTF-8");
            }
            catch(IOException e) {
                Log.e(LogTag, String.format("Reading string from %s", getName()), e );
            }
            return s;
        }

        /** Return the file URL. */
        @Override
        public URI asURL() {
            URI url = null;
            try {
                String path = Paths.join("android_asset", getName() );
                url = new URI( String.format("file:///%s", path ) );
            }
            catch(URISyntaxException e) {
                Log.e( LogTag, "Parsing URL", e );
            }
            return url;
        }

        /**
         * Fetch this resource's image representation.
         * Assumes that the resource's string representation is the name of an image to be found in the
         * app's resource bundle.
         */
        @Override
        public Drawable asImage() {
            Drawable image = null;
            try {
                image = Drawable.createFromStream( openInputStream(), getName() );
            }
            catch(FileNotFoundException fnfe) {
                Log.e(LogTag, String.format("Asset not found: %s", getName() ));
            }
            catch(IOException ioe) {
                Log.e(LogTag, String.format("Reading image from %s", getName() ), ioe );
            }
            return image;
        }

    }

}
