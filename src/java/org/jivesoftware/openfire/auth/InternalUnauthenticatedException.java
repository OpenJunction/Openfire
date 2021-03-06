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

/**
 * Thrown when Openfire is not able to authenticate itself into the user and group system.
 *
 * @author Gabriel Guardincerri
 */
public class InternalUnauthenticatedException extends Exception {

    public InternalUnauthenticatedException() {
        super();
    }

    public InternalUnauthenticatedException(String message) {
        super(message);
    }

    public InternalUnauthenticatedException(String message, Throwable cause) {
        super(message, cause);
    }

    public InternalUnauthenticatedException(Throwable cause) {
        super(cause);
    }
}
