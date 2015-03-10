package com.innerfunction.semo.content;

import java.io.File;
import java.net.MalformedURLException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import name.fraser.neil.plaintext.diff_match_patch;
import name.fraser.neil.plaintext.diff_match_patch.Patch;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import com.innerfunction.util.FileIO;
import com.innerfunction.util.HTTPUtils;
import com.innerfunction.util.StringTemplate;

/**
 * A content subscription.
 * Downloads content updates and unpacks them to the device's local file system.
 * @author juliangoacher
 */
public class Subscription {

    static final String Tag = Subscription.class.getSimpleName();

    static final String ContentTextEncoding = "utf-8";
    
    /** The subscription name. */
    private String name;
    /** The subscription manager. */
    private SubscriptionManager manager;
    /** An Android context object. */
    private Context context;
    /** A message digest for calculating hashes of text content. */
    private MessageDigest md;
    /** The subscription's content directory. */
    private File contentDir;
    /** Local storage vars specific to this subscription. */
    private Locals subLocals;
    /** General local storage vars, common to all subscriptions. */
    private Locals generalLocals;
    /** The feed's content URL. */
    private String contentURL;
    /** The subscription's download file. */
    private File downloadFile;
    /** File download callback handler. */
    private HTTPUtils.GetFileCallback contentDownloadHandler = new HTTPUtils.GetFileCallback() {
        @Override
        public void receivedFile(File file) {
            Subscription.this.unpackContent( file, false );
            Subscription.this.finishDownload();
            if( !file.delete() ) {
                Log.w( Tag, String.format("Failed to delete download file at %s", file ) );
            }
        }
    };
    
    public Subscription(String name, SubscriptionManager manager, Context context) {
        this.name = name;
        this.manager = manager;
        this.context = context;
        try {
            md = MessageDigest.getInstance("MD5");
        }
        catch(NoSuchAlgorithmException e) {
            // Really shouldn't happen.
        }
        contentDir = new File( manager.getContentDir(), name );
        subLocals = new Locals( String.format("semo.subs.%s", name ) );
        generalLocals = manager.getSettings();
    }
    
    /**
     * Get the subscription's content directory.
     */
    public File getContentDir() {
        return contentDir;
    }
    
    /**
     * Get the current fully downloaded and unpacked content version.
     */
    public String getVersion() {
        return subLocals.getString("version");
    }
    
    /**
     * Refresh the subscription's content.
     * Checks the general download policy, and attempts to download an update if the policy
     * allows it.
     */
    public void refresh() {
        String downloadPolicy = generalLocals.getString("downloadPolicy", null );
        Log.i( Tag, String.format("downloadPolicy=%s", downloadPolicy) );
        if( "never".equals( downloadPolicy ) ) {
            // Downloads disabled
            return;
        }
        // Check connectivity.
        ConnectivityManager cm = (ConnectivityManager)context.getSystemService( Context.CONNECTIVITY_SERVICE );
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean connected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        if( !connected ) {
            // No network, so can't download.
            Log.d( Tag, "Network not reachable");
            return;
        }
        // Check network type.
        // TODO: Download policy values may need to be reviewed here - Android provides greater
        // discrimination between network types.
        switch( activeNetwork.getType() ) {
        case ConnectivityManager.TYPE_WIFI:
        case ConnectivityManager.TYPE_WIMAX:
        case ConnectivityManager.TYPE_ETHERNET:
            // Always download over wifi network.
            Log.d( Tag, "WIFI or equivalent network available");
            startDownload();
            break;
        default:
            Log.d( Tag, "Non-WIFI network available");
            if( !"wifi-only".equals( downloadPolicy ) ) {
                // Only download if policy allows it.
                startDownload();
            }
        }
    }

    /**
     * Start the download process.
     * If a previous, interrupted, download is detected then resume that; otherwise
     * check for updated content.
     */
    protected void startDownload() {
        contentURL = subLocals.getString("contentURL", null );
        String downloadFileName = subLocals.getString("filename", null );
        if( contentURL != null && downloadFileName != null ) {
            downloadFile = new File( downloadFileName );
            resumeDownload();
        }
        else {
            checkForUpdates();
        }
    }

    /**
     * Resume an interrupted download.
     */
    protected void resumeDownload() {
        if( downloadFile.exists() ) {
            long offset = downloadFile.length();
            try {
                HTTPUtils.getFile( contentURL, offset, downloadFile, contentDownloadHandler );
            }
            catch(MalformedURLException e) {
                Log.w( Tag, String.format("Bad content URL: %s", contentURL ));
            }
        }
        else {
            // Download file not found; clean up build and start again.
            finishDownload();
            checkForUpdates();
        }
    }

