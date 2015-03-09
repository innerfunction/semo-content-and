package com.innerfunction.uri;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.innerfunction.util.Paths;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

public class IFAssetManager {

    static final String Tag = IFAssetManager.class.getSimpleName();
    
    private AssetManager assetManager;
    private Map<String,Set<String>> assetNamesByPath;

    public IFAssetManager(Context context) {
        this.assetManager = context.getAssets();
        this.assetNamesByPath = new HashMap<String,Set<String>>();
    }

    public InputStream openInputStream(String name) throws IOException {
        return this.assetManager.open( name );
    }

    public boolean assetExists(String assetName) {
        // Note: following necessary to detect whether the referenced asset exists. This
        // is so as to be consistent in behaviour with the file based URI schemes, which
        // evaluate null if the referenced file doesn't exist.
        String dirPath = Paths.dirname( assetName );
        Set<String> assetPaths = getAssetNamesUnderPath( dirPath );
        return assetPaths.contains( Paths.basename( assetName ) );
    }

    /**
     * Return a set of all asset names under the specified path.
     * If enabled with the cacheAssetNames flag, then lists of names under paths are cached to
     * aid performance.
     * @param path
     * @return
     */
    public Set<String> getAssetNamesUnderPath(String path) {
        Set<String> assetNames = null;
        assetNames = this.assetNamesByPath.get( path );
        if( assetNames == null ) {
            try {
                String[] assets = this.assetManager.list( path );
                assetNames = new HashSet<String>( Arrays.asList( assets ) );
            }
            catch(IOException e) {
                Log.e(Tag, "Listing assets", e );
                assetNames = new HashSet<String>();
            }
            this.assetNamesByPath.put( path, assetNames );
        }
        return assetNames;
    }

}
