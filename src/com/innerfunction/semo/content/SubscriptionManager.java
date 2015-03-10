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
    
    // Rename to getLocalSettings()
    public Locals getSettings() {
        return null;
    }
    
    public File getContentDir() {
        return null;
    }
    
    public File getDownloadDir() {
        return null;
    }
    
    public String getSubscriptionURL() {
        return null;
    }
    
    // Rename to resetSubscriptionContent ?
    public void unpackBaseContentForSubscription(Subscription subs) {
        
    }
    
    public List<PostUnpackListener> getPostUnpackListeners() {
        return postUnpackListeners;
    }
    
    /**
     * Lock or unlock a named subscription.
     * @param name
     * @param locked
     */
    protected void lockSubscription(String name, boolean locked) {
        
    }
}
