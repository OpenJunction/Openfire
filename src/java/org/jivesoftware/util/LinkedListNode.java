/**
 * $RCSfile$
 * $Revision: 10204 $
 * $Date: 2008-04-11 15:44:25 -0700 (Fri, 11 Apr 2008) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.util;

/**
 * Doubly linked node in a LinkedList. Most LinkedList implementations keep the
 * equivalent of this class private. We make it public so that references
 * to each node in the list can be maintained externally.
 * <p/>
 * Exposing this class lets us make remove operations very fast. Remove is
 * built into this class and only requires two reference reassignments. If
 * remove existed in the main LinkedList class, a linear scan would have to
 * be performed to find the correct node to delete.
 * <p/>
 * The linked list implementation was specifically written for the Jive
 * cache system. While it can be used as a general purpose linked list, for
 * most applications, it is more suitable to use the linked list that is part
 * of the Java Collections package.
 *
 * @author Jive Software
 * @see org.jivesoftware.util.LinkedList
 */
public class LinkedListNode {

    public LinkedListNode previous;
    public LinkedListNode next;
    public Object object;

    /**
     * This class is further customized for the CoolServlets cache system. It
     * maintains a timestamp of when a Cacheable object was first added to
     * cache. Timestamps are stored as long values and represent the number
     * of milleseconds passed since January 1, 1970 00:00:00.000 GMT.<p>
     * <p/>
     * The creation timestamp is used in the case that the cache has a
     * maximum lifetime set. In that case, when
     * [current time] - [creation time] > [max lifetime], the object will be
     * deleted from cache.
     */
    public long timestamp;

    /**
     * Constructs a new linked list node.
     *
     * @param object   the Object that the node represents.
     * @param next     a reference to the next LinkedListNode in the list.
     * @param previous a reference to the previous LinkedListNode in the list.
     */
    public LinkedListNode(Object object, LinkedListNode next,
                          LinkedListNode previous) {
        this.object = object;
        this.next = next;
        this.previous = previous;
    }

    /**
     * Removes this node from the linked list that it is a part of.
     */
    public void remove() {
        previous.next = next;
        next.previous = previous;
    }

    /**
     * Returns a String representation of the linked list node by calling the
     * toString method of the node's object.
     *
     * @return a String representation of the LinkedListNode.
     */
    public String toString() {
        return object == null ? "null" : object.toString();
    }
}
