package com.innerfunction.semo.content;

import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import name.fraser.neil.plaintext.diff_match_patch;
import name.fraser.neil.plaintext.diff_match_patch.Patch;
import android.content.Context;
import android.util.Log;

import com.innerfunction.util.FileIO;
import com.innerfunction.util.Locals;

/**
 * A class for unpacking downloaded content.
 * @author juliangoacher
 *
 */
public class ContentUnpacker {

    static final String Tag = ContentUnpacker.class.getSimpleName();

    static final String ContentTextEncoding = "utf-8";

    /** The android context. */
    private Context context;
    /** The content manager. */
    private ContentManager manager;
    /** A message digest for calculating hashes of text content. */
    private MessageDigest md;

    public ContentUnpacker(Context context, ContentManager manager) {
        this.context = context;
        this.manager = manager;
        try {
            md = MessageDigest.getInstance("MD5");
        }
        catch(NoSuchAlgorithmException e) {
            // Really shouldn't happen.
        }
    }
    
    @SuppressWarnings("unchecked")
    public List<String> unpackContent(Subscription sub, File sourceZipFile, boolean resume) {
        String subName = sub.getName();
        Locals subLocals = sub.getLocals();
        File contentDir = sub.getContentDir();
        List<String> unpackedFiles = new ArrayList<String>();
        try {
            // Start by storing the full path of the source zip file.
            subLocals.setString("sourceZip", sourceZipFile.getAbsolutePath() );
            // Check for an unpack status left over from a previous interrupted process.
            String unpackStatus = subLocals.getString("unpackStatus");
            // In no unpack status found...
            if( unpackStatus == null ) {
                // If resuming then nothing more to do.
                if( resume ) {
                    return unpackedFiles;
                }
                // Set initial unpack state.
                unpackStatus = subLocals.setString("unpackStatus", "unzip");
            }
            
            if("unzip".equals( unpackStatus ) ) {
                // Unzip the content zip into the sub's content directory, overwriting
                // and possibly replacing any pre-existing files.
                String[] unzippedFiles = FileIO.unzip( sourceZipFile, contentDir );
                unpackedFiles.addAll( Arrays.asList( unzippedFiles ) );
                unpackStatus = subLocals.setString("unpackStatus", "patch");
            }
            
            // Read the content manifest.
            File semoDir = new File( contentDir, ".semo");
            File manifestFile = new File( semoDir, "manifest.json");
            Map<String,Object> manifest = (Map<String,Object>)FileIO.readJSON( manifestFile, ContentTextEncoding );
            // Check that the manifest subscription name is correct.
            if( !subName.equals( manifest.get("name") ) ) {
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
                                unpackedFiles.add( targetFile.getAbsolutePath() ); 
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
                            // Record the unpacked file.
                            unpackedFiles.add( targetFile.getAbsolutePath() );
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
                    // Record the patched file.
                    unpackedFiles.add( targetFile.getAbsolutePath() );
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
                    // TODO: Should deleted files be recorded as unpacked?
                }
                
                subLocals.setString("version", newVersion );
                unpackStatus = subLocals.setString("unpackStatus", "post-unpack");
            }
            
            if("post-unpack".equals( unpackStatus ) ) {
                // Notify all post-update listeners registered with the subs manager.
                List<SubscriptionUnpackListener> subsUnpackListeners = manager.getSubscriptionUnpackListeners();
                // Set a pointer on the listener being processed. If a previous post-unpack process
                // was interrupted then this will pickup from that point.
                int postUnpackIndex = subLocals.getInt("postUnpackIndex", 0 );
                int postUnpackCount = subsUnpackListeners.size();
                while( postUnpackIndex < postUnpackCount ) {
                    SubscriptionUnpackListener listener = subsUnpackListeners.get( postUnpackIndex );
                    listener.onSubscriptionUnpack( sub );
                    postUnpackCount = subLocals.setInt("postUnpackIndex", postUnpackIndex + 1 );
                }
            }
            
            // Remove process state.
            subLocals.remove("postUnpackIndex","unpackStatus","sourceZip");
            
            // Delete the version manifest.
            if( !versionManifestFile.delete() ) {
                Log.w( Tag, String.format("Failed to delete version manifest at %s", versionManifestFile ) );
            }
        }
        catch(Exception e) {
            unpackedFiles.clear();
            // TODO: Need to review the implications of doing the following ops, and their impact on whatever
            // process called this method.
            // Lock the subscription.
            manager.lockSubscription( subName, true );
            // Remove subscription content dir
            FileIO.removeDir( contentDir, context );
            // Unpack base content.
            manager.resetSubscriptionContent( sub );
            // Unlock subscription.
            manager.lockSubscription( subName, false );
            // Request full content update.
            sub.refresh( null );
        }
        return unpackedFiles;
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
