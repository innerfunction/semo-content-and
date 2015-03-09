package com.innerfunction.uri;

import android.content.Context;
import java.util.Map;

public class StringSchemeHandler extends AbstractSchemeHandler {

    private Context context;

    public StringSchemeHandler(Context context) {
        this.context = context;
    }

    @Override
    public Resource handle(CompoundURI uri, Map<String, Resource> params, Resource parent) {
        return new Resource( this.context, uri.getName(), uri, parent );
    }
}
