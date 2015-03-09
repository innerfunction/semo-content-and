package com.innerfunction.uri;

import java.util.Map;

public interface SchemeHandler {

    public CompoundURI resolveToAbsoluteURI(CompoundURI uri, CompoundURI context);

    public Resource handle(CompoundURI uri, Map<String,Resource> params, Resource parent);

}
