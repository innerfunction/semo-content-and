package com.innerfunction.uri;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import com.innerfunction.util.FileIO;

/**
 * Object for representing file resources.
 */
@SuppressWarnings("unused")
public class FileResource extends Resource {

    private static final String LogTag = FileResource.class.getSimpleName();

    /** The file being represented. */
    private File file;

    public FileResource(Context context, File file, CompoundURI uri, Resource parent) {
        super( context, file, uri, parent );
        this.file = file;
        this.updateable = true;
    }

    protected FileResource(Context context, Object resource, CompoundURI uri, Resource parent) {
        super( context, resource, uri, parent );
        this.updateable = true;
    }

    public InputStream openInputStream() throws IOException {
        return new FileInputStream( this.file );
    }

    public String getName() {
        return this.file.getAbsolutePath();
    }

    public boolean exists() {
        return this.file.exists();
    }

    /** Return the string contents of the file resource. */
    public String asString() {
        return FileIO.readString( this.file, "UTF-8");
    }

    /** Return the file URL. */
    public URI asURL() {
        return this.file.toURI();
    }

    /** Return the byte contents of the file resource. */
    public byte[] asData() {
        try {
            return FileIO.readData( this.file );
        }
        catch (FileNotFoundException e) {
            return null;
        }
    }

    /** Return the file's contents as parsed JSON data. */
    public Object asJSONData() {
        return conversions.asJSONData( asString() );
    }

    /** Return the contents of the file resource as an image. */
    public Drawable asImage() {
        return Drawable.createFromPath( this.file.getAbsolutePath() );
    }

    public Object getRepresentation(String representation) {
        if( "data".equals( representation ) ) {
            return this.asData();
        }
        return super.getRepresentation( representation );
    }

}
