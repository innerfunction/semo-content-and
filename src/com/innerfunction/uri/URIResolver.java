package com.innerfunction.uri;

public interface URIResolver {

    public Resource resolveURIFromString(String uri);

    public Resource resolveURIFromString(String uri, Resource context);

    public Resource resolveURI(CompoundURI uri);

    public Resource resolveURI(CompoundURI uri, Resource context);

    public boolean dispatchURI(String uri);
    
    public boolean dispatchURI(String uri, Resource context);
}
