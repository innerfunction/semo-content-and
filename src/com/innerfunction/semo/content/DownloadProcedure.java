package com.innerfunction.semo.content;

import java.io.File;
import java.net.MalformedURLException;
import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;
import com.innerfunction.choreographer.Procedure;
import com.innerfunction.choreographer.Process;
import com.innerfunction.util.HTTPUtils;
import com.innerfunction.util.Locals;

public class DownloadProcedure implements Procedure {

    static final String Tag = DownloadProcedure.class.getSimpleName();
    
    private Context context;
    private Locals downloadSettings;
    private File downloadDir;
    
    public DownloadProcedure(Context context) {
        this.context = context;
    }
    
    @Override
    public void step(String step, String arg, Process process) {
        if("start".equals( step ) ) {
            start( arg, process );
        }
        else if("request".equals( step ) ) {
            request( arg, process );
        }
        else if("done".equals( step ) ) {
            done( process );
        }
    }

    public void start(String url, Process process) {
        boolean startDownload = false;
        // Check the download policy.
        String downloadPolicy = downloadSettings.getString("downloadPolicy", null );
        Log.i( Tag, String.format("downloadPolicy=%s", downloadPolicy) );
        // Check that downloads aren't disabled.
        if( !"never".equals( downloadPolicy ) ) {
            // Check connectivity.
            ConnectivityManager cm = (ConnectivityManager)context.getSystemService( Context.CONNECTIVITY_SERVICE );
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            boolean connected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
            if( !connected ) {
                // No network, so can't download.
                Log.d( Tag, "Network not reachable");
            }
            else {
                // Check network type.
                // TODO: Download policy values may need to be reviewed here - Android provides greater
                // discrimination between network types.
                switch( activeNetwork.getType() ) {
                case ConnectivityManager.TYPE_WIFI:
                case ConnectivityManager.TYPE_WIMAX:
                case ConnectivityManager.TYPE_ETHERNET:
                    // Always download over wifi network.
                    Log.d( Tag, "WIFI or equivalent network available");
                    startDownload = true;
                    break;
                default:
                    Log.d( Tag, "Non-WIFI network available");
                    if( !"wifi-only".equals( downloadPolicy ) ) {
                        // Only download if policy allows it.
                        startDownload = true;
                    }
                }
            }
        }
        if( startDownload ) {
            process.step("prepare", url );
        }
        else {
            process.done();
        }
    }
    
    @SuppressLint("DefaultLocale")
    public void prepare(String url, Process process) {
        String filename = String.format("%s.%d.tmp", Tag, process.getPID() );
        File downloadFile = new File( downloadDir, filename );
        process.getLocals().setString("downloadFile", downloadFile.getAbsolutePath() );
        process.step("request", url );
    }
    
    public void request(String url, final Process process) {
        String downloadFilename = process.getLocals().getString("downloadFilename");
        if( downloadFilename == null ) {
            process.error("downloadFilename not found");
        }
        else {
            File downloadFile = new File( downloadFilename );
            long offset = downloadFile.length();
            try {
                HTTPUtils.getFile( url, offset, downloadFile, new HTTPUtils.FileRequestCallback() {
                    @Override
                    public void receivedFile(File file) {
                        process.step("done");
                    }
                });
            }
            catch(MalformedURLException e) {
                downloadFile.delete();
                process.error( e );
            }
        }
    }
    
    public void done(Process process) {
        process.done( process.getLocals().getString("downloadFilename") );
    }
    
}
