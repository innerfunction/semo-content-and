package com.innerfunction.semo.content;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SubscriptionManager {

    private ArrayList<PostUnpackListener> postUnpackListeners;
    
    public File getContentDir() {
        return null;
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
