package com.innerfunction.semo.content;

import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import com.innerfunction.uri.FileResource;
import com.innerfunction.uri.Resource;
import com.innerfunction.util.BackgroundTaskRunner;
import com.innerfunction.util.HTTPUtils;
import com.innerfunction.util.Locals;
import com.innerfunction.util.StringTemplate;

/**
 * A content subscription.
 * Downloads content updates and unpacks them to the device's local file system.
 * @author juliangoacher
 */
public class Subscription {

    static final String Tag = Subscription.class.getSimpleName();
    
    /** The subscription name. */
    private String name;
    /** The subscription manager. */
    private ContentManager manager;
    /** An Android context object. */
    private Context context;
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
    /** An object responsible for unpacking downloaded content. */
    private ContentUnpacker unpacker;
    /**
     * A zip file (packaged with the app) containing the subscription's initial content.
     * Can be null. Configured using the initialContent property.
     */
    private Resource initialContent;
    /** File download callback handler. */
    private HTTPUtils.GetFileCallback contentDownloadHandler = new HTTPUtils.GetFileCallback() {
        @Override
        public void receivedFile(File file) {
            Subscription.this.unpackContent( file, false );
            Subscription.this.finishDownload();
        }
    };
    /**
     * An array of refresh listeners.
     * If this is null then it means that no refresh is in progress. If it is non-null then
     * it will contain one or more listeners. All listeners are notified once a refresh completes.
     */
    private List<ContentListener> refreshListeners;
    
    public Subscription(Context context) {
        this.context = context;
    }
    
    /**
     * Ready the subscription for use with a content manager.
     * @param manager   The manager to use the sub with.
     * @param name      The name the sub is registered with the manager under.
     */
    public void setup(ContentManager manager, String name) {
        this.name = name;
        this.manager = manager;
        contentDir = new File( manager.getContentDir(), name );
        subLocals = new Locals( String.format("semo.subs.%s", name ) );
        generalLocals = manager.getLocalSettings();
        unpacker = new ContentUnpacker( context, manager );
    }
    
    /**
     * Set the subscription's initial content resource.
     */
    public void setInitialContent(Resource rsc) {
        initialContent = rsc;
    }
    
    /**
     * Get the subscription name.
     */
    public String getName() {
        return name;
    }
    
    /**
     * Get the subscription local settings.
     */
    public Locals getLocals() {
        return subLocals;
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
    public String getContentVersion() {
        return subLocals.getString("version");
    }
    
    /**
     * Initialize the subscription by ensuring that the initial version is downloaded
     * or unpacked.
     */
    public void initialize(final ContentListener listener) {
        if( subLocals.getBoolean("initialized", false ) ) {
            final String sourceZip = subLocals.getString("sourceZip");
            if( sourceZip != null ) {
                // Path to a source zip file found. This indicates a previous unpack process
                // that was interrupted, so try to resume the operation.
                BackgroundTaskRunner.run(new BackgroundTaskRunner.Task() {
                    @Override
                    public void run() {
                        unpackContent( new File( sourceZip ), true );
                        listener.onContentRefresh();
                    }
                });
            }
            else {
                // Subscription initialized and fully unpacked, so nothing to do.
                // Mark content as initialized.
                subLocals.setBoolean("initialized", true );
                listener.onContentRefresh();
            }
        }
        else if( initialContent instanceof FileResource ) {
            // Subscription not initialized and initial content is specified; so unpack the content
            // before attempting a refresh from the server.
            Log.d( Tag, String.format("Unpacking initial content from %s", initialContent ) );
            BackgroundTaskRunner.run(new BackgroundTaskRunner.Task() {
                @Override
                public void run() {
                    File zipFile = ((FileResource)initialContent).asFile();
                    // Unpack the initial content.
                    Subscription.this.unpackContent( zipFile, false );
                    // Mark content as initialized.
                    subLocals.setBoolean("initialized", true );
                    // Then try a refresh.
                    Subscription.this.refresh( listener );
                }
            });
        }
        else {
            // Subscription not initialized and no initial content specified, so request content
            // from the server.
            Log.d( Tag, "Downloading initial content");
            refresh(new ContentListener() {
                @Override
                public void onContentRefresh() {
                    // Mark content as initialized.
                    subLocals.setBoolean("initialized", true );
                    // Notify the listener.
                    listener.onContentRefresh();
                }
            });
        }
    }
    
    /**
     * Refresh the subscription's content.
     * Checks the general download policy, and attempts to download an update if the policy
     * allows it.
     * @param listener A refresh listener; notified once the refresh has fully completed.
     */
    public void refresh(ContentListener listener) {
        // Add the listener to the list of listeners.
        if( refreshListeners != null ) {
            // A non-null listener list means that a refresh is in progress; so add the
            // new listener to the list and return immediately, the listener will be
            // notified when the refresh completes.
            if( listener != null ) {
                refreshListeners.add( listener );
            }
            return;
        }
        // Starting a new refresh.
        refreshListeners = new ArrayList<ContentListener>();
        if( listener != null ) {
            refreshListeners.add( listener );
        }
        // Check the download policy.
        String downloadPolicy = generalLocals.getString("downloadPolicy", null );
        Log.i( Tag, String.format("downloadPolicy=%s", downloadPolicy) );
        if( "never".equals( downloadPolicy ) ) {
            // Downloads disabled
            refreshComplete();
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
        String filename = String.format("%s.zip", name );
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
        refreshComplete();
    }
    
    /**
     * End of refresh process.
     */
    protected void refreshComplete() {
        // Clear the refresh listener list and notify all listeners on the list.
        List<ContentListener> listeners = refreshListeners;
        refreshListeners = null;
        for(ContentListener listener : listeners) {
            listener.onContentRefresh();
        }
    }
    
    /**
     * Unpack subscription content.
     * Unpacks a content update from a content zip file.
     * @param sourceZipFile The zip file to unpack.
     * @param resume        Indicate whether to attempt to resume a possibly interrupted
     *                      previous unpack process.
     */
    public void unpackContent(File sourceZipFile, boolean resume) {
        unpacker.unpackContent( this, sourceZipFile, resume );
    }

}
