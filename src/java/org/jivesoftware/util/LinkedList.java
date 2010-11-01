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
 * Simple LinkedList implementation. The main feature is that list nodes
 * are public, which allows very fast delete operations when one has a
 * reference to the node that is to be deleted.<p>
 *
 * @author Jive Software
 */
public class LinkedList {

    /**
     * The root of the list keeps a reference to both the first and last
     * elements of the list.
     */
    private LinkedListNode head = new LinkedListNode("head", null, null);

    /**
     * Creates a new linked list.
     */
    public LinkedList() {
        head.next = head.previous = head;
    }

    /**
     * Returns the first linked list node in the list.
     *
     * @return the first element of the list.
     */
    public LinkedListNode getFirst() {
        LinkedListNode node = head.next;
        if (node == head) {
            return null;
        }
        return node;
    }

    /**
     * Returns the last linked list node in the list.
     *
     * @return the last element of the list.
     */
    public LinkedListNode getLast() {
        LinkedListNode node = head.previous;
        if (node == head) {
            return null;
        }
        return node;
    }

    /**
     * Adds a node to the beginning of the list.
     *
     * @param node the node to add to the beginning of the list.
     */
    public LinkedListNode addFirst(LinkedListNode node) {
        node.next = head.next;
        node.previous = head;
        node.previous.next = node;
        node.next.previous = node;
        return node;
    }

    /**
     * Adds an object to the beginning of the list by automatically creating a
     * a new node and adding it to the beginning of the list.
     *
     * @param object the object to add to the beginning of the list.
     * @return the node created to wrap the object.
     */
    public LinkedListNode addFirst(Object object) {
        LinkedListNode node = new LinkedListNode(object, head.next, head);
        node.previous.next = node;
        node.next.previous = node;
        return node;
    }

    /**
     * Adds an object to the end of the list by automatically creating a
     * a new node and adding it to the end of the list.
     *
     * @param object the object to add to the end of the list.
     * @return the node created to wrap the object.
     */
    public LinkedListNode addLast(Object object) {
        LinkedListNode node = new LinkedListNode(object, head, head.previous);
        node.previous.next = node;
        node.next.previous = node;
        return node;
    }

    /**
     * Erases all elements in the list and re-initializes it.
     */
    public void clear() {
        //Remove all references in the list.
        LinkedListNode node = getLast();
        while (node != null) {
            node.remove();
            node = getLast();
        }

        //Re-initialize.
        head.next = head.previous = head;
    }

    /**
     * Returns a String representation of the linked list with a comma
     * delimited list of all the elements in the list.
     *
     * @return a String representation of the LinkedList.
     */
    public String toString() {
        LinkedListNode node = head.next;
        StringBuilder buf = new StringBuilder();
        while (node != head) {
            buf.append(node.toString()).append(", ");
            node = node.next;
        }
        return buf.toString();
    }
}
