/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.pubsub.models;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.util.Log;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.pubsub.Node;
import org.jivesoftware.openfire.roster.Roster;
import org.jivesoftware.openfire.roster.RosterItem;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;

import java.util.ArrayList;
import java.util.List;

/**
 * Anyone in the specified roster group(s) may subscribe and retrieve items.
 *
 * @author Matt Tucker
 */
public class RosterAccess extends AccessModel {

    RosterAccess() {
    }

    public boolean canSubscribe(Node node, JID owner, JID subscriber) {
        // Let node owners and sysadmins always subcribe to the node
        if (node.isAdmin(owner)) {
            return true;
        }
        // Get the only owner of the node
        JID nodeOwner = node.getOwners().iterator().next();
        // Give access to the owner of the roster :)
        if (nodeOwner.toBareJID().equals(owner.toBareJID())) {
            return true;
        }
        // Get the roster of the node owner
        XMPPServer server = XMPPServer.getInstance();
        // Check that the node owner is a local user
        if (server.isLocal(nodeOwner)) {
            try {
                Roster roster = server.getRosterManager().getRoster(nodeOwner.getNode());
                RosterItem item = roster.getRosterItem(owner);
                // Check that the subscriber is subscribe to the node owner's presence
                boolean isSubscribed = item != null && (
                        RosterItem.SUB_BOTH == item.getSubStatus() ||
                                RosterItem.SUB_FROM == item.getSubStatus());
                if (isSubscribed) {
                    // Get list of groups where the contact belongs
                    List<String> contactGroups = new ArrayList<String>(item.getGroups());
                    for (Group group : item.getSharedGroups()) {
                        contactGroups.add(group.getName());
                    }
                    for (Group group : item.getInvisibleSharedGroups()) {
                        contactGroups.add(group.getName());
                    }
                    // Check if subscriber is present in the allowed groups of the node
                    return contactGroups.removeAll(node.getRosterGroupsAllowed());
                }
            }
            catch (UserNotFoundException e) {
                // Do nothing
            }
        }
        else {
            // Owner of the node is a remote user. This should never happen.
            Log.warn("Node with access model Roster has a remote user as owner: " +
                    node.getNodeID());
        }
        return false;
    }

    public boolean canAccessItems(Node node, JID owner, JID subscriber) {
        return canSubscribe(node, owner, subscriber);
    }

    public String getName() {
        return "roster";
    }

    public PacketError.Condition getSubsriptionError() {
        return PacketError.Condition.not_authorized;
    }

    public Element getSubsriptionErrorDetail() {
        return DocumentHelper.createElement(
                QName.get("not-in-roster-group", "http://jabber.org/protocol/pubsub#errors"));
    }

    public boolean isAuthorizationRequired() {
        return false;
    }
}
