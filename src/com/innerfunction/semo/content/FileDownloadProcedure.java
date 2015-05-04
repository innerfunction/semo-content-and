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

/**
 * File download procedure primitive.
 * Implements a single-stepped procedure to download a file from a URL and return the path
 * to the downloaded file.
 *
 * @author juliangoacher
 *
 */
public class FileDownloadProcedure implements Procedure {

    static final String Tag = FileDownloadProcedure.class.getSimpleName();
    
    static enum ShouldDownload { Yes, WaitForConnectivity, No };
    
    private ConnectivityManager connectivityManager;
    private Locals downloadSettings;
    private File downloadDir;
    
    public FileDownloadProcedure(Context context) {
        connectivityManager = (ConnectivityManager)context.getSystemService( Context.CONNECTIVITY_SERVICE );
    }
    
    public void setDownloadSettings(Locals settings) {
        downloadSettings = settings;
    }
    
    public void setDownloadDir(File dir) {
        downloadDir = dir;
    }
    
    @SuppressLint({ "DefaultLocale", "NewApi" })
    @Override
    public void run(final Process process, final String step, final Object... args) {
        if("start".equals( step ) ) {
            ShouldDownload shouldDownload = checkShouldDownload();
            switch( shouldDownload ) {
            case Yes:
                String filename = process.getLocals().getString("download.filename");
                if( filename == null ) {
                    filename = String.format("%s.%d.tmp", Tag, process.getPID() );
                    process.getLocals().setString("download.filename", filename );
                }
                File downloadFile = new File( downloadDir, filename );
                long offset = downloadFile.length();
                try {
                    String url = (String)args[0];
                    HTTPUtils.getFile( url, offset, downloadFile, new HTTPUtils.FileRequestCallback() {
                        @Override
                        public void receivedFile(File file) {
                            process.done( file.getAbsolutePath() );
                        }
                    });
                }
                catch(MalformedURLException e) {
                    downloadFile.delete();
                    process.error( e );
                }
                break;
            case WaitForConnectivity:
                connectivityManager.addDefaultNetworkActiveListener(new ConnectivityManager.OnNetworkActiveListener() {
                    @Override
                    public void onNetworkActive() {
                        connectivityManager.removeDefaultNetworkActiveListener( this );
                        run( process, step, args );
                    }
                });
                break;
            case No:
            default:
                process.done();
            }
        }
        else {
            process.error("%s: Bad step value; %s", Tag, step );
        }
    }

    private ShouldDownload checkShouldDownload() {
        ShouldDownload shouldDownload = ShouldDownload.No;
        // Check the download policy.
        String downloadPolicy = downloadSettings.getString("downloadPolicy", null );
        Log.i( Tag, String.format("downloadPolicy=%s", downloadPolicy) );
        // Check that downloads aren't disabled.
        if( !"never".equals( downloadPolicy ) ) {
            // Check connectivity.
            NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
            boolean connected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
            if( !connected ) {
                shouldDownload = ShouldDownload.WaitForConnectivity;
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
                    shouldDownload = ShouldDownload.Yes;
                    break;
                default:
                    Log.d( Tag, "Non-WIFI network available");
                    if( !"wifi-only".equals( downloadPolicy ) ) {
                        // Only download if policy allows it.
                        shouldDownload = ShouldDownload.Yes;
                    }
                    else {
                        shouldDownload = ShouldDownload.WaitForConnectivity;
                    }
                }
            }
        }
        return shouldDownload;
    }
    
}
