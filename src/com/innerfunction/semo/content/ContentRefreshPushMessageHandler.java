package com.innerfunction.semo.content;

import android.content.Intent;

import com.innerfunction.semo.Component;
import com.innerfunction.semo.Configuration;
import com.innerfunction.semo.push.PushMessageHandler;
import com.innerfunction.semo.push.NotificationService;

public class ContentRefreshPushMessageHandler implements Component, PushMessageHandler {

    // TODO: How to resolve a reference to this.
    private ContentManager contentManager;
    private boolean autoRefresh;
    
    @Override
    public void configure(Configuration configuration) {
        // TODO Auto-generated method stub
        autoRefresh = configuration.getValueAsBoolean("autoRefresh");
    }

    @Override
    public boolean handleMessageIntent(Intent intent, NotificationService service) {
        // Refresh silently in background, or prompt user before refresh
        String subName = intent.getStringExtra("subscriptionName");
        if( autoRefresh ) {
            contentManager.refreshSubscription( subName );
        }
        else {
            // Prompt user to download
        }
        return false;
    }

}
