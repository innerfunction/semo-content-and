package com.innerfunction.semo.content;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import android.annotation.SuppressLint;
import android.content.Context;

import com.innerfunction.semo.core.ComponentFactory;
import com.innerfunction.semo.core.Configuration;
import com.innerfunction.util.FileIO;
import com.innerfunction.util.Locals;

@SuppressLint("DefaultLocale")
public class SubscriptionManager {

    // Configure subs + manager
    // Subs initialization options (unpack from zip; mirror app fs; download content)
    // Subs refresh options (push/periodic/none)
    // Subs refresh options (all subs sequential/parallel)
    // Subs manual refresh
    
    static enum InitMethod { Unpack, MirrorFS, Download };
    static enum RefreshMethod { Push, Periodic, Manual, None };
    
    private File contentDir;
    private File downloadDir;
    private Locals localSettings;
    private String subscriptionURL;
    private String defaultInitMethod;
    private String defaultRefreshMethod;
    private ArrayList<SubscriptionUnpackListener> subscriptionUnpackListeners;
    private Map<String,Subscription> subscriptions;
    
    public SubscriptionManager(Configuration configuration, ComponentFactory componentFactory, Context androidContext) throws Exception {
        File cacheDir = new File( FileIO.getCacheDir( androidContext ), "semo");
        contentDir = new File( cacheDir, "content");
        if( !(contentDir.exists() || contentDir.mkdirs()) ) {
            throw new Exception( String.format("Unable to create content directory: %s", contentDir.getAbsolutePath() ) );
        }
        downloadDir = new File( cacheDir, "download");
        if( !(downloadDir.exists() || downloadDir.mkdirs()) ) {
            throw new Exception( String.format("Unable to create download directory: %s", downloadDir.getAbsolutePath() ) );
        }
        localSettings = new Locals("semo.subs");
        
        subscriptionURL = configuration.getValueAsString("url");
        if( subscriptionURL == null ) {
            throw new Exception("No subscription URL defined in configuration");
        }
        
        subscriptions = new HashMap<String,Subscription>();
        Map<String,Configuration> subsConfigs = configuration.getValueAsConfigurationMap("subscriptions");
        for(String name : subsConfigs.keySet() ) {
            Configuration subsConfig = subsConfigs.get( name );
            Subscription subs = new Subscription( name, this, androidContext );
            subscriptions.put( name, subs );
        }
        
        subscriptionUnpackListeners = new ArrayList<SubscriptionUnpackListener>();
        List<Configuration> unpackListenerConfigs = configuration.getValueAsConfigurationList("subscriptionUnpackListeners");
        int idx = 0;
        for(Configuration unpackListenerConfig : unpackListenerConfigs ) {
            String id = String.format("subscriptionUnpackListeners[%d]", idx++ );
            try {
                SubscriptionUnpackListener listener = (SubscriptionUnpackListener)componentFactory.makeComponent( unpackListenerConfig, id );
                subscriptionUnpackListeners.add( listener );
            }
            catch(ClassCastException e) {
                throw new Exception( String.format("%s is not an instance of SubscriptionUnpackListener", id ) );
            }
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
    public void refreshSubscription(String name, ContentRefreshListener listener) {
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
    public void refreshAllSubscriptions(final ContentRefreshListener listener) {
        // Iterate over all subscription names.
        final Iterator<String> names = subscriptions.keySet().iterator();
        // Create a refresh listener as a loop controller; it waits for a refresh to complete
        // before continuing with the next refresh.
        ContentRefreshListener loop = new ContentRefreshListener() {
            @Override
            public void onContentRefresh() {
                // If still subscription names...
                if( names.hasNext() ) {
                    // ...then refresh the next subscription.
                    String name = names.next();
                    // Pass this as the refresh listener so that this method is called again
                    // once the refresh is complete.
                    refreshSubscription( name, this );
                }
                else {
                    // All names seen, so call the caller's listener, if any.
                    if( listener != null ) {
                        listener.onContentRefresh();
                    }
                }
            }
        };
        // Start the loop by calling the refresh method.
        loop.onContentRefresh();
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
    
    public List<SubscriptionUnpackListener> getSubscriptionUnpackListeners() {
        return subscriptionUnpackListeners;
    }
}