    /**
     * Check for updated content.
     */
    protected void checkForUpdates() {
        String subsURL = manager.getSubscriptionURL();
        if( subsURL == null ) {
            Subscription.this.finishDownload();
            return;
        }
        // Feed ID can be specified as a template accepting feed ID and since build build as values.
        Map<String,Object> context = new HashMap<String,Object>();
        context.put("subs", name );
        context.put("since", subLocals.getString("version") );
        String url = StringTemplate.render( subsURL, context );
        // Send the HTTP request.
        try {
            HTTPUtils.getJSON( url, new HTTPUtils.GetJSONCallback() {
                @Override
                public void receivedJSON(Map<String, Object> json) {
                    String status = "unknown";
                    if( json != null ) {
                        json.get("status").toString();
                    }
                    if("error".equals( status ) ) {
                        // Feed error.
                        Subscription.this.finishDownload();
                    }
                    else if("no-update".equals( status) || "no-content-available".equals( status )) {
                        // No update available.
                        Subscription.this.finishDownload();
                    }
                    else if("update-since".equals( status ) || "current-content".equals( status )) {
                        // Read content URL and start update download.
                        String url = json.get("url").toString();
                        subLocals.setString("status", status );
                        Subscription.this.downloadContent( url );
                    }
                    else {
                        Subscription.this.finishDownload();
                    }
                }
            });
        }
        catch(MalformedURLException e) {
            Log.w( Tag, String.format("Bad build query URL: %s", url ));
            Subscription.this.finishDownload();
        }
    }

    /**
     * Download a content update.
     * @param contentURL    The URL of a zip file containing the update.
     */
    protected void downloadContent(String contentURL) {
        subLocals.setString("contentURL", contentURL );
        // Delete any previous download file.
        if( downloadFile != null && downloadFile.exists() ) {
            downloadFile.delete();
        }
        // Setup the download file path. This is placed in the app's tmp directory.
        String filename = String.format("_semo_content_%s.zip", name );
        downloadFile = new File( manager.getDownloadDir(), filename );
        subLocals.setString("downloadFile", downloadFile.getAbsolutePath() );
        // Send download request.
        try {
            HTTPUtils.getFile( contentURL, 0, downloadFile, contentDownloadHandler );
        }
        catch(MalformedURLException e) {
            Log.w( Tag, String.format("Bad content URL: %s", contentURL ));
        }
    }

    /**
     * Cleanup after a download.
     */
    protected void finishDownload() {
        if( downloadFile != null ) {
            downloadFile.delete();
            downloadFile = null;
        }
        subLocals.remove("url","filename","status");
    }

    /**
     * Unpack subscription content.
     * Unpacks a content update from a content zip file.
     * @param sourceZipFile The zip file to unpack.
     * @param resume        Indicate whether to attempt to resume a possibly interrupted
     *                      previous unpack process.
     */
    @SuppressWarnings("unchecked")
    public void unpackContent(File sourceZipFile, boolean resume) {
        try {
            // Check for an unpack status left over from a previous interrupted process.
            String unpackStatus = subLocals.getString("unpackStatus");
            // In no unpack status found...
            if( unpackStatus == null ) {
                // If resuming then nothing more to do.
                if( resume ) {
                    return;
                }
                // Set initial unpack state.
                unpackStatus = subLocals.setString("unpackStatus", "unzip");
            }
            
            if("unzip".equals( unpackStatus ) ) {
                // Unzip the content zip into the sub's content directory, overwriting
                // and possibly replacing any pre-existing files.
                FileIO.unzip( sourceZipFile, contentDir );
                unpackStatus = subLocals.setString("unpackStatus", "patch");
            }
            
            // Read the content manifest.
            File semoDir = new File( contentDir, ".semo");
            File manifestFile = new File( semoDir, "manifest.json");
            Map<String,Object> manifest = (Map<String,Object>)FileIO.readJSON( manifestFile, ContentTextEncoding );
            // Check that the manifest subscription name is correct.
            if( !name.equals( manifest.get("name") ) ) {
                throw new Exception( String.format("Name in manifest doesn't match subscription: %s", manifest.get("name") ) );
            }
            // Read the version number for the new content.
            String newVersion = (String)manifest.get("version");
            if( newVersion == null ) {
                throw new Exception("Content version not found in manifest");
            }
            // Read this version's content manifest.
            File versionManifestFile = new File( semoDir, String.format("%s-manifest.json", newVersion ) );
            Map<String,Object> versionManifest = (Map<String,Object>)FileIO.readJSON( versionManifestFile, ContentTextEncoding );
            // TODO: Support declaring content text encoding in manifest
            
            if("patch".equals( unpackStatus ) ) {
                // Apply patched updates to previous content.
                // Create a reference to a temporary file for holding patch results.
                File tempPatchFile = new File( semoDir, "patch.temp");
                // Read the list of file patches from the version manifest.
                List<Object> patches = (List<Object>)versionManifest.get("patches");
                // Set a pointer on the current patch. If a previous patch process was interrupted then this
                // should resume from that point; else position before the first patch.
                int patchIndex = subLocals.getInt("patchIndex", -1 );
                // A map containing info about the current file patch.
                Map<String,Object> patch;
                // The target file being patched.
                File targetFile;
                // The target file's contents.
                String targetFileContents;
                // The object for applying patches to file contents.
                diff_match_patch patcher = new diff_match_patch();
                // The total number of patches to apply.
                int patchCount = patches.size();
                // If patch pointer is referencing a patch already then a previous patch process must have
                // been interrupted; so complete that patch before continuing.
                if( patchIndex > -1 && patchIndex < patchCount ) {
                    // Get the current patch.
                    patch = (Map<String,Object>)patches.get( patchIndex );
                    // Reference the patch target file.
                    targetFile = new File( contentDir, (String)patch.get("file") );
                    // If the target file doesn't exist then it must have been deleted before the previous
                    // patch process was interrupted...
                    if( !targetFile.exists() ) {
                        // If the patch temporary file exists then the target file was deleted and the process
                        // stopped before the the temp file could be moved to replace the target file.
                        if( tempPatchFile.exists() ) {
                            // Perform a hash of the temporary patch file's contents.
                            String patchTempFileContents = FileIO.readString( tempPatchFile, ContentTextEncoding );
                            CharSequence hash = md5Hash( patchTempFileContents );
                            // If the hash matches the expected after state then go ahead and complete the patch op.
                            if( hash.equals( patch.get("after") ) ) {
                                // Move the temp file - the patch is then completed.
                                if( !tempPatchFile.renameTo( targetFile ) ) {
                                    throw new Exception( String.format("Failed to move patch.temp when attempting to recover patch to %s",
                                            targetFile ) );
                                }
                            }
                            else {
                                // Unexpected patch state; can't recover so fatal error.
                                throw new Exception( String.format("Bad after state when attempring to recover patch to %s", targetFile ) );
                            }
                        }
                        else {
                            // If no patch target and no patch temp file then something odd has happened, and we can't
                            // recover so this is a fatal error.
                            throw new Exception( String.format("Broken patch on %s", targetFile ) );
                        }
                    }
                    else {
                        // Read the patch target's contents.
                        targetFileContents = FileIO.readString( targetFile, ContentTextEncoding );
                        // Check the MD5 hash of the file contents.
                        CharSequence hash = md5Hash( targetFileContents );
                        // Test whether the hash matches the pre-patch state.
                        if( hash.equals( patch.get("before") ) ) {
                            // Patch was interrupted before it could be applied, so apply patches to the target.
                            LinkedList<Patch> filePatches = patcher.patch_fromText( (String)patch.get("patches") );
                            targetFileContents = (String)patcher.patch_apply( filePatches, targetFileContents )[0];
                            // Validate post-patch state using MD5 hash.
                            hash = md5Hash( targetFileContents );
                            if( !hash.equals( patch.get("after") ) ) {
                                // Post-patch state is invalid, so fatal error.
                                throw new Exception( String.format("Inconsistent post-patch state for %s", targetFile ) );
                            }
                            // Write patched content to temporary file.
                            if( !FileIO.writeString( tempPatchFile, targetFileContents ) ) {
                                throw new Exception( String.format("Failed to write patch.temp when patching %s", targetFile ) );
                            }
                            // Delete the patch target.
                            if( !targetFile.delete() ) {
                                throw new Exception( String.format("Failed to remove %s when patching", targetFile ) );
                            }
                            // Move the temporary file to the patch target.
                            if( !tempPatchFile.renameTo( targetFile ) ) {
                                throw new Exception( String.format("Failed to move patch.temp to %s when patching", targetFile ) );
                            }
                        }
                        else if( !hash.equals( patch.get("after") ) ) {
                            // Target file state doesn't match either the pre- or post-patch state, so something odd
                            // has happened; can't recover from this, so fatal error.
                            throw new Exception( String.format("Inconsistent post-patch state for %s", targetFile ) );
                        }
                        // Else file was fully patched before interruption, nothing more to do.
                    }
                    // Update pointer to next patch.
                    patchIndex = subLocals.setInt("patchIndex", patchIndex + 1 );
                }
                else {
                    // Move patch pointer to first patch on list.
                    patchIndex = subLocals.setInt("patchIndex", 0 );
                }
                
                // Iterate over all un-applied patches.
                while( patchIndex < patchCount ) {
                    // Read the patch object.
                    patch = (Map<String,Object>)patches.get( patchIndex );
                    // Check that the patch target exists.
                    targetFile = new File( contentDir, (String)patch.get("file") );
                    if( !targetFile.exists() ) {
                        throw new Exception( String.format("Patch target not found: %s", targetFile ) );
                    }
                    // Read the patch target's contents.
                    targetFileContents = FileIO.readString( targetFile, ContentTextEncoding );
                    // Validate using MD5 hash that patch content is correct.
                    CharSequence hash = md5Hash( targetFileContents );
                    if( !hash.equals( patch.get("before") ) ) {
                        throw new Exception( String.format("Inconsistent pre-patch state for %s", targetFile ) );
                    }
                    // Apply patches to the patch target.
                    LinkedList<Patch> filePatches = patcher.patch_fromText( (String)patch.get("patches") );
                    targetFileContents = (String)patcher.patch_apply( filePatches, targetFileContents )[0];
                    // Validate post-patch state using MD5 hash.
                    hash = md5Hash( targetFileContents );
                    if( !hash.equals( patch.get("after") ) ) {
                        throw new Exception( String.format("Inconsistent post-patch state for %s", targetFile ) );
                    }
                    // Write patched content to temporary file.
                    if( !FileIO.writeString( tempPatchFile, targetFileContents ) ) {
                        throw new Exception( String.format("Failed to write patch.temp when patching %s", targetFile ) );
                    }
                    // Delete the patch target.
                    if( !targetFile.delete() ) {
                        throw new Exception( String.format("Failed to remove %s when patching", targetFile ) );
                    }
                    // Move the temporary file to the patch target.
                    if( !tempPatchFile.renameTo( targetFile ) ) {
                        throw new Exception( String.format("Failed to move patch.temp to %s when patching", targetFile ) );
                    }
                    // Iterate to next patch.
                    patchIndex = subLocals.setInt("patchIndex", patchIndex + 1 );
                }
                
                unpackStatus = subLocals.setString("unpackStatus", "clean");
            }
            
            if("clean".equals( unpackStatus ) ) {
                
                subLocals.remove("patchIndex");
                
                // Iterate over list of file deletions and delete all files.
                List<String> deletes = (List<String>)versionManifest.get("deletes");
                for( String path : deletes ) {
                    File deleteFile = new File( contentDir, path );
                    if( !deleteFile.delete() ) {
                        throw new Exception( String.format("Unable to delete file %s", deleteFile ) );
                    }
                }
                
                subLocals.setString("version", newVersion );
                unpackStatus = subLocals.setString("unpackStatus", "post-unpack");
            }
            
            if("post-unpack".equals( unpackStatus ) ) {
                // Notify all post-update listeners registered with the subs manager.
                List<PostUnpackListener> postUnpackListeners = manager.getPostUnpackListeners();
                // Set a pointer on the listener being processed. If a previous post-unpack process
                // was interrupted then this will pickup from that point.
                int postUnpackIndex = subLocals.getInt("postUnpackIndex", 0 );
                int postUnpackCount = postUnpackListeners.size();
                while( postUnpackIndex < postUnpackCount ) {
                    PostUnpackListener listener = postUnpackListeners.get( postUnpackIndex );
                    listener.onPostUnpack( this );
                    postUnpackCount = subLocals.setInt("postUnpackIndex", postUnpackIndex + 1 );
                }
            }
            
            // Remove process state.
            subLocals.remove("postUnpackIndex");
            subLocals.remove("unpackStatus");
            // Delete the version manifest.
            if( !versionManifestFile.delete() ) {
                Log.w( Tag, String.format("Failed to delete version manifest at %s", versionManifestFile ) );
            }
        }
        catch(Exception e) {
            // Lock the subscription.
            manager.lockSubscription( name, true );
            // Remove subscription content dir
            FileIO.removeDir( contentDir, context );
            // Unpack base content.
            manager.unpackBaseContentForSubscription( this );
            // Unlock subscription.
            manager.lockSubscription( name, false );
            // Request full content update.
            refresh();
        }
    }
    
    /**
     * Return the MD5 hash of a content string as a hex encoded string.
     * @param contents
     * @return
     */
    private CharSequence md5Hash(String contents) {
        md.reset();
        md.update( contents.getBytes() );
        byte[] digest = md.digest();
        StringBuilder hex = new StringBuilder();
        for( int i = 0; i < digest.length; i++ ) {
            hex.append( Integer.toString( ( digest[i] & 0xff ) + 0x100, 16 ).substring( 1 ) );
        }
        return hex;
    }
}
