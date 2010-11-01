/**
 * $RCSfile$
 * $Revision: 3191 $
 * $Date: 2005-12-12 13:41:22 -0300 (Mon, 12 Dec 2005) $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.update;

import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.jivesoftware.openfire.XMPPServer;

/**
 * Service that allow for aysynchrous calling of system managers.
 *
 * @author Derek DeMoro
 */
public class PluginDownloadManager {

    /**
     * Starts the download process of a given plugin with it's URL.
     *
     * @param url the url of the plugin to download.
     * @return the Update.
     */
    public Update downloadPlugin(String url) {
        UpdateManager updateManager = XMPPServer.getInstance().getUpdateManager();
        updateManager.downloadPlugin(url);

        Update returnUpdate = null;
        for (Update update : updateManager.getPluginUpdates()) {
            if (update.getURL().equals(url)) {
                returnUpdate = update;
                break;
            }
        }

        return returnUpdate;
    }

    /**
     * Installs a new plugin into Openfire.
     *
     * @param url the url of the plugin to install.
     * @param hashCode the matching hashcode of the <code>AvailablePlugin</code>.
     * @return the hashCode.
     */
    public DownloadStatus installPlugin(String url, int hashCode) {
        UpdateManager updateManager = XMPPServer.getInstance().getUpdateManager();

        boolean worked = updateManager.downloadPlugin(url);

        final DownloadStatus status = new DownloadStatus();
        status.setHashCode(hashCode);
        status.setSuccessfull(worked);
        status.setUrl(url);

        return status;
    }

    /**
     * Updates the PluginList from the server. Please note, this method is used with javascript calls and will not
     * be found with a find usages.
     *
     * @return true if the plugin list was updated.
     */
    public boolean updatePluginsList() {
        UpdateManager updateManager = XMPPServer.getInstance().getUpdateManager();
        try {
            // Todo: Unify update checking into one xml file. Have the update check set the last check property.
            updateManager.checkForServerUpdate(true);
            updateManager.checkForPluginsUpdates(true);

            // Keep track of the last time we checked for updates
            JiveGlobals.setProperty("update.lastCheck",
                    String.valueOf(System.currentTimeMillis()));

            return true;
        }
        catch (Exception e) {
            Log.error(e);
        }

        return false;
    }
}