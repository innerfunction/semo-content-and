package com.innerfunction.uri;

import android.content.Context;
import android.util.Log;
import com.innerfunction.util.Paths;
import java.io.File;
import java.util.Map;

public class FileBasedSchemeHandler extends AbstractSchemeHandler {

    private static final String LogTag = FileBasedSchemeHandler.class.getSimpleName();
    protected Context context;
    protected File rootDir;

    protected FileBasedSchemeHandler(Context context) {
        this.context = context;
        this.rootDir = new File("/");
    }

    public FileBasedSchemeHandler(Context context, String rootPath) {
        this.context = context;
        this.rootDir = new File( rootPath );
    }

    public FileBasedSchemeHandler(Context context, File rootDir) {
        this.context = context;
        this.rootDir = rootDir;
    }

    @Override
    public CompoundURI resolveToAbsoluteURI(CompoundURI uri, CompoundURI context) {
        // If URI name doesn't begin with / then it is a relative URI.
        String name = uri.getName();
        if( name.charAt( 0 ) != '/' ) {
            uri = uri.copyOf();
            File contextDir = new File( Paths.dirname( context.getName() ) );
            uri.setName( new File( contextDir, name ).getAbsolutePath() );
        }
        return uri;
    }

    @Override
    public Resource handle(CompoundURI uri, Map<String,Resource> params, Resource parent) {
        Resource result = null;
        String name = uri.getName();
        if( name.length() > 0 && name.charAt( 0 ) == '/' ) {
            name = name.substring( 1 );
        }
        File file = new File( this.rootDir, name );
        Log.d(LogTag, String.format("%s -> %s", uri, file.getAbsolutePath() ) );
        if( file.exists() ) {
            result = new FileResource( this.context, file, uri, parent );
        }
        else{
            Log.w( LogTag, "File not found: " + uri);
        }
        return result;
    }

}

