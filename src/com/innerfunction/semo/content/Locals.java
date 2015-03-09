// TODO: Move to com.innerfunction.util
package com.innerfunction.semo.content;

import android.content.SharedPreferences;

public class Locals {

    private String keyPrefix;
    private SharedPreferences preferences;

    public Locals(String keyPrefix) {
        this.keyPrefix = keyPrefix+".";
    }
    
    public Locals() {
        this.keyPrefix = "";
    }
    
    public String getString(String name) {
        return getString( name, null );
    }
    
    public String getString(String name, String defValue) {
        return preferences.getString( getKey( name ), defValue );
    }
    
    public String setString(String name, String value) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString( getKey( name ), value );
        editor.commit();
        return value;
    }
    
    public int getInt(String name) {
        return getInt( name, -1 );
    }
    
    public int getInt(String name, int defValue) {
        return preferences.getInt( getKey( name ), defValue );
    }
    
    public int setInt(String name, int value) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt( getKey( name ), value );
        editor.commit();
        return value;
    }

    public void remove(String... names) {
        SharedPreferences.Editor editor = preferences.edit();
        for( String name : names ) {
            editor.remove( name );
        }
        editor.commit();
    }
    
    private String getKey(String name) {
        return keyPrefix+name;
    }
}
