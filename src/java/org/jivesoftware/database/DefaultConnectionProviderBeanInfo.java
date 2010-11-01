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

package org.jivesoftware.database;

import org.jivesoftware.util.JiveBeanInfo;

/**
 * BeanInfo class for the DefaultConnectionProvider class.
 *
 * @author Jive Software
 */
public class DefaultConnectionProviderBeanInfo extends JiveBeanInfo {

    public static final String[] PROPERTY_NAMES = {
        "driver",
        "serverURL",
        "username",
        "password",
        "minConnections",
        "maxConnections",
        "connectionTimeout"
    };

    public DefaultConnectionProviderBeanInfo() {
        super();
    }

    public Class getBeanClass() {
        return org.jivesoftware.database.DefaultConnectionProvider.class;
    }

    public String[] getPropertyNames() {
        return PROPERTY_NAMES;
    }

    public String getName() {
        return "DefaultConnectionProvider";
    }
}