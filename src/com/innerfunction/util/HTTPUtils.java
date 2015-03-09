package com.innerfunction.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

import org.json.simple.parser.ParseException;

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

/**
 * Utility methods for download data over HTTP.
 * @author juliangoacher
 *
 */
@SuppressLint("NewApi")
public class HTTPUtils {

    static final String Tag = HTTPUtils.class.getSimpleName();

    /**
     * Read the character encoding of a HTTP response.
     * @param connection
     * @return
     */
    public static String getConnectionCharset(HttpURLConnection connection) {
        // Attempt to read the download's character encoding.
        String charset = "UTF-8"; // Default fallback, if charset not found in response.
        String contentType = connection.getContentType();
        if( contentType != null ) {
            String[] matches = Regex.matches("charset=([^;]+)", contentType );
            if( matches != null && matches.length > 1 ) {
                charset = matches[1];
            }
        }
        return charset;
    }

    /** Callback for the getJSON method. */
    public static interface GetJSONCallback {
        
        /** Method for passing JSON response. */
        public void receivedJSON(Map<String,Object> json);
    }
    
    /** Get JSON from the specified URL. */
    public static void getJSON(final String url, final GetJSONCallback callback) throws MalformedURLException {
        final URL _url = new URL( url );
        // Execute the request on a background thread.
        AsyncTask<Void,Void,Void> task = new AsyncTask<Void,Void,Void>() {
            @SuppressWarnings("unchecked")
            @Override
            protected Void doInBackground(Void... arg0) {
                Map<String,Object> json = null;
                InputStream in = null;
                try {
                    HttpURLConnection connection = (HttpURLConnection)_url.openConnection();
                    connection.setRequestProperty("Accepts","application/json");
                    in = connection.getInputStream();
                    String charset = HTTPUtils.getConnectionCharset( connection );
                    json = (Map<String,Object>)FileIO.readJSON( in, url, charset );
                }
                catch(IOException e) {
                    Log.e( Tag, String.format("Downloading from %s", url ), e );
                }
                catch(ParseException e) {
                    Log.e( Tag, String.format("Parsing JSON from %s", url ), e );
                }
                finally {
                    try {
                        in.close();
                    }
                    catch(Exception e) {}
                    callback.receivedJSON( json );
                }
                return null;
            }
        };
        // Taken from http://stackoverflow.com/a/11977186
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB ) {
            task.executeOnExecutor( AsyncTask.THREAD_POOL_EXECUTOR, (Void[])null );
        }
        else {
            task.execute();
        }
    }

    /** Callback for the getFile method. */
    public static interface GetFileCallback {
        
        /** Method for passing a file response. */
        public void receivedFile(File file);
        
    }
    
    /** 
     * Get data from the specified URL and write to file.
     * @param url       The URL to GET.
     * @param offset    The offset to request data from.
     * @param file      The file to write the data response to.
     * @param callback
     * @throws MalformedURLException
     */
    public static void getFile(final String url, final long offset, final File file, final GetFileCallback callback) throws MalformedURLException {
        final URL _url = new URL( url );
        // Execute the request on a background thread.
        AsyncTask<Void,Void,Void> task = new AsyncTask<Void,Void,Void>() {
            @Override
            protected Void doInBackground(Void... arg0) {
                InputStream in = null;
                try {
                    HttpURLConnection connection = (HttpURLConnection)_url.openConnection();
                    boolean append = false;
                    if( offset > 0 ) {
                        connection.setRequestProperty("Range", String.format("%d-", offset ));
                        append = true;
                    }
                    in = connection.getInputStream();
                    if( !FileIO.writeData( file, in, append ) ) {
                        Log.w(Tag, String.format("Failed to write data to %s", file.getAbsolutePath()));
                    }
                }
                catch(IOException e) {
                    Log.e( Tag, String.format("Downloading from %s", url ), e );
                }
                finally {
                    try {
                        in.close();
                    }
                    catch(Exception e) {}
                    callback.receivedFile( file );
                }
                return null;
            }
        };
        // Taken from http://stackoverflow.com/a/11977186
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB ) {
            task.executeOnExecutor( AsyncTask.THREAD_POOL_EXECUTOR, (Void[])null );
        }
        else {
            task.execute();
        }
    }

}
