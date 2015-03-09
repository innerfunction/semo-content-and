package com.innerfunction.uri;

import java.util.Map;

public abstract class AbstractSchemeHandler implements SchemeHandler {

    @Override
    public CompoundURI resolveToAbsoluteURI(CompoundURI uri, CompoundURI context) {
        return uri;
    }

    @Override
    public abstract Resource handle(CompoundURI uri, Map<String, Resource> params, Resource parent);

}
