package com.innerfunction.util;

import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Singleton class for sending notifications between objects.
 * Objects register to receive notifications of a specific type. Notifications of that type can then be
 * sent to each observer object, together with an object payload.
 */
public class NotificationCenter {

    private static NotificationCenter Instance = new NotificationCenter();
    private ConcurrentMap<String,Observable> observablesByType = new ConcurrentHashMap<String,Observable>();

    private NotificationCenter() {};

    /** Send a notification of the specified type with the specified data payload. */
    public void sendNotification(String type, Object data) {
        Observable observable = this.observablesByType.get( type );
        if( observable != null ) {
            observable.notifyObservers( data );
        }
    }

    /** Add an observer for notifications of the specified type. */
    public Observer addObserver(String type, Observer observer) {
        Observable observable = this.observablesByType.get( type );
        if( observable == null ) {
            observable = new TypeObservable();
            Observable previous = this.observablesByType.putIfAbsent( type, observable );
            // If 'previous' isn't null then some other thread has added a new observer of the specified type,
            // before the current thread; so give precedence to the previously added observer.
            observable = (previous == null) ? observable : previous;
        }
        observable.addObserver( observer );
        return observer;
    }

    /** Remove a previously registered observer for notifications of the specified type. */
    public void removeObserver(String type, Observer observer) {
        Observable observable = this.observablesByType.get( type );
        if( observable != null ) {
            observable.deleteObserver( observer );
        }
    }

    /** Remove all instances of a previously registered notification observer. */
    public void removeObserver(Observer observer) {
        // Iterate over all observables and attempt removing the observer from each one.
        for( String type : this.observablesByType.keySet() ) {
            Observable observable = this.observablesByType.get( type );
            if( observable != null ) {
                observable.deleteObserver( observer );
            }
        }
    }

    /** Get an instance of this class. */
    public static NotificationCenter getInstance() {
        return Instance;
    }

    /**
     * Subclass of Observable for recording update type observers.
     * The only reason this is necessary is because the setChanged() method is protected.
     */
    static class TypeObservable extends Observable {
        
        public TypeObservable() {}
        
        public void notifyObservers(Object data) {
            setChanged();
            super.notifyObservers( data );
        }
    }

}
