/**
 * $RCSfile$
 * $Revision: 29557 $
 * $Date: 2006-04-19 21:21:28 -0700 (Wed, 19 Apr 2006) $
 *
 * Copyright (C) 2004-2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.xmpp.workgroup.utils;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.StringUtils;
import org.xmpp.component.ComponentManagerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Date;

/**
 * Handles basic DB Operations.
 */
public class DbWorkgroup {

    private static final String UPDATE_TRANSCRIPT =
            "UPDATE fpSession SET transcript=?, endTime=? WHERE sessionID=?";
    private static final String INSERT_AGENT_SESSION =
            "INSERT INTO fpAgentSession(sessionID, agentJID, joinTime, leftTime) VALUES(?,?,?,?)";
    private static final String UPDATE_AGENT_SESSION =
            "UPDATE fpAgentSession SET leftTime=? WHERE sessionID=? AND agentJID=?";

    private DbWorkgroup() {
        // Empty Private Constructor for Utility class.
    }

    /**
     * Updates a chat transcript.
     *
     * @param sessionID  the sessionID belonging to the chat transcript.
     * @param transcript the chat transcript (XML)
     * @param endTime    the time the chat ended.
     */
    public static void updateTranscript(String sessionID, String transcript, Date endTime) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_TRANSCRIPT);

            DbConnectionManager.setLargeTextField(pstmt, 1, transcript);
            pstmt.setString(2, StringUtils.dateToMillis(endTime));
            pstmt.setString(3, sessionID);
            pstmt.executeUpdate();
        }
        catch (Exception e) {
            ComponentManagerFactory.getComponentManager().getLog().error(e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    /**
     * Updates the Joined Session.
     *
     * @param sessionID the sessionID.
     * @param agent the agent who joined.
     * @param joining true if they are joining, false if leaving.
     */
    public static void updateJoinedSession(String sessionID, String agent, boolean joining) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            Date time = new Date();
            con = DbConnectionManager.getConnection();

            if (joining) {
                pstmt = con.prepareStatement(INSERT_AGENT_SESSION);
                pstmt.setString(1, sessionID);
                pstmt.setString(2, agent);
                pstmt.setString(3, StringUtils.dateToMillis(time));
                pstmt.setString(4, "");
            }
            else {
                pstmt = con.prepareStatement(UPDATE_AGENT_SESSION);
                pstmt.setString(1, StringUtils.dateToMillis(time));
                pstmt.setString(2, sessionID);
                pstmt.setString(3, agent);
            }
            pstmt.executeUpdate();
        }
        catch (Exception ex) {
            ComponentManagerFactory.getComponentManager().getLog().error(ex);
        }
        finally {
           DbConnectionManager.closeConnection(pstmt, con);
        }
    }
}