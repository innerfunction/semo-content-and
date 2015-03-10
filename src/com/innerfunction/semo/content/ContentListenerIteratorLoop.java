package com.innerfunction.semo.content;

import java.util.Iterator;

/**
 * A utility function for iterating over items in an iterator using an async content operation.
 * The async content operation is called for each iteration, and the iterator only progresses once
 * that operation has completed.
 * @author juliangoacher
 *
 */
public class ContentListenerIteratorLoop {

    /** Interface implemented by iteration operations. */
    public static interface IterationOp<T> {
        /** Execute an iteration. */
        public void iteration(T item, ContentListener listener);
    }
    
    /**
     * Loop over the items in the specified iterator, calling the iteration op for each item.
     * The content listener will be called after all items have been iterated over.
     * @param it
     * @param op
     * @param listener
     */
    public static <T> void loop(final Iterator<T> it, final IterationOp<T> op, final ContentListener listener) {
        ContentListener loop = new ContentListener() {
            @Override
            public void onContentRefresh() {
                // If still subscription names...
                if( it.hasNext() ) {
                    // ...then refresh the next subscription.
                    T item = it.next();
                    // Pass this as the refresh listener so that this method is called again
                    // once the refresh is complete.
                    op.iteration( item, this );
                }
                else {
                    // All names seen, so call the caller's listener, if any.
                    if( listener != null ) {
                        listener.onContentRefresh();
                    }
                }
            }
        };
        // Start the loop by calling the refresh method.
        loop.onContentRefresh();
    }
}
