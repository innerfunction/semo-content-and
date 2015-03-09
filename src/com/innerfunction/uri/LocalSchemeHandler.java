package com.innerfunction.uri;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import java.util.Map;

public class LocalSchemeHandler extends AbstractSchemeHandler {

    private static final String LogTag = LocalSchemeHandler.class.getSimpleName();
    private Context context;
    private SharedPreferences preferences;

    public LocalSchemeHandler(Context context) {
        this.context = context;
        ApplicationInfo ainfo = context.getApplicationInfo();
        String prefsName = String.format("Eventpac.%s", ainfo.processName );
        Log.i( LogTag, String.format("Using shared preferences name %s", prefsName ) );
        this.preferences = context.getSharedPreferences( prefsName, 0 );
    }

    @Override
    public Resource handle(CompoundURI uri, Map<String, Resource> params, Resource parent) {
        return new LocalResource( this.context, this.preferences, uri, parent );
    }

}
