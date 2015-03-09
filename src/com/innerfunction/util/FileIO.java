package com.innerfunction.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.StringBufferInputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

/**
 * Utility methods for reading and writing files and streams.
 * @author juliangoacher
 *
 */
@SuppressWarnings("deprecation")
public class FileIO {

    static final String LogTag = "FileIO";
    
    public static byte[] readData(File file) throws FileNotFoundException{
        byte[] data = null;
        FileInputStream fin = null;
        try {
            fin = new FileInputStream( file );
            data = new byte[ (int)file.length() ];
            fin.read( data, 0, data.length );
        }
        catch(FileNotFoundException e){
            throw e;
        }
        catch(Exception e) {
            Log.e( LogTag, String.format("Reading file %s", file.getPath() ), e );
        }
        finally {
            try {
                fin.close();
            }
            catch(Exception e) {}
        }
        return data;
    }
    
    public static byte[] readData(InputStream in, String name) {
        ByteArrayOutputStream result = new ByteArrayOutputStream( 16384 );
        try {
            byte[] buffer = new byte[16384];
            int read;
            while( (read = in.read( buffer )) != -1 ) {
                result.write( buffer, 0, read );
            }
        }
        catch(Exception e) {
            Log.e( LogTag, String.format("Reading stream %s", name ), e );
        }
        finally {
            try {
                in.close();
            }
            catch(Exception e) {}
        }
        return result.toByteArray();
    }

    public static String readString(File file, String encoding){
        String str = null;
        try {
            str = new String( FileIO.readData( file ), encoding );
        }
        catch (FileNotFoundException e){
            Log.e( LogTag, String.format("File not found %s", file.getAbsolutePath()));
        }
        catch(UnsupportedEncodingException e) {
            Log.e( LogTag, String.format("%s decoding error", encoding) );
        }
        return str;
    }

    public static String readString(InputStream in, String name, String encoding) {
        String str = null;
        try {
            str = new String( FileIO.readData( in, name ), encoding );
        }
        catch(UnsupportedEncodingException e) {
            Log.e( LogTag, String.format("%s decoding error", encoding) );
        }
        return str;
    }

    public static Object readJSON(File file, String encoding) throws FileNotFoundException, ParseException {
        return JSONValue.parse( readString( file, encoding ) );
    }

    public static Object readJSON(InputStream in, String name, String encoding) throws ParseException {
        return JSONValue.parse( readString( in, name, encoding ) );
    }

    public static boolean writeData(File file, InputStream in) {
        return FileIO.writeData( file, in, false );
    }
    
    public static boolean writeData(File file, InputStream in, boolean append) {
        boolean ok = true;
        FileOutputStream fout = null;
        try {
            byte[] buffer = new byte[16384];
            fout = new FileOutputStream( file, append );
            int length;
            while( (length = in.read( buffer )) > 0 ) {
                fout.write( buffer, 0, length );
            }
        }
        catch(Exception e) {
            Log.e(LogTag, String.format("Writing %s", file ), e );
        }
        finally {
            try {
                fout.close();
            }
            catch(Exception e) {}
        }
        return ok;
    }

    public static boolean writeString(File file, String s) {
        return FileIO.writeData( file, new StringBufferInputStream( s ));
    }

    public static String[] unzip(File zipFile, File targetDir) {
        FileInputStream in = null;
        try {
            in = new FileInputStream( zipFile );
            return unzip( in, targetDir );
        }
        catch(Exception e) {
            Log.e(LogTag, String.format("Unzipping %s", zipFile ), e );
        }
        return new String[0];
    }
    
    public static String[] unzip(InputStream in, File targetDir) {
        List<String> files = new ArrayList<String>();
        ZipInputStream zin = null;
        try {
            zin = new ZipInputStream( in );
            ZipEntry entry;
            while( (entry = zin.getNextEntry()) != null ) {
                String fileName = entry.getName();
                File entryFile = new File( targetDir, fileName );
                if( entry.isDirectory() ) {
                    if( !entryFile.isDirectory() ) {
                        entryFile.mkdirs();
                    }
                }
                else {
                    File parentDir = entryFile.getParentFile();
                    if( !parentDir.isDirectory() ) {
                        parentDir.mkdirs();
                    }
                    FileIO.writeData( entryFile, zin );
                }
                zin.closeEntry();
                files.add( entryFile.getAbsolutePath() );
            }
        }
        catch(Exception e) {
            Log.e(LogTag, "Unzipping input stream");
        }
        finally {
            try {
                zin.close();
            }
            catch(Exception e) {}
        }
        String result[] = new String[files.size()];
        for( int i = 0; i < result.length; i++ ) {
            result[i] = files.get( i );
        }
        return result;
    }
    
    public static File getCacheDir(Context context) {
        // TODO: Should instead use getExternalCacheDir?
        return context.getExternalFilesDir( Environment.DIRECTORY_DOWNLOADS );
    }

    public static boolean removeDir(File dir, Context context) {
        boolean ok = false;
        File cacheDir = FileIO.getCacheDir( context );
        if( dir.exists() && dir.toString().startsWith( cacheDir.toString() ) ) {
            Runtime rt = Runtime.getRuntime();
            try {
                // Delete the directory by first moving to a temporary location, then deleting.
                // This is because deleting in place will cause problems if the location is to be written
                // to immediately after.
                File temp = new File( dir.getParentFile(), String.format("fileio-%d-rm", System.currentTimeMillis() ) );
                dir.renameTo( temp );
                String cmd = String.format("rm -Rf %s", temp );
                @SuppressWarnings("unused")
                Process p = rt.exec( cmd );
                //p.waitFor(); Uncomment if should wait for rm to complete before continuing (i.e. synchronous behaviour)
                ok = true;
            }
            catch(Exception e) {
                Log.e(LogTag, String.format("Removing directory %s", dir ), e );
            }
        }
        return ok;
    }

}
