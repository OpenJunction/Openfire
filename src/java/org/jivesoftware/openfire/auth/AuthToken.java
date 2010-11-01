/**
 * $RCSfile$
 * $Revision: 691 $
 * $Date: 2004-12-13 15:06:54 -0300 (Mon, 13 Dec 2004) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.auth;

import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.util.JiveGlobals;

/**
 * A token that proves that a user has successfully authenticated.
 *
 * @author Matt Tucker
 * @see AuthFactory
 */
public class AuthToken {

    private static final long serialVersionUID = 01L;
    private String username;
    private String domain;
    private Boolean anonymous;

    /**
     * Constucts a new AuthToken with the specified username.
     * The username can be either a simple username or a full JID.
     *
     * @param jid the username or bare JID to create an authToken token with.
     */
    public AuthToken(String jid) {
        if (jid == null) {
            this.domain = JiveGlobals.getProperty("xmpp.domain");
            return;
        }
        int index = jid.indexOf("@");
        if (index > -1) {
            this.username = jid.substring(0,index);
            this.domain = jid.substring(index+1);
        } else {
            this.username = jid;
            this.domain = JiveGlobals.getProperty("xmpp.domain");
        }
    }

    public AuthToken(String jid, Boolean anonymous) {
        int index = jid.indexOf("@");
        if (index > -1) {
            this.username = jid.substring(0,index);
            this.domain = jid.substring(index+1);
        } else {
            this.username = jid;
            this.domain = JiveGlobals.getProperty("xmpp.domain");
        }
        this.anonymous = anonymous;
    }

    /**
     * Returns the username associated with this AuthToken. A <tt>null</tt> value
     * means that the authenticated user is anonymous.
     *
     * @return the username associated with this AuthToken or null when using an anonymous user.
     */
    public String getUsername() {
        return username;
    }

    /**
     * Returns the domain associated with this AuthToken.
     *
     * @return the domain associated with this AuthToken.
     */
    public String getDomain() {
        return domain;
    }

    /**
     * Returns true if this AuthToken is the Anonymous auth token.
     *
     * @return true if this token is the anonymous AuthToken.
     */
    public boolean isAnonymous() {
        if (anonymous == null) {
            anonymous = username == null || !UserManager.getInstance().isRegisteredUser(username);
        }
        return anonymous;
    }
}