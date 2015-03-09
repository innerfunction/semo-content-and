package com.innerfunction.semo.content;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SubscriptionManager {

    private ArrayList<PostUnpackListener> postUnpackListeners;
    
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
