package com.innerfunction.uri;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.innerfunction.util.NotificationCenter;

/**
 * Object for representing local preference data resources.
 */
public class LocalResource extends Resource {

    private static final String LogTag = "LocalURIResource";
    private String name;
    private SharedPreferences preferences;

    public LocalResource(Context context, SharedPreferences preferences, CompoundURI uri, Resource parent) {
        super( context, uri.getName(), uri, parent );
        this.name = uri.getName();
        this.preferences = preferences;
        this.updateable = true;
    }

    public String getLocalName() {
    	return this.name;
    }
    
    public String asString() {
        return this.preferences.getString( name, null );
    }

    public Number asNumber() {
        return this.preferences.getFloat( name, 0 );
    }

    public Boolean asBoolean() {
        return this.preferences.getBoolean( name, false );
    }

    public boolean updateWithValue(Object value) {
        SharedPreferences.Editor editor = this.preferences.edit();
        if( value instanceof Boolean ) {
            editor.putBoolean( this.name, (Boolean)value );
        }
        else if( value instanceof Number ) {
            editor.putFloat( this.name, ((Number)value).floatValue() );
        }
        else {
            editor.putString( this.name, value.toString() );
        }
        if( editor.commit() ) {
            NotificationCenter.getInstance().sendNotification( Constants.NotificationTypes.LocalDataUpdate.toString(), this.name );
            return true;
        }
        Log.w( LogTag, String.format("Failed to write update to shared preferences (%s -> %s)", value, this.name ) );
        return false;
    }
}
