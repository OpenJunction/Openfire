/**
 * $RCSfile$
 * $Revision: 569 $
 * $Date: 2004-12-01 15:31:18 -0300 (Wed, 01 Dec 2004) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.muc.spi;

import java.util.Date;

import org.jivesoftware.openfire.muc.MUCRoom;
import org.xmpp.packet.Message;
import org.xmpp.packet.JID;

/**
 * Represents an entry in the conversation log of a room. An entry basically obtains the necessary
 * information to log from the message adding a timestamp of when the message was sent to the room.
 * 
 * @author Gaston Dombiak
 */
class ConversationLogEntry {

    private Date date;

    private String subject;

    private String body;

    private JID sender;
    
    private String nickname;
    
    private long roomID;

    /**
     * Creates a new ConversationLogEntry that registers that a given message was sent to a given
     * room on a given date.
     * 
     * @param date the date when the message was sent to the room.
     * @param room the room that received the message.
     * @param message the message to log as part of the conversation in the room.
     * @param sender the real XMPPAddress of the sender (e.g. john@example.org). 
     */
    public ConversationLogEntry(Date date, MUCRoom room, Message message, JID sender) {
        this.date = date;
        this.subject = message.getSubject();
        this.body = message.getBody();
        this.sender = sender;
        this.roomID = room.getID();
        this.nickname = message.getFrom().getResource();
    }

    /**
     * Returns the body of the logged message.
     * 
     * @return the body of the logged message.
     */
    public String getBody() {
        return body;
    }

    /**
     * Returns the XMPP address of the logged message's sender.
     * 
     * @return the XMPP address of the logged message's sender.
     */
    public JID getSender() {
        return sender;
    }

    /**
     * Returns the nickname that the user had at the moment that the message was sent to the room.
     * 
     * @return the nickname that the user had at the moment that the message was sent to the room.
     */
    public String getNickname() {
        return nickname;
    }

    /**
     * Returns the subject of the logged message.
     * 
     * @return the subject of the logged message.
     */
    public String getSubject() {
        return subject;
    }

    /**
     * Returns the date when the logged message was sent to the room.
     * 
     * @return the date when the logged message was sent to the room.
     */
    public Date getDate() {
        return date;
    }

    /**
     * Returns the ID of the room where the message was sent.
     * 
     * @return the ID of the room where the message was sent.
     */
    public long getRoomID() {
        return roomID;
    }

}