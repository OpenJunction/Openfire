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

package org.jivesoftware.openfire.spi;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.cluster.NodeID;
import org.jivesoftware.util.cache.CacheSizes;
import org.jivesoftware.util.cache.Cacheable;
import org.jivesoftware.util.cache.ExternalizableUtil;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Internal object used by RoutingTableImpl to keep track of the node that own a ClientSession
 * and whether the session is available or not.
 *
 * @author Gaston Dombiak
 */
public class ClientRoute implements Cacheable, Externalizable {

    private NodeID nodeID;
    private boolean available;

    public ClientRoute() {
    }


    public NodeID getNodeID() {
        return nodeID;
    }


    public boolean isAvailable() {
        return available;
    }

    public ClientRoute(NodeID nodeID, boolean available) {
        this.nodeID = nodeID;
        this.available = available;
    }

    public int getCachedSize() {
        // Approximate the size of the object in bytes by calculating the size
        // of each field.
        int size = 0;
        size += CacheSizes.sizeOfObject();      // overhead of object
        size += nodeID.toByteArray().length;                  // Node ID
        size += CacheSizes.sizeOfBoolean();     // available
        return size;
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeByteArray(out, nodeID.toByteArray());
        ExternalizableUtil.getInstance().writeBoolean(out, available);
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        byte[] bytes = ExternalizableUtil.getInstance().readByteArray(in);
        // Retrieve the NodeID but try to use the singleton instance
        if (XMPPServer.getInstance().getNodeID().equals(bytes)) {
            nodeID = XMPPServer.getInstance().getNodeID();
        }
        else {
            nodeID = NodeID.getInstance(bytes);
        }
        available = ExternalizableUtil.getInstance().readBoolean(in);
    }
}
