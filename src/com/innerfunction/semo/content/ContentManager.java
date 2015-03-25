package com.innerfunction.semo.content;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import com.innerfunction.util.FileIO;
import com.innerfunction.util.Locals;

/**
 * Class responsible for managing one or more content subscriptions.
 * TODO: Subs should notify list of updated file paths.
 * @author juliangoacher
 */
@SuppressLint("DefaultLocale")
public class ContentManager {
    
    static final String Tag = ContentManager.class.getSimpleName();
    
    /**
     * The directory containing subscription content.
     */
    private File contentDir;
    /**
     * The directory containing downloaded content files.
     */
    private File downloadDir;
    /**
     * Local content settings.
     */
    private Locals localSettings;
    /**
     * The server URL content updates are downloaded from.
     */
    private String subscriptionURL;
    /**
     * Array of processes that operate on unpacked content.
     */
    private List<ContentUnpackListener> contentUnpackListeners;
    /**
     * A map of content subscriptions, keyed by subscription name.
     */
    private Map<String,Subscription> subscriptions = new HashMap<String,Subscription>();
    
    public ContentManager(Context androidContext) {
        // Setup content directories.
        File cacheDir = new File( FileIO.getCacheDir( androidContext ), "semo");
        contentDir = new File( cacheDir, "content");
        if( !(contentDir.exists() || contentDir.mkdirs()) ) {
            Log.e( Tag, String.format("Unable to create content directory: %s", contentDir.getAbsolutePath() ) );
        }
        downloadDir = new File( cacheDir, "download");
        if( !(downloadDir.exists() || downloadDir.mkdirs()) ) {
            Log.e( Tag, String.format("Unable to create download directory: %s", downloadDir.getAbsolutePath() ) );
        }
        localSettings = new Locals("semo.subs");
    }
    
    public void setSubscriptionURL(String url) {
        subscriptionURL = url;
    }
    
    public void setSubscriptions(Map<String,Subscription> subs) {
        for( String name : subs.keySet() ) {
            Subscription sub = subs.get( name );
            sub.setup( this, name );
        }
        subscriptions.putAll( subs );
    }
    
    public void setContentUnpackListeners(List<ContentUnpackListener> listeners) {
        contentUnpackListeners = listeners;
    }
    
    public List<ContentUnpackListener> getContentUnpackListeners() {
        return contentUnpackListeners;
    }

    /**
     * Initialize content by initializing all subscriptions.
     * @param listener The content listener is called after all subscriptions have initialized.
     */
    public void initialize(final ContentListener listener) throws Exception {
        if( subscriptionURL == null ) {
            throw new Exception("No subscription URL defined in configuration");
        }
        if( subscriptions != null ) {
            // Iterate over all subscription names.
            final Iterator<String> names = subscriptions.keySet().iterator();
            // Loop over the names and initialize each subscription.
            ContentListenerIteratorLoop.loop( names, new ContentListenerIteratorLoop.IterationOp<String>() {
                @Override
                public void iteration(String name, ContentListener listener) {
                    Subscription subs = subscriptions.get( name );
                    subs.initialize( listener );
                }
            }, listener);
        }
    }
    
    public Locals getLocalSettings() {
        return localSettings;
    }
    
    public File getContentDir() {
        return contentDir;
    }
    
    public File getDownloadDir() {
        return downloadDir;
    }
    
    public String getSubscriptionURL() {
        return subscriptionURL;
    }
    
    /**
     * Refresh a named subscription.
     * @param name The subscription name.
     */
    public void refreshSubscription(String name) {
        refreshSubscription( name, null );
    }
    
    /**
     * Refresh a named subscription.
     * @param name      The subscription name.
     * @param listener  A refresh listener; its onContentRefresh method is called once
     *                  the refresh has fully completed.
     */
    public void refreshSubscription(String name, ContentListener listener) {
        Subscription subs = subscriptions.get( name );
        if( subs != null ) {
            subs.refresh( listener );
        }
    }
    
    /**
     * Refresh all subscriptions.
     */
    public void refreshAllSubscriptions() {
        refreshAllSubscriptions( null );
    }
    
    /**
     * Refresh all subscriptions.
     * Subscriptions are refreshed in sequence, i.e. the second refresh doesn't start until the
     * first has fully downloaded and unpacked.  
     * @param listener A refresh listener; its onContentRefresh method is called once all
     *                 subscriptions have fully refreshed.
     */
    public void refreshAllSubscriptions(final ContentListener listener) {
        // Iterate over all subscription names.
        final Iterator<String> names = subscriptions.keySet().iterator();
        // Loop over the names and refresh each subscription.
        ContentListenerIteratorLoop.loop( names, new ContentListenerIteratorLoop.IterationOp<String>() {
            @Override
            public void iteration(String name, ContentListener listener) {
                refreshSubscription( name, listener );
            }
        }, listener);
    }

    public void resetSubscriptionContent(Subscription subs) {
        
    }
    
    /**
     * Lock or unlock a named subscription.
     * @param name
     * @param locked
     */
    protected void lockSubscription(String name, boolean locked) {
        
    }
}
