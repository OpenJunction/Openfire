/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.group;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.xmpp.packet.JID;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * The JDBC group provider allows you to use an external database to define the make up of groups.
 * It is best used with the JDBCAuthProvider to provide integration between your external system and
 * Openfire.  All data is treated as read-only so any set operations will result in an exception.
 *
 * To enable this provider, set the following in the system properties:
 *
 * <ul>
 * <li><tt>provider.group.className = org.jivesoftware.openfire.group.JDBCGroupProvider</tt></li>
 * </ul>
 *
 * Then you need to set your driver, connection string and SQL statements:
 *
 * <ul>
 * <li><tt>jdbcProvider.driver = com.mysql.jdbc.Driver</tt></li>
 * <li><tt>jdbcProvider.connectionString = jdbc:mysql://localhost/dbname?user=username&amp;password=secret</tt></li>
 * <li><tt>jdbcGroupProvider.groupCountSQL = SELECT count(*) FROM myGroups</tt></li>
 * <li><tt>jdbcGroupProvider.allGroupsSQL = SELECT groupName FROM myGroups</tt></li>
 * <li><tt>jdbcGroupProvider.userGroupsSQL = SELECT groupName FORM myGroupUsers WHERE username=?</tt></li>
 * <li><tt>jdbcGroupProvider.descriptionSQL = SELECT groupDescription FROM myGroups WHERE groupName=?</tt></li>
 * <li><tt>jdbcGroupProvider.loadMembersSQL = SELECT username FORM myGroupUsers WHERE groupName=? AND isAdmin='N'</tt></li>
 * <li><tt>jdbcGroupProvider.loadAdminsSQL = SELECT username FORM myGroupUsers WHERE groupName=? AND isAdmin='Y'</tt></li>
 * </ul>
 *
 * In order to use the configured JDBC connection provider do not use a JDBC
 * connection string, set the following property
 *
 * <ul>
 * <li><tt>jdbcGroupProvider.useConnectionProvider = true</tt></li>
 * </ul>
 *
 * @author David Snopek
 */
public class JDBCGroupProvider implements GroupProvider {

    private String connectionString;

    private String groupCountSQL;
    private String descriptionSQL;
    private String allGroupsSQL;
    private String userGroupsSQL;
    private String loadMembersSQL;
    private String loadAdminsSQL;
    private boolean useConnectionProvider;

    private XMPPServer server = XMPPServer.getInstance();  

    /**
     * Constructor of the JDBCGroupProvider class.
     */
    public JDBCGroupProvider() {
        // Convert XML based provider setup to Database based
        JiveGlobals.migrateProperty("jdbcProvider.driver");
        JiveGlobals.migrateProperty("jdbcProvider.connectionString");
        JiveGlobals.migrateProperty("jdbcGroupProvider.groupCountSQL");
        JiveGlobals.migrateProperty("jdbcGroupProvider.allGroupsSQL");
        JiveGlobals.migrateProperty("jdbcGroupProvider.userGroupsSQL");
        JiveGlobals.migrateProperty("jdbcGroupProvider.descriptionSQL");
        JiveGlobals.migrateProperty("jdbcGroupProvider.loadMembersSQL");
        JiveGlobals.migrateProperty("jdbcGroupProvider.loadAdminsSQL");

        useConnectionProvider = JiveGlobals.getBooleanProperty("jdbcGroupProvider.useConnectionProvider");

        if (!useConnectionProvider) {
            // Load the JDBC driver and connection string.
            String jdbcDriver = JiveGlobals.getProperty("jdbcProvider.driver");
            try {
                Class.forName(jdbcDriver).newInstance();
            }
            catch (Exception e) {
                Log.error("Unable to load JDBC driver: " + jdbcDriver, e);
                return;
            }
            connectionString = JiveGlobals.getProperty("jdbcProvider.connectionString");
        }

        // Load SQL statements
        groupCountSQL = JiveGlobals.getProperty("jdbcGroupProvider.groupCountSQL");
        allGroupsSQL = JiveGlobals.getProperty("jdbcGroupProvider.allGroupsSQL");
        userGroupsSQL = JiveGlobals.getProperty("jdbcGroupProvider.userGroupsSQL");
        descriptionSQL = JiveGlobals.getProperty("jdbcGroupProvider.descriptionSQL");
        loadMembersSQL = JiveGlobals.getProperty("jdbcGroupProvider.loadMembersSQL");
        loadAdminsSQL = JiveGlobals.getProperty("jdbcGroupProvider.loadAdminsSQL");
    }

    /**
     * Always throws an UnsupportedOperationException because JDBC groups are read-only.
     *
     * @param name the name of the group to create.
     * @throws UnsupportedOperationException when called.
     */
    public Group createGroup(String name) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Always throws an UnsupportedOperationException because JDBC groups are read-only.
     *
     * @param name the name of the group to delete
     * @throws UnsupportedOperationException when called.
     */
    public void deleteGroup(String name) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    private Connection getConnection() throws SQLException {
        if (useConnectionProvider)
            return DbConnectionManager.getConnection();
        return DriverManager.getConnection(connectionString);
    }

    public Group getGroup(String name) throws GroupNotFoundException {
        String description = null;

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = getConnection();
            pstmt = con.prepareStatement(descriptionSQL);
            pstmt.setString(1, name);
            rs = pstmt.executeQuery();
            if (!rs.next()) {
                throw new GroupNotFoundException("Group with name "
                        + name + " not found.");
            }
            description = rs.getString(1);
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        Collection<JID> members = getMembers(name, false);
        Collection<JID> administrators = getMembers(name, true);
        return new Group(name, description, members, administrators);
    }

    private Collection<JID> getMembers(String groupName, boolean adminsOnly) {
        List<JID> members = new ArrayList<JID>();

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = getConnection();
            if (adminsOnly) {
                if (loadAdminsSQL == null) {
                    return members;
                }
                pstmt = con.prepareStatement(loadAdminsSQL);
            }
            else {
                pstmt = con.prepareStatement(loadMembersSQL);
            }

            pstmt.setString(1, groupName);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                String user = rs.getString(1);
                if (user != null) {
                    JID userJID;
                    if (user.contains("@")) {
                        userJID = new JID(user);
                    }
                    else {
                        userJID = server.createJID(user, null); 
                    }
                    members.add(userJID);
                }
            }
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return members;
    }

    /**
     * Always throws an UnsupportedOperationException because JDBC groups are read-only.
     *
     * @param oldName the current name of the group.
     * @param newName the desired new name of the group.
     * @throws UnsupportedOperationException when called.
     */
    public void setName(String oldName, String newName) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Always throws an UnsupportedOperationException because JDBC groups are read-only.
     *
     * @param name the group name.
     * @param description the group description.
     * @throws UnsupportedOperationException when called.
     */
    public void setDescription(String name, String description)
            throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    public int getGroupCount() {
        int count = 0;
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = getConnection();
            pstmt = con.prepareStatement(groupCountSQL);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                count = rs.getInt(1);
            }
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return count;
    }

    public Collection<String> getSharedGroupsNames() {
        // Get the list of shared groups from the database
        return Group.getSharedGroupsNames();
    }

    public Collection<String> getGroupNames() {
        List<String> groupNames = new ArrayList<String>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = getConnection();
            pstmt = con.prepareStatement(allGroupsSQL);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                groupNames.add(rs.getString(1));
            }
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return groupNames;
    }

    public Collection<String> getGroupNames(int start, int num) {
        List<String> groupNames = new ArrayList<String>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = getConnection();
            pstmt = DbConnectionManager.createScrollablePreparedStatement(con, allGroupsSQL);
            rs = pstmt.executeQuery();
            DbConnectionManager.scrollResultSet(rs, start);
            int count = 0;
            while (rs.next() && count < num) {
                groupNames.add(rs.getString(1));
                count++;
            }
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return groupNames;
    }

    public Collection<String> getGroupNames(JID user) {
        List<String> groupNames = new ArrayList<String>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = getConnection();
            pstmt = con.prepareStatement(userGroupsSQL);
            pstmt.setString(1, server.isLocal(user) ? user.getNode() : user.toString());
            rs = pstmt.executeQuery();
            while (rs.next()) {
                groupNames.add(rs.getString(1));
            }
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return groupNames;
    }

    /**
     * Always throws an UnsupportedOperationException because JDBC groups are read-only.
     *
     * @param groupName name of a group.
     * @param user the JID of the user to add
     * @param administrator true if is an administrator.
     * @throws UnsupportedOperationException when called.
     */
    public void addMember(String groupName, JID user, boolean administrator)
            throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException();
    }

    /**
     * Always throws an UnsupportedOperationException because JDBC groups are read-only.
     *
     * @param groupName the naame of a group.
     * @param user the JID of the user with new privileges
     * @param administrator true if is an administrator.
     * @throws UnsupportedOperationException when called.
     */
    public void updateMember(String groupName, JID user, boolean administrator)
            throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException();
    }

    /**
     * Always throws an UnsupportedOperationException because JDBC groups are read-only.
     *
     * @param groupName the name of a group.
     * @param user the JID of the user to delete.
     * @throws UnsupportedOperationException when called.
     */
    public void deleteMember(String groupName, JID user)
            throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException();
    }

    /**
     * Always returns true because JDBC groups are read-only.
     *
     * @return true because all JDBC functions are read-only.
     */
    public boolean isReadOnly() {
        return true;
    }

    public Collection<String> search(String query) {
        return Collections.emptyList();
    }

    public Collection<String> search(String query, int startIndex, int numResults) {
        return Collections.emptyList();
    }

    public boolean isSearchSupported() {
        return false;
    }
}
