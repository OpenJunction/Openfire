<%--
  -	$Revision: 10535 $
  -	$Date: 2008-06-16 10:47:21 -0700 (Mon, 16 Jun 2008) $
  -
  - Copyright (C) 2004-2008 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution, or a commercial license
  - agreement with Jive.
--%>

<%@ page import="org.jivesoftware.util.*,
                 org.jivesoftware.openfire.muc.MUCRoom,
                 java.net.URLEncoder"
    errorPage="error.jsp"
%>
<%@ page import="org.xmpp.packet.JID" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<%  // Get parameters //
    boolean cancel = request.getParameter("cancel") != null;
    boolean delete = request.getParameter("delete") != null;
    JID roomJID = new JID(ParamUtils.getParameter(request,"roomJID"));
    String alternateJID = ParamUtils.getParameter(request,"alternateJID");
    String reason = ParamUtils.getParameter(request,"reason");
    String roomName = roomJID.getNode();

    // Handle a cancel
    if (cancel) {
        response.sendRedirect("muc-room-summary.jsp?roomJID="+URLEncoder.encode(roomJID.toBareJID(), "UTF-8"));
        return;
    }

    // Load the room object
    MUCRoom room = webManager.getMultiUserChatManager().getMultiUserChatService(roomJID).getChatRoom(roomName);

    // Handle a room delete:
    if (delete) {
        // Delete the room
        if (room !=  null) {
            // If the room still exists then destroy it
            room.destroyRoom(alternateJID, reason);
            // Log the event
            webManager.logEvent("destroyed MUC room "+roomName, "reason = "+reason+"\nalt jid = "+alternateJID);
        }
        // Done, so redirect
        response.sendRedirect("muc-room-summary.jsp?roomJID="+URLEncoder.encode(roomJID.toBareJID(), "UTF-8")+"&deletesuccess=true");
        return;
    }
%>

<html>
    <head>
        <title><fmt:message key="muc.room.delete.title"/></title>
        <meta name="subPageID" content="muc-room-delete"/>
        <meta name="extraParams" content="<%= "roomJID="+URLEncoder.encode(roomJID.toBareJID(), "UTF-8") %>"/>
        <meta name="helpPage" content="delete_a_group_chat_room.html"/>
    </head>
    <body>

<p>
<fmt:message key="muc.room.delete.info" />
<b><a href="muc-room-edit-form.jsp?roomJID=<%= URLEncoder.encode(room.getJID().toBareJID(), "UTF-8") %>"><%= room.getJID().toBareJID() %></a></b>
<fmt:message key="muc.room.delete.detail" />
</p>

<form action="muc-room-delete.jsp">
<input type="hidden" name="roomJID" value="<%= roomJID.toBareJID() %>">

<fieldset>
    <legend><fmt:message key="muc.room.delete.destructon_title" /></legend>
    <div>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr>
            <td class="c1">
                <fmt:message key="muc.room.delete.room_id" />
            </td>
            <td>
                <%= room.getJID().toBareJID() %>
            </td>
        </tr>
        <tr>
            <td class="c1">
                <fmt:message key="muc.room.delete.reason" />
            </td>
            <td>
                <input type="text" size="50" maxlength="150" name="reason">
            </td>
        </tr>
        <tr>
            <td class="c1">
                <fmt:message key="muc.room.delete.alternate_address" />
            </td>
            <td>
                <input type="text" size="30" maxlength="150" name="alternateJID">
            </td>
        </tr>
    </tbody>
    </table>
    </div>
</fieldset>

<br><br>

<input type="submit" name="delete" value="<fmt:message key="muc.room.delete.destroy_room" />">
<input type="submit" name="cancel" value="<fmt:message key="global.cancel" />">
</form>

    </body>
</html>