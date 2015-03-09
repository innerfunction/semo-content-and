package com.innerfunction.semo.content;

import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import name.fraser.neil.plaintext.diff_match_patch;
import name.fraser.neil.plaintext.diff_match_patch.Patch;
import android.content.Context;

import com.innerfunction.util.FileIO;

/**
 * A content subscription.
 * Downloads content updates and unpacks them to the device's local file system.
 * @author juliangoacher
 */
public class Subscription {

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
    }
    
    public File getContentDir() {
        return contentDir;
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
            String unpackStatus = getLocalString("unpackStatus");
            // In no unpack status found...
            if( unpackStatus == null ) {
                // If resuming then nothing more to do.
                if( resume ) {
                    return;
                }
                // Set initial unpack state.
                unpackStatus = setLocalString("unpackStatus", "unzip");
            }
            
            if("unzip".equals( unpackStatus ) ) {
                // Unzip the content zip into the sub's content directory, overwriting
                // and possibly replacing any pre-existing files.
                FileIO.unzip( sourceZipFile, contentDir );
                unpackStatus = setLocalString("unpackStatus", "patch");
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
                File patchTempFile = new File( semoDir, "patch.temp");
                // Read the list of file patches from the version manifest.
                List<Object> patches = (List<Object>)versionManifest.get("patches");
                // Set a pointer on the current patch. If a previous patch process was interrupted then this
                // should resume from that point; else position before the first patch.
                int patchIndex = getLocalInt("patchIndex", -1 );
                // A map containing info about the current file patch.
                Map<String,Object> patch;
                // The target file being patched.
                File patchFile;
                // The target file's contents.
                String patchFileContents;
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
                    patchFile = new File( contentDir, (String)patch.get("file") );
                    // If the target file doesn't exist then it must have been deleted before the previous
                    // patch process was interrupted...
                    if( !patchFile.exists() ) {
                        // If the patch temporary file exists then the target file was deleted and the process
                        // stopped before the the temp file could be moved to replace the target file.
                        if( patchTempFile.exists() ) {
                            // Move the temp file - the patch is then completed.
                            if( !patchTempFile.renameTo( patchFile ) ) {
                                throw new Exception( String.format("Failed to move patch.temp to %s when patching", patchFile ) );
                            }
                        }
                        else {
                            // If no patch target and no patch temp file then something odd has happened, and we can't
                            // recover so this is a fatal error.
                            throw new Exception( String.format("Broken patch on %s", patchFile ) );
                        }
                    }
                    else {
                        // Read the patch target's contents.
                        patchFileContents = FileIO.readString( patchFile, ContentTextEncoding );
                        // Check the MD5 hash of the file contents.
                        CharSequence hash = md5Hash( patchFileContents );
                        // Test whether the hash matches the pre-patch state.
                        if( hash.equals( patch.get("beforeHash") ) ) {
                            // Patch was interrupted before it could be applied, so apply patches to the target.
                            LinkedList<Patch> filePatches = patcher.patch_fromText( (String)patch.get("patches") );
                            patchFileContents = (String)patcher.patch_apply( filePatches, patchFileContents )[0];
                            // Validate post-patch state using MD5 hash.
                            hash = md5Hash( patchFileContents );
                            if( !hash.equals( patch.get("afterHash") ) ) {
                                // Post-patch state is invalid, so fatal error.
                                throw new Exception( String.format("Inconsistent post-patch state for %s", patchFile ) );
                            }
                            // Write patched content to temporary file.
                            if( !FileIO.writeString( patchTempFile, patchFileContents ) ) {
                                throw new Exception( String.format("Failed to write patch.temp when patching %s", patchFile ) );
                            }
                            // Delete the patch target.
                            if( !patchFile.delete() ) {
                                throw new Exception( String.format("Failed to remove %s when patching", patchFile ) );
                            }
                            // Move the temporary file to the patch target.
                            if( !patchTempFile.renameTo( patchFile ) ) {
                                throw new Exception( String.format("Failed to move patch.temp to %s when patching", patchFile ) );
                            }
                        }
                        else if( !hash.equals( patch.get("afterHash") ) ) {
                            // Target file state doesn't match either the pre- or post-patch state, so something odd
                            // has happened; can't recover from this, so fatal error.
                            throw new Exception( String.format("Inconsistent post-patch state for %s", patchFile ) );
                        }
                        // Else file was fully patched before interruption, nothing more to do.
                    }
                    // Update pointer to next patch.
                    patchIndex = setLocalInt("patchIndex", patchIndex + 1 );
                }
                else {
                    // Move patch pointer to first patch on list.
                    patchIndex = setLocalInt("patchIndex", 0 );
                }
                
                // Iterate over all un-applied patches.
                while( patchIndex < patchCount ) {
                    // Read the patch object.
                    patch = (Map<String,Object>)patches.get( patchIndex );
                    // Check that the patch target exists.
                    patchFile = new File( contentDir, (String)patch.get("file") );
                    if( !patchFile.exists() ) {
                        throw new Exception( String.format("Patch target not found: %s", patchFile ) );
                    }
                    // Read the patch target's contents.
                    patchFileContents = FileIO.readString( patchFile, ContentTextEncoding );
                    // Validate using MD5 hash that patch content is correct.
                    CharSequence hash = md5Hash( patchFileContents );
                    if( !hash.equals( patch.get("beforeHash") ) ) {
                        throw new Exception( String.format("Inconsistent pre-patch state for %s", patchFile ) );
                    }
                    // Apply patches to the patch target.
                    LinkedList<Patch> filePatches = patcher.patch_fromText( (String)patch.get("patches") );
                    patchFileContents = (String)patcher.patch_apply( filePatches, patchFileContents )[0];
                    // Validate post-patch state using MD5 hash.
                    hash = md5Hash( patchFileContents );
                    if( !hash.equals( patch.get("afterHash") ) ) {
                        throw new Exception( String.format("Inconsistent post-patch state for %s", patchFile ) );
                    }
                    // Write patched content to temporary file.
                    if( !FileIO.writeString( patchTempFile, patchFileContents ) ) {
                        throw new Exception( String.format("Failed to write patch.temp when patching %s", patchFile ) );
                    }
                    // Delete the patch target.
                    if( !patchFile.delete() ) {
                        throw new Exception( String.format("Failed to remove %s when patching", patchFile ) );
                    }
                    // Move the temporary file to the patch target.
                    if( !patchTempFile.renameTo( patchFile ) ) {
                        throw new Exception( String.format("Failed to move patch.temp to %s when patching", patchFile ) );
                    }
                    // Iterate to next patch.
                    patchIndex = setLocalInt("patchIndex", patchIndex + 1 );
                }
                
                unpackStatus = setLocalString("unpackStatus", "clean");
            }
            
            if("clean".equals( unpackStatus ) ) {
                
                removeLocal("patchIndex");
                
                // Iterate over list of file deletions and delete all files.
                List<String> deletes = (List<String>)versionManifest.get("deletes");
                for( String path : deletes ) {
                    File deleteFile = new File( contentDir, path );
                    if( !deleteFile.delete() ) {
                        throw new Exception( String.format("Unable to delete file %s", deleteFile ) );
                    }
                }
                
                // TODO: Are there cases where the zip file shouldn't be deleted? Or leave to subs manager?
                if( !sourceZipFile.delete() ) {
                    // TODO: Is this a fatal error?
                }
                
                setLocalString("version", newVersion );
                unpackStatus = setLocalString("unpackStatus", "post-unpack");
            }
            
            if("post-unpack".equals( unpackStatus ) ) {
                // Notify all post-update listeners registered with the subs manager.
                List<PostUnpackListener> postUnpackListeners = manager.getPostUnpackListeners();
                // Set a pointer on the listener being processed. If a previous post-unpack process
                // was interrupted then this will pickup from that point.
                int postUnpackIndex = getLocalInt("postUnpackIndex", 0 );
                int postUnpackCount = postUnpackListeners.size();
                while( postUnpackIndex < postUnpackCount ) {
                    PostUnpackListener listener = postUnpackListeners.get( postUnpackIndex );
                    listener.onPostUnpack( this );
                    postUnpackCount = setLocalInt("postUnpackIndex", postUnpackIndex + 1 );
                }
            }
            
            // Remove process state.
            removeLocal("postUnpackIndex");
            removeLocal("unpackStatus");
            // Delete the version manifest.
            if( !versionManifestFile.delete() ) {
                // TODO: Log warning.
            }
        }
        catch(Exception e) {
            // TODO: Lock subscription
            manager.lockSubscription( name, true );
            // TODO: Remove subscription content dir
            FileIO.removeDir( contentDir, context );
            // TODO: Unpack base content
            manager.unpackBaseContentForSubscription( this );
            // TODO: Unlock subscription
            manager.lockSubscription( name, false );
            // TODO: Request full content update
            manager.refreshSubscription( this );
        }
    }
    
    /**
     * Read a string from local storage.
     * @param localName
     * @return
     */
    private String getLocalString(String localName) {
        String key = localKey( localName );
        return null;
    }
    
    /**
     * Store a string value in local storage.
     * @param localName
     * @param value
     * @return
     */
    private String setLocalString(String localName, String value) {
        String key = localKey( localName );
        return value;
    }
    
    /**
     * Read an int value from local storage.
     * @param localName
     * @param defaultValue
     * @return
     */
    private int getLocalInt(String localName, int defaultValue) {
        String key = localKey( localName );
        return defaultValue;
    }
    
    /**
     * Store an int value in local storage.
     * @param localName
     * @param value
     * @return
     */
    private int setLocalInt(String localName, int value) {
        String key = localKey( localName );
        return value;
    }
    
    /**
     * Remove a value from local storage.
     * @param localName
     */
    private void removeLocal(String localName) {
        String key = localKey( localName );
        
    }
    
    /**
     * Get the full local storage key for a local var name.
     * @param localName
     * @return
     */
    private String localKey(String localName) {
        return String.format("semo.sub.%s.%s", name, localName );
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
        for (int i = 0; i < digest.length; i++) {
            hex.append( Integer.toString( ( digest[i] & 0xff ) + 0x100, 16 ).substring( 1 ) );
        }
        return hex;
    }
}
