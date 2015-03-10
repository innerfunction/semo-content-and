package com.innerfunction.semo.content;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.innerfunction.util.Locals;

public class SubscriptionManager {

    // Configure subs + manager
    // Subs initialization options (unpack from zip; mirror app fs; download content)
    // Subs refresh options (push/periodic/none)
    // Subs refresh options (all subs sequential/parallel)
    // Subs manual refresh
    
    private ArrayList<PostUnpackListener> postUnpackListeners;
    private File contentDir;
    private File downloadDir;
    private Locals localSettings;
    
    public SubscriptionManager() {
        localSettings = new Locals("semo.subs");
    }
    
    // Rename to getLocalSettings()
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
        return null;
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
    
    public List<PostUnpackListener> getPostUnpackListeners() {
        return postUnpackListeners;
    }
}
