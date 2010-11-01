/**
 * $RCSfile$
 * $Revision: 3158 $
 * $Date: 2005-12-04 22:55:49 -0300 (Sun, 04 Dec 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.muc.spi;

import org.dom4j.Element;
import org.jivesoftware.database.SequenceManager;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.cluster.NodeID;
import org.jivesoftware.openfire.muc.*;
import org.jivesoftware.openfire.muc.cluster.*;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.NotFoundException;
import org.jivesoftware.util.cache.CacheFactory;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.xmpp.packet.*;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Implementation of a chatroom that is being hosted by this JVM. A LocalMUCRoom could represent
 * a persistent room which means that its configuration will be maintained in synch with its
 * representation in the database.<p>
 *
 * When running in a cluster each cluster node will have its own copy of local rooms. Persistent
 * rooms will be loaded by each cluster node when starting up. Not persistent rooms will be copied
 * from the senior cluster member. All room occupants will be copied from the senior cluster member
 * too.
 * 
 * @author Gaston Dombiak
 */
public class LocalMUCRoom implements MUCRoom {

    /**
     * The service hosting the room.
     */
    private MultiUserChatService mucService;

    /**
     * The occupants of the room accessible by the occupants nickname.
     */
    private Map<String,MUCRole> occupants = new ConcurrentHashMap<String, MUCRole>();

    /**
     * The occupants of the room accessible by the occupants bare JID.
     */
    private Map<String, List<MUCRole>> occupantsByBareJID = new ConcurrentHashMap<String, List<MUCRole>>();

    /**
     * The occupants of the room accessible by the occupants full JID.
     */
    private Map<JID, MUCRole> occupantsByFullJID = new ConcurrentHashMap<JID, MUCRole>();

    /**
     * The name of the room.
     */
    private String name;

    /**
     * A lock to protect the room occupants.
     */
    ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * The role of the room itself.
     */
    private MUCRole role = new RoomRole(this);

    /**
     * The router used to send packets for the room.
     */
    private PacketRouter router;

    /**
     * The start time of the chat.
     */
    long startTime;

    /**
     * The end time of the chat.
     */
    long endTime;

    /**
     * After a room has been destroyed it may remain in memory but it won't be possible to use it.
     * When a room is destroyed it is immediately removed from the MultiUserChatService but it's
     * possible that while the room was being destroyed it was being used by another thread so we
     * need to protect the room under these rare circumstances.
     */
    boolean isDestroyed = false;

    /**
     * ChatRoomHistory object.
     */
    private MUCRoomHistory roomHistory;

    /**
     * Time when the room was locked. A value of zero means that the room is unlocked.
     */
    private long lockedTime;

    /**
     * List of chatroom's owner. The list contains only bare jid.
     */
    List<String> owners = new CopyOnWriteArrayList<String>();

    /**
     * List of chatroom's admin. The list contains only bare jid.
     */
    List<String> admins = new CopyOnWriteArrayList<String>();

    /**
     * List of chatroom's members. The list contains only bare jid.
     */
    private Map<String, String> members = new ConcurrentHashMap<String,String>();

    /**
     * List of chatroom's outcast. The list contains only bare jid of not allowed users.
     */
    private List<String> outcasts = new CopyOnWriteArrayList<String>();

    /**
     * The natural language name of the room.
     */
    private String naturalLanguageName;

    /**
     * Description of the room. The owner can change the description using the room configuration
     * form.
     */
    private String description;

    /**
     * Indicates if occupants are allowed to change the subject of the room.
     */
    private boolean canOccupantsChangeSubject;

    /**
     * Maximum number of occupants that could be present in the room. If the limit's been reached
     * and a user tries to join, a not-allowed error will be returned.
     */
    private int maxUsers;

    /**
     * List of roles of which presence will be broadcasted to the rest of the occupants. This
     * feature is useful for implementing "invisible" occupants.
     */
    private List<String> rolesToBroadcastPresence = new ArrayList<String>();

    /**
     * A public room means that the room is searchable and visible. This means that the room can be
     * located using disco requests.
     */
    private boolean publicRoom;

    /**
     * Persistent rooms are saved to the database to make sure that rooms configurations can be
     * restored in case the server goes down.
     */
    private boolean persistent;

    /**
     * Moderated rooms enable only participants to speak. Users that join the room and aren't
     * participants can't speak (they are just visitors).
     */
    private boolean moderated;

    /**
     * A room is considered members-only if an invitation is required in order to enter the room.
     * Any user that is not a member of the room won't be able to join the room unless the user
     * decides to register with the room (thus becoming a member).
     */
    private boolean membersOnly;

    /**
     * Some rooms may restrict the occupants that are able to send invitations. Sending an
     * invitation in a members-only room adds the invitee to the members list.
     */
    private boolean canOccupantsInvite;

    /**
     * The password that every occupant should provide in order to enter the room.
     */
    private String password = null;

    /**
     * Every presence packet can include the JID of every occupant unless the owner deactives this
     * configuration.
     */
    private boolean canAnyoneDiscoverJID;

    /**
     * Enables the logging of the conversation. The conversation in the room will be saved to the
     * database.
     */
    private boolean logEnabled;

    /**
     * Enables the logging of the conversation. The conversation in the room will be saved to the
     * database.
     */
    private boolean loginRestrictedToNickname;

    /**
     * Enables the logging of the conversation. The conversation in the room will be saved to the
     * database.
     */
    private boolean canChangeNickname;

    /**
     * Enables the logging of the conversation. The conversation in the room will be saved to the
     * database.
     */
    private boolean registrationEnabled;

    /**
     * Internal component that handles IQ packets sent by the room owners.
     */
    private IQOwnerHandler iqOwnerHandler;

    /**
     * Internal component that handles IQ packets sent by moderators, admins and owners.
     */
    private IQAdminHandler iqAdminHandler;

    /**
     * The last known subject of the room. This information is used to respond disco requests. The
     * MUCRoomHistory class holds the history of the room together with the last message that set
     * the room's subject.
     */
    private String subject = "";

    /**
     * The ID of the room. If the room is temporary and does not log its conversation then the value
     * will always be -1. Otherwise a value will be obtained from the database.
     */
    private long roomID = -1;

    /**
     * The date when the room was created.
     */
    private Date creationDate;

    /**
     * The last date when the room's configuration was modified.
     */
    private Date modificationDate;

    /**
     * The date when the last occupant left the room. A null value means that there are occupants
     * in the room at the moment.
     */
    private Date emptyDate;

    /**
     * Indicates if the room is present in the database.
     */
    private boolean savedToDB = false;

    /**
     * Do not use this constructor. It was added to implement the Externalizable
     * interface required to work inside of a cluster.
     */
    public LocalMUCRoom() {
    }

    /**
     * Create a new chat room.
     *
     * @param chatservice the service hosting the room.
     * @param roomname the name of the room.
     * @param packetRouter the router for sending packets from the room.
     */
    LocalMUCRoom(MultiUserChatService chatservice, String roomname, PacketRouter packetRouter) {
        this.mucService = chatservice;
        this.name = roomname;
        this.naturalLanguageName = roomname;
        this.description = roomname;
        this.router = packetRouter;
        this.startTime = System.currentTimeMillis();
        this.creationDate = new Date(startTime);
        this.modificationDate = new Date(startTime);
        this.emptyDate = new Date(startTime);
        this.canOccupantsChangeSubject = MUCPersistenceManager.getBooleanProperty(mucService.getServiceName(), "room.canOccupantsChangeSubject", false);
        this.maxUsers = MUCPersistenceManager.getIntProperty(mucService.getServiceName(), "room.maxUsers", 30);
        this.publicRoom = MUCPersistenceManager.getBooleanProperty(mucService.getServiceName(), "room.publicRoom", true);
        this.persistent = MUCPersistenceManager.getBooleanProperty(mucService.getServiceName(), "room.persistent", false);
        this.moderated = MUCPersistenceManager.getBooleanProperty(mucService.getServiceName(), "room.moderated", false);
        this.membersOnly = MUCPersistenceManager.getBooleanProperty(mucService.getServiceName(), "room.membersOnly", false);
        this.canOccupantsInvite = MUCPersistenceManager.getBooleanProperty(mucService.getServiceName(), "room.canOccupantsInvite", false);
        this.canAnyoneDiscoverJID = MUCPersistenceManager.getBooleanProperty(mucService.getServiceName(), "room.canAnyoneDiscoverJID", true);
        this.logEnabled = MUCPersistenceManager.getBooleanProperty(mucService.getServiceName(), "room.logEnabled", false);
        this.loginRestrictedToNickname = MUCPersistenceManager.getBooleanProperty(mucService.getServiceName(), "room.loginRestrictedToNickname", false);
        this.canChangeNickname = MUCPersistenceManager.getBooleanProperty(mucService.getServiceName(), "room.canChangeNickname", true);
        this.registrationEnabled = MUCPersistenceManager.getBooleanProperty(mucService.getServiceName(), "room.registrationEnabled", true);
        // TODO Allow to set the history strategy from the configuration form?
        roomHistory = new MUCRoomHistory(this, new HistoryStrategy(mucService.getHistoryStrategy()));
        this.iqOwnerHandler = new IQOwnerHandler(this, packetRouter);
        this.iqAdminHandler = new IQAdminHandler(this, packetRouter);
        // No one can join the room except the room's owner
        this.lockedTime = startTime;
        // Set the default roles for which presence is broadcast
        rolesToBroadcastPresence.add("moderator");
        rolesToBroadcastPresence.add("participant");
        rolesToBroadcastPresence.add("visitor");
    }

    public String getName() {
        return name;
    }

    public JID getJID() {
        return new JID(getName(), getMUCService().getServiceDomain(), null);
    }

    public MultiUserChatService getMUCService() {
        return mucService;
    }

    public void setMUCService(MultiUserChatService service) {
        this.mucService = service;
    }

    public long getID() {
        if (isPersistent() || isLogEnabled()) {
            if (roomID == -1) {
                roomID = SequenceManager.nextID(JiveConstants.MUC_ROOM);
            }
        }
        return roomID;
    }

    public void setID(long roomID) {
        this.roomID = roomID;
    }

    public Date getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

    public Date getModificationDate() {
        return modificationDate;
    }

    public void setModificationDate(Date modificationDate) {
        this.modificationDate = modificationDate;
    }

    public void setEmptyDate(Date emptyDate) {
        // Do nothing if old value is same as new value
        if (this.emptyDate == emptyDate) {
            return;
        }
        this.emptyDate = emptyDate;
        MUCPersistenceManager.updateRoomEmptyDate(this);
    }

    public Date getEmptyDate() {
        return this.emptyDate;
    }

    public MUCRole getRole() {
        return role;
    }

    public MUCRole getOccupant(String nickname) throws UserNotFoundException {
        if (nickname == null) {
             throw new UserNotFoundException();
        }
        MUCRole role = occupants.get(nickname.toLowerCase());
        if (role != null) {
            return role;
        }
        throw new UserNotFoundException();
    }

    public List<MUCRole> getOccupantsByBareJID(String jid) throws UserNotFoundException {
        List<MUCRole> roles = occupantsByBareJID.get(jid);
        if (roles != null && !roles.isEmpty()) {
            return Collections.unmodifiableList(roles);
        }
        throw new UserNotFoundException();
    }

    public MUCRole getOccupantByFullJID(JID jid) {
        MUCRole role = occupantsByFullJID.get(jid);
        if (role != null) {
            return role;
        }
        return null;
    }

    public Collection<MUCRole> getOccupants() {
        return Collections.unmodifiableCollection(occupants.values());
    }

    public int getOccupantsCount() {
        return occupants.size();
    }

    public boolean hasOccupant(String nickname) {
        return occupants.containsKey(nickname.toLowerCase());
    }

    public String getReservedNickname(String bareJID) {
        String answer = members.get(bareJID);
        if (answer == null || answer.trim().length() == 0) {
            return null;
        }
        return answer;
    }

    public MUCRole.Affiliation getAffiliation(String bareJID) {
        if (owners.contains(bareJID)) {
            return MUCRole.Affiliation.owner;
        }
        else if (admins.contains(bareJID)) {
            return MUCRole.Affiliation.admin;
        }
        else if (members.containsKey(bareJID)) {
            return MUCRole.Affiliation.member;
        }
        else if (outcasts.contains(bareJID)) {
            return MUCRole.Affiliation.outcast;
        }
        return MUCRole.Affiliation.none;
    }

    public LocalMUCRole joinRoom(String nickname, String password, HistoryRequest historyRequest,
            LocalMUCUser user, Presence presence) throws UnauthorizedException,
            UserAlreadyExistsException, RoomLockedException, ForbiddenException,
            RegistrationRequiredException, ConflictException, ServiceUnavailableException,
            NotAcceptableException {
        if (((MultiUserChatServiceImpl)mucService).getMUCDelegate() != null) {
            if (!((MultiUserChatServiceImpl)mucService).getMUCDelegate().joiningRoom(this, user.getAddress())) {
                // Delegate said no, reject join.
                throw new UnauthorizedException();
            }
        }
        LocalMUCRole joinRole = null;
        lock.writeLock().lock();
        try {
            // If the room has a limit of max user then check if the limit has been reached
            if (isDestroyed || (getMaxUsers() > 0 && getOccupantsCount() >= getMaxUsers())) {
                throw new ServiceUnavailableException();
            }
            boolean isOwner = owners.contains(user.getAddress().toBareJID());
            // If the room is locked and this user is not an owner raise a RoomLocked exception
            if (isLocked()) {
                if (!isOwner) {
                    throw new RoomLockedException();
                }
            }
            // Check if the nickname is already used in the room
            if (occupants.containsKey(nickname.toLowerCase())) {
                if (occupants.get(nickname.toLowerCase()).getUserAddress().toBareJID().equals(user.getAddress().toBareJID())) {
                    // Nickname exists in room, and belongs to this user, pretend to kick the previous instance.
                    // The previous instance will see that they are disconnected, and the new instance will
                    // "take over" the previous role.  Participants in the room shouldn't notice anything
                    // has occurred.
                    String reason = "Your account signed into this chatroom with the same nickname from another location.";
                    Presence updatedPresence = new Presence(Presence.Type.unavailable);
                    updatedPresence.setFrom(occupants.get(nickname.toLowerCase()).getRoleAddress());
                    updatedPresence.setTo(occupants.get(nickname.toLowerCase()).getUserAddress());
                    Element frag = updatedPresence.addChildElement(
                            "x", "http://jabber.org/protocol/muc#user");

                    // Set the person who performed the kick ("you" effectively)
                    frag.addElement("item").addElement("actor").setText(user.getAddress().toString());
                    // Add the reason why the user was kicked
                    frag.element("item").addElement("reason").setText(reason);
                    // Add the status code 307 that indicates that the user was kicked
                    frag.addElement("status").addAttribute("code", "307");

                    router.route(updatedPresence);
                }
                else {
                    // Nickname is already used, and not by the same JID
                    throw new UserAlreadyExistsException();
                }
            }
            // If the room is password protected and the provided password is incorrect raise a
            // Unauthorized exception
            if (isPasswordProtected()) {
                if (password == null || !password.equals(getPassword())) {
                    throw new UnauthorizedException();
                }
            }
            // If another user attempts to join the room with a nickname reserved by the first user
            // raise a ConflictException
            if (members.containsValue(nickname)) {
                if (!nickname.equals(members.get(user.getAddress().toBareJID()))) {
                    throw new ConflictException();
                }
            }
            if (isLoginRestrictedToNickname()) {
                String reservedNickname = members.get(user.getAddress().toBareJID());
                if (reservedNickname != null && !nickname.equals(reservedNickname)) {
                    throw new NotAcceptableException();
                }
            }

            // Set the corresponding role based on the user's affiliation
            MUCRole.Role role;
            MUCRole.Affiliation affiliation;
            if (isOwner) {
                // The user is an owner. Set the role and affiliation accordingly.
                role = MUCRole.Role.moderator;
                affiliation = MUCRole.Affiliation.owner;
            }
            else if (mucService.getSysadmins().contains(user.getAddress().toBareJID())) {
                // The user is a system administrator of the MUC service. Treat him as an owner
                // although he won't appear in the list of owners
                role = MUCRole.Role.moderator;
                affiliation = MUCRole.Affiliation.owner;
            }
            else if (admins.contains(user.getAddress().toBareJID())) {
                // The user is an admin. Set the role and affiliation accordingly.
                role = MUCRole.Role.moderator;
                affiliation = MUCRole.Affiliation.admin;
            }
            else if (members.containsKey(user.getAddress().toBareJID())) {
                // The user is a member. Set the role and affiliation accordingly.
                role = MUCRole.Role.participant;
                affiliation = MUCRole.Affiliation.member;
            }
            else if (outcasts.contains(user.getAddress().toBareJID())) {
                // The user is an outcast. Raise a "Forbidden" exception.
                throw new ForbiddenException();
            }
            else {
                // The user has no affiliation (i.e. NONE). Set the role accordingly.
                if (isMembersOnly()) {
                    // The room is members-only and the user is not a member. Raise a
                    // "Registration Required" exception.
                    throw new RegistrationRequiredException();
                }
                role = (isModerated() ? MUCRole.Role.visitor : MUCRole.Role.participant);
                affiliation = MUCRole.Affiliation.none;
            }
            // Create a new role for this user in this room
            joinRole = new LocalMUCRole(mucService, this, nickname, role, affiliation, user, presence, router);
            // Add the new user as an occupant of this room
            occupants.put(nickname.toLowerCase(), joinRole);
            // Update the tables of occupants based on the bare and full JID
            List<MUCRole> list = occupantsByBareJID.get(user.getAddress().toBareJID());
            if (list == null) {
                list = new ArrayList<MUCRole>();
                occupantsByBareJID.put(user.getAddress().toBareJID(), list);
            }
            list.add(joinRole);
            occupantsByFullJID.put(user.getAddress(), joinRole);
        }
        finally {
            lock.writeLock().unlock();
        }
        // Notify other cluster nodes that a new occupant joined the room
        CacheFactory.doClusterTask(new OccupantAddedEvent(this, joinRole));

        // Send presence of existing occupants to new occupant
        sendInitialPresences(joinRole);
        // It is assumed that the room is new based on the fact that it's locked and
        // that it was locked when it was created.
        boolean isRoomNew = isLocked() && creationDate.getTime() == lockedTime;
        try {
            // Send the presence of this new occupant to existing occupants
            Presence joinPresence = joinRole.getPresence().createCopy();
            if (isRoomNew) {
                Element frag = joinPresence.getChildElement("x", "http://jabber.org/protocol/muc#user");
                frag.addElement("status").addAttribute("code", "201");
            }
            broadcastPresence(joinPresence);
        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
        // If the room has just been created send the "room locked until configuration is
        // confirmed" message
        if (isRoomNew) {
            Message message = new Message();
            message.setType(Message.Type.groupchat);
            message.setBody(LocaleUtils.getLocalizedString("muc.new"));
            message.setFrom(role.getRoleAddress());
            joinRole.send(message);
        }
        else if (isLocked()) {
            // Warn the owner that the room is locked but it's not new
            Message message = new Message();
            message.setType(Message.Type.groupchat);
            message.setBody(LocaleUtils.getLocalizedString("muc.locked"));
            message.setFrom(role.getRoleAddress());
            joinRole.send(message);
        }
        else if (canAnyoneDiscoverJID()) {
            // Warn the new occupant that the room is non-anonymous (i.e. his JID will be
            // public)
            Message message = new Message();
            message.setType(Message.Type.groupchat);
            message.setBody(LocaleUtils.getLocalizedString("muc.warnnonanonymous"));
            message.setFrom(role.getRoleAddress());
            Element frag = message.addChildElement("x", "http://jabber.org/protocol/muc#user");
            frag.addElement("status").addAttribute("code", "100");
            joinRole.send(message);
        }
        if (historyRequest == null) {
            Iterator history = roomHistory.getMessageHistory();
            while (history.hasNext()) {
                joinRole.send((Message) history.next());
            }
        }
        else {
            historyRequest.sendHistory(joinRole, roomHistory);
        }
        // Update the date when the last occupant left the room
        setEmptyDate(null);
        // Fire event that occupant joined the room
        MUCEventDispatcher.occupantJoined(getRole().getRoleAddress(), user.getAddress(), joinRole.getNickname());
        return joinRole;
    }

    /**
     * Sends presence of existing occupants to new occupant.
     *
     * @param joinRole the role of the new occupant in the room.
     */
    private void sendInitialPresences(LocalMUCRole joinRole) {
        for (MUCRole occupant : occupants.values()) {
            if (occupant == joinRole) {
                continue;
            }
            Presence occupantPresence = occupant.getPresence();
            // Skip to the next occupant if we cannot send presence of this occupant
            if (hasToCheckRoleToBroadcastPresence()) {
                Element frag = occupantPresence.getChildElement("x",
                        "http://jabber.org/protocol/muc#user");
                // Check if we can broadcast the presence for this role
                if (!canBroadcastPresence(frag.element("item").attributeValue("role"))) {
                    continue;
                }
            }
            // Don't include the occupant's JID if the room is semi-anon and the new occupant
            // is not a moderator
            if (!canAnyoneDiscoverJID() && MUCRole.Role.moderator != joinRole.getRole()) {
                occupantPresence = occupantPresence.createCopy();
                Element frag = occupantPresence.getChildElement("x",
                        "http://jabber.org/protocol/muc#user");
                frag.element("item").addAttribute("jid", null);
            }
            joinRole.send(occupantPresence);
        }
    }

    public void occupantAdded(OccupantAddedEvent event) {
        // Do not add new occupant with one with same nickname already exists
        if (occupants.containsKey(event.getNickname().toLowerCase())) {
            // TODO Handle conflict of nicknames
            return;
        }
        // Create a proxy for the occupant that joined the room from another cluster node
        RemoteMUCRole joinRole = new RemoteMUCRole(mucService, event);
        // Add the new user as an occupant of this room
        occupants.put(event.getNickname().toLowerCase(), joinRole);
        // Update the tables of occupants based on the bare and full JID
        List<MUCRole> list = occupantsByBareJID.get(event.getUserAddress().toBareJID());
        if (list == null) {
            list = new ArrayList<MUCRole>();
            occupantsByBareJID.put(event.getUserAddress().toBareJID(), list);
        }
        list.add(joinRole);
        occupantsByFullJID.put(event.getUserAddress(), joinRole);

        // Update the date when the last occupant left the room
        setEmptyDate(null);
        if (event.isOriginator()) {
            // Fire event that occupant joined the room
            MUCEventDispatcher.occupantJoined(getRole().getRoleAddress(), event.getUserAddress(), joinRole.getNickname());
        }
        // Check if we need to send presences of the new occupant to occupants hosted by this JVM
        if (event.isSendPresence()) {
            for (MUCRole occupant : occupants.values()) {
                if (occupant.isLocal()) {
                    occupant.send(event.getPresence().createCopy());
                }
            }
        }
    }

    public void leaveRoom(MUCRole leaveRole) {
        if (leaveRole.isLocal()) {
            // Ask other cluster nodes to remove occupant from room
            OccupantLeftEvent event = new OccupantLeftEvent(this, leaveRole);
            CacheFactory.doClusterTask(event);
        }

        try {
            Presence presence = leaveRole.getPresence().createCopy();
            presence.setType(Presence.Type.unavailable);
            presence.setStatus(null);
            // Change (or add) presence information about roles and affiliations
            Element childElement = presence.getChildElement("x", "http://jabber.org/protocol/muc#user");
            if (childElement == null) {
                childElement = presence.addChildElement("x", "http://jabber.org/protocol/muc#user");
            }
            Element item = childElement.element("item");
            if (item == null) {
                item = childElement.addElement("item");
            }
            item.addAttribute("role", "none");
            // Inform the leaving user that he/she has left the room
            leaveRole.send(presence);
            // Inform the rest of the room occupants that the user has left the room
            broadcastPresence(presence);
        }
        catch (Exception e) {
            Log.error(e);
        }

        // Remove occupant from room and destroy room if empty and not persistent
        OccupantLeftEvent event = new OccupantLeftEvent(this, leaveRole);
        event.setOriginator(true);
        event.run();
    }

    public void leaveRoom(OccupantLeftEvent event) {
        MUCRole leaveRole = event.getRole();
        if (leaveRole == null) {
            return;
        }
        lock.writeLock().lock();
        try {
            // Removes the role from the room
            removeOccupantRole(leaveRole, event.isOriginator());

            // TODO Implement this: If the room owner becomes unavailable for any reason before
            // submitting the form (e.g., a lost connection), the service will receive a presence
            // stanza of type "unavailable" from the owner to the room@service/nick or room@service
            // (or both). The service MUST then destroy the room, sending a presence stanza of type
            // "unavailable" from the room to the owner including a <destroy/> element and reason
            // (if provided) as defined under the "Destroying a Room" use case.

            // Remove the room from the service only if there are no more occupants and the room is
            // not persistent
            if (occupants.isEmpty() && !isPersistent()) {
                endTime = System.currentTimeMillis();
                if (event.isOriginator()) {
                    mucService.removeChatRoom(name);
                    // Fire event that the room has been destroyed
                    MUCEventDispatcher.roomDestroyed(getRole().getRoleAddress());
                }
            }
            if (occupants.isEmpty()) {
                // Update the date when the last occupant left the room
                setEmptyDate(new Date());
            }
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Removes the role of the occupant from all the internal occupants collections. The role will
     * also be removed from the user's roles.
     *
     * @param leaveRole the role to remove.
     * @param originator true if this JVM is the one that originated the event.
     */
    private void removeOccupantRole(MUCRole leaveRole, boolean originator) {
        occupants.remove(leaveRole.getNickname().toLowerCase());

        JID userAddress = leaveRole.getUserAddress();
        // Notify the user that he/she is no longer in the room
        leaveRole.destroy();
        // Update the tables of occupants based on the bare and full JID
        List list = occupantsByBareJID.get(userAddress.toBareJID());
        if (list != null) {
            list.remove(leaveRole);
            if (list.isEmpty()) {
                occupantsByBareJID.remove(userAddress.toBareJID());
            }
        }
        occupantsByFullJID.remove(userAddress);
        if (originator) {
            // Fire event that occupant left the room
            MUCEventDispatcher.occupantLeft(getRole().getRoleAddress(), userAddress);
        }
    }

    public void destroyRoom(DestroyRoomRequest destroyRequest) {
        String alternateJID = destroyRequest.getAlternateJID();
        String reason = destroyRequest.getReason();
        MUCRole leaveRole;
        Collection<MUCRole> removedRoles = new ArrayList<MUCRole>();
        lock.writeLock().lock();
        try {
            boolean hasRemoteOccupants = false;
            // Remove each occupant
            for (String nickname: occupants.keySet()) {
                leaveRole = occupants.remove(nickname);

                if (leaveRole != null) {
                    // Add the removed occupant to the list of removed occupants. We are keeping a
                    // list of removed occupants to process later outside of the lock.
                    if (leaveRole.isLocal()) {
                        removedRoles.add(leaveRole);
                    }
                    else {
                        hasRemoteOccupants = true;
                    }
                    removeOccupantRole(leaveRole, destroyRequest.isOriginator());
                }
            }
            endTime = System.currentTimeMillis();
            // Set that the room has been destroyed
            isDestroyed = true;
            if (destroyRequest.isOriginator()) {
                if (hasRemoteOccupants) {
                    // Ask other cluster nodes to remove occupants since room is being destroyed
                    CacheFactory.doClusterTask(new DestroyRoomRequest(this, alternateJID, reason));
                }
                // Removes the room from the list of rooms hosted in the service
                mucService.removeChatRoom(name);
            }
        }
        finally {
            lock.writeLock().unlock();
        }
        // Send an unavailable presence to each removed occupant
        for (MUCRole removedRole : removedRoles) {
            try {
                // Send a presence stanza of type "unavailable" to the occupant
                Presence presence = createPresence(Presence.Type.unavailable);
                presence.setFrom(removedRole.getRoleAddress());

                // A fragment containing the x-extension for room destruction.
                Element fragment = presence.addChildElement("x",
                        "http://jabber.org/protocol/muc#user");
                Element item = fragment.addElement("item");
                item.addAttribute("affiliation", "none");
                item.addAttribute("role", "none");
                if (alternateJID != null && alternateJID.length() > 0) {
                    fragment.addElement("destroy").addAttribute("jid", alternateJID);
                }
                if (reason != null && reason.length() > 0) {
                    Element destroy = fragment.element("destroy");
                    if (destroy == null) {
                        destroy = fragment.addElement("destroy");
                    }
                    destroy.addElement("reason").setText(reason);
                }
                removedRole.send(presence);
            }
            catch (Exception e) {
                Log.error(e);
            }
        }
        if (destroyRequest.isOriginator()) {
            // Remove the room from the DB if the room was persistent
            MUCPersistenceManager.deleteFromDB(this);
            // Fire event that the room has been destroyed
            MUCEventDispatcher.roomDestroyed(getRole().getRoleAddress());
        }
    }

    public void destroyRoom(String alternateJID, String reason) {
        DestroyRoomRequest destroyRequest = new DestroyRoomRequest(this, alternateJID, reason);
        destroyRequest.setOriginator(true);
        destroyRequest.run();
    }

    public Presence createPresence(Presence.Type presenceType) throws UnauthorizedException {
        Presence presence = new Presence();
        presence.setType(presenceType);
        presence.setFrom(role.getRoleAddress());
        return presence;
    }

    public void serverBroadcast(String msg) {
        Message message = new Message();
        message.setType(Message.Type.groupchat);
        message.setBody(msg);
        message.setFrom(role.getRoleAddress());
        broadcast(message);
    }

    public void sendPublicMessage(Message message, MUCRole senderRole) throws ForbiddenException {
        // Check that if the room is moderated then the sender of the message has to have voice
        if (isModerated() && senderRole.getRole().compareTo(MUCRole.Role.participant) > 0) {
            throw new ForbiddenException();
        }
        // Send the message to all occupants
        message.setFrom(senderRole.getRoleAddress());
        send(message);
        // Fire event that message was receibed by the room
        MUCEventDispatcher.messageReceived(getRole().getRoleAddress(), senderRole.getUserAddress(),
                senderRole.getNickname(), message);
    }

    public void sendPrivatePacket(Packet packet, MUCRole senderRole) throws NotFoundException {
        String resource = packet.getTo().getResource();
        MUCRole occupant = occupants.get(resource.toLowerCase());
        if (occupant != null) {
            packet.setFrom(senderRole.getRoleAddress());
            occupant.send(packet);
        }
        else {
            throw new NotFoundException();
        }
    }

    public void send(Packet packet) {
        if (packet instanceof Message) {
            broadcast((Message)packet);
        }
        else if (packet instanceof Presence) {
            broadcastPresence((Presence)packet);
        }
        else if (packet instanceof IQ) {
            IQ reply = IQ.createResultIQ((IQ) packet);
            reply.setChildElement(((IQ) packet).getChildElement());
            reply.setError(PacketError.Condition.bad_request);
            router.route(reply);
        }
    }

    /**
     * Broadcasts the specified presence to all room occupants. If the presence belongs to a
     * user whose role cannot be broadcast then the presence will only be sent to the presence's
     * user. On the other hand, the JID of the user that sent the presence won't be included if the
     * room is semi-anon and the target occupant is not a moderator.
     *
     * @param presence the presence to broadcast.
     */
    private void broadcastPresence(Presence presence) {
        if (presence == null) {
            return;
        }
        if (hasToCheckRoleToBroadcastPresence()) {
            Element frag = presence.getChildElement("x", "http://jabber.org/protocol/muc#user");
            // Check if we can broadcast the presence for this role
            if (!canBroadcastPresence(frag.element("item").attributeValue("role"))) {
                // Just send the presence to the sender of the presence
                try {
                    MUCRole occupant = getOccupant(presence.getFrom().getResource());
                    occupant.send(presence);
                }
                catch (UserNotFoundException e) {
                    // Do nothing
                }
                return;
            }
        }

        // Broadcast presence to occupants hosted by other cluster nodes
        BroadcastPresenceRequest request = new BroadcastPresenceRequest(this, presence);
        CacheFactory.doClusterTask(request);

        // Broadcast presence to occupants connected to this JVM
        request = new BroadcastPresenceRequest(this, presence);
        request.setOriginator(true);
        request.run();
    }

    public void broadcast(BroadcastPresenceRequest presenceRequest) {
        String jid = null;
        Presence presence = presenceRequest.getPresence();
        Element frag = presence.getChildElement("x", "http://jabber.org/protocol/muc#user");
        // Don't include the occupant's JID if the room is semi-anon and the new occupant
        // is not a moderator
        if (!canAnyoneDiscoverJID()) {
            jid = frag.element("item").attributeValue("jid");
        }
        for (MUCRole occupant : occupants.values()) {
            if (!occupant.isLocal()) {
                continue;
            }
            // Don't include the occupant's JID if the room is semi-anon and the new occupant
            // is not a moderator
            if (!canAnyoneDiscoverJID()) {
                if (MUCRole.Role.moderator == occupant.getRole()) {
                    frag.element("item").addAttribute("jid", jid);
                }
                else {
                    frag.element("item").addAttribute("jid", null);
                }
            }
            occupant.send(presence);
        }
    }

    private void broadcast(Message message) {
        // Broadcast message to occupants hosted by other cluster nodes
        BroadcastMessageRequest request = new BroadcastMessageRequest(this, message, occupants.size());
        CacheFactory.doClusterTask(request);

        // Broadcast message to occupants connected to this JVM
        request = new BroadcastMessageRequest(this, message, occupants.size());
        request.setOriginator(true);
        request.run();
    }

    public void broadcast(BroadcastMessageRequest messageRequest) {
        Message message = messageRequest.getMessage();
        // Add message to the room history
        roomHistory.addMessage(message);
        // Send message to occupants connected to this JVM
        for (MUCRole occupant : occupants.values()) {
            // Do not send broadcast messages to deaf occupants or occupants hosted in
            // other cluster nodes
            if (occupant.isLocal() && !occupant.isVoiceOnly()) {
                occupant.send(message);
            }
        }
        if (messageRequest.isOriginator() && isLogEnabled()) {
            MUCRole senderRole = null;
            JID senderAddress;
            if (message.getFrom() != null && message.getFrom().getResource() != null) {
                senderRole = occupants.get(message.getFrom().getResource().toLowerCase());
            }
            if (senderRole == null) {
                // The room itself is sending the message
                senderAddress = getRole().getRoleAddress();
            }
            else {
                // An occupant is sending the message
                senderAddress = senderRole.getUserAddress();
            }
            // Log the conversation
            mucService.logConversation(this, message, senderAddress);
        }
        mucService.messageBroadcastedTo(messageRequest.getOccupants());
    }

    /**
     * An empty role that represents the room itself in the chatroom. Chatrooms need to be able to
     * speak (server messages) and so must have their own role in the chatroom.
     */
    private class RoomRole implements MUCRole {

        private MUCRoom room;

        private RoomRole(MUCRoom room) {
            this.room = room;
        }

        public Presence getPresence() {
            return null;
        }

        public void setPresence(Presence presence) {
        }

        public void setRole(MUCRole.Role newRole) {
        }

        public MUCRole.Role getRole() {
            return MUCRole.Role.moderator;
        }

        public void setAffiliation(MUCRole.Affiliation newAffiliation) {
        }

        public MUCRole.Affiliation getAffiliation() {
            return MUCRole.Affiliation.owner;
        }

        public void changeNickname(String nickname) {
        }

        public String getNickname() {
            return null;
        }

        public boolean isVoiceOnly() {
            return false;
        }

        public boolean isLocal() {
            return true;
        }

        public NodeID getNodeID() {
            return XMPPServer.getInstance().getNodeID();
        }

        public MUCRoom getChatRoom() {
            return room;
        }

        private JID crJID = null;

        public JID getRoleAddress() {
            if (crJID == null) {
                crJID = new JID(room.getName(), mucService.getServiceDomain(), null, true);
            }
            return crJID;
        }

        public JID getUserAddress() {
            return null;
        }

        public void send(Packet packet) {
            room.send(packet);
        }

        public void destroy() {
        }
    }

    public long getChatLength() {
        return endTime - startTime;
    }

    /**
     * Updates all the presences of the given user with the new affiliation and role information. Do
     * nothing if the given jid is not present in the room. If the user has joined the room from
     * several client resources, all his/her occupants' presences will be updated.
     *
     * @param bareJID the bare jid of the user to update his/her role.
     * @param newAffiliation the new affiliation for the JID.
     * @param newRole the new role for the JID.
     * @return the list of updated presences of all the client resources that the client used to
     *         join the room.
     * @throws NotAllowedException If trying to change the moderator role to an owner or an admin or
     *         if trying to ban an owner or an administrator.
     */
    private List<Presence> changeOccupantAffiliation(String bareJID, MUCRole.Affiliation newAffiliation, MUCRole.Role newRole)
            throws NotAllowedException {
        List<Presence> presences = new ArrayList<Presence>();
        // Get all the roles (i.e. occupants) of this user based on his/her bare JID
        List<MUCRole> roles = occupantsByBareJID.get(bareJID);
        if (roles == null) {
            return presences;
        }
        // Collect all the updated presences of these roles
        for (MUCRole role : roles) {
            // Update the presence with the new affiliation and role
            if (role.isLocal()) {
                role.setAffiliation(newAffiliation);
                role.setRole(newRole);
                // Notify the othe cluster nodes to update the occupant
                CacheFactory.doClusterTask(new UpdateOccupant(this, role));
                // Prepare a new presence to be sent to all the room occupants
                presences.add(role.getPresence().createCopy());
            }
            else {
                // Ask the cluster node hosting the occupant to make the changes. Note that if the change
                // is not allowed a NotAllowedException will be thrown
                Element element = (Element) CacheFactory.doSynchronousClusterTask(
                        new UpdateOccupantRequest(this, role.getNickname(), newAffiliation, newRole),
                        role.getNodeID().toByteArray());
                if (element != null) {
                    // Prepare a new presence to be sent to all the room occupants
                    presences.add(new Presence(element, true));
                }
                else {
                    throw new NotAllowedException();
                }
            }
        }
        // Answer all the updated presences
        return presences;
    }

    /**
     * Updates the presence of the given user with the new role information. Do nothing if the given
     * jid is not present in the room.
     *
     * @param jid the full jid of the user to update his/her role.
     * @param newRole the new role for the JID.
     * @return the updated presence of the user or null if none.
     * @throws NotAllowedException If trying to change the moderator role to an owner or an admin.
     */
    private Presence changeOccupantRole(JID jid, MUCRole.Role newRole) throws NotAllowedException {
        // Try looking the role in the bare JID list
        MUCRole role = occupantsByFullJID.get(jid);
        if (role != null) {
            if (role.isLocal()) {
                // Update the presence with the new role
                role.setRole(newRole);
                // Notify the othe cluster nodes to update the occupant
                CacheFactory.doClusterTask(new UpdateOccupant(this, role));
                // Prepare a new presence to be sent to all the room occupants
                return role.getPresence().createCopy();
            }
            else {
                // Ask the cluster node hosting the occupant to make the changes. Note that if the change
                // is not allowed a NotAllowedException will be thrown
                Element element = (Element) CacheFactory.doSynchronousClusterTask(
                        new UpdateOccupantRequest(this, role.getNickname(), null, newRole),
                        role.getNodeID().toByteArray());
                if (element != null) {
                    // Prepare a new presence to be sent to all the room occupants
                    return new Presence(element, true);
                }
                else {
                    throw new NotAllowedException();
                }
            }
        }
        return null;
    }

    public void addFirstOwner(String bareJID) {
        owners.add(bareJID);
    }

    public List<Presence> addOwner(String bareJID, MUCRole sendRole) throws ForbiddenException {
        lock.writeLock().lock();
        try {
            MUCRole.Affiliation oldAffiliation = MUCRole.Affiliation.none;
            if (MUCRole.Affiliation.owner != sendRole.getAffiliation()) {
                throw new ForbiddenException();
            }
            // Check if user is already an owner
            if (owners.contains(bareJID)) {
                // Do nothing
                return Collections.emptyList();
            }
            owners.add(bareJID);
            // Remove the user from other affiliation lists
            if (removeAdmin(bareJID)) {
                oldAffiliation = MUCRole.Affiliation.admin;
            }
            else if (removeMember(bareJID)) {
                oldAffiliation = MUCRole.Affiliation.member;
            }
            else if (removeOutcast(bareJID)) {
                oldAffiliation = MUCRole.Affiliation.outcast;
            }
            // Update the DB if the room is persistent
            MUCPersistenceManager.saveAffiliationToDB(
                this,
                bareJID,
                null,
                MUCRole.Affiliation.owner,
                oldAffiliation);
        }
        finally {
            lock.writeLock().unlock();
        }
        // Update the presence with the new affiliation and inform all occupants
        try {
            return changeOccupantAffiliation(bareJID, MUCRole.Affiliation.owner,
                    MUCRole.Role.moderator);
        }
        catch (NotAllowedException e) {
            // We should never receive this exception....in theory
            return null;
        }
    }

    private boolean removeOwner(String bareJID) {
        return owners.remove(bareJID);
    }

    public List<Presence> addAdmin(String bareJID, MUCRole sendRole) throws ForbiddenException,
            ConflictException {
        lock.writeLock().lock();
        try {
            MUCRole.Affiliation oldAffiliation = MUCRole.Affiliation.none;
            if (MUCRole.Affiliation.owner != sendRole.getAffiliation()) {
                throw new ForbiddenException();
            }
            // Check that the room always has an owner
            if (owners.contains(bareJID) && owners.size() == 1) {
                throw new ConflictException();
            }
            // Check if user is already an admin
            if (admins.contains(bareJID)) {
                // Do nothing
                return Collections.emptyList();
            }
            admins.add(bareJID);
            // Remove the user from other affiliation lists
            if (removeOwner(bareJID)) {
                oldAffiliation = MUCRole.Affiliation.owner;
            }
            else if (removeMember(bareJID)) {
                oldAffiliation = MUCRole.Affiliation.member;
            }
            else if (removeOutcast(bareJID)) {
                oldAffiliation = MUCRole.Affiliation.outcast;
            }
            // Update the DB if the room is persistent
            MUCPersistenceManager.saveAffiliationToDB(
                this,
                bareJID,
                null,
                MUCRole.Affiliation.admin,
                oldAffiliation);
        }
        finally {
            lock.writeLock().unlock();
        }
        // Update the presence with the new affiliation and inform all occupants
        try {
            return changeOccupantAffiliation(bareJID, MUCRole.Affiliation.admin,
                    MUCRole.Role.moderator);
        }
        catch (NotAllowedException e) {
            // We should never receive this exception....in theory
            return null;
        }
    }

    private boolean removeAdmin(String bareJID) {
        return admins.remove(bareJID);
    }

    public List<Presence> addMember(String bareJID, String nickname, MUCRole sendRole)
            throws ForbiddenException, ConflictException {
        lock.writeLock().lock();
        try {
            MUCRole.Affiliation oldAffiliation = (members.containsKey(bareJID) ?
                    MUCRole.Affiliation.member : MUCRole.Affiliation.none);
            if (isMembersOnly()) {
                if (!canOccupantsInvite()) {
                    if (MUCRole.Affiliation.admin != sendRole.getAffiliation()
                            && MUCRole.Affiliation.owner != sendRole.getAffiliation()) {
                        throw new ForbiddenException();
                    }
                }
            }
            else {
                if (MUCRole.Affiliation.admin != sendRole.getAffiliation()
                        && MUCRole.Affiliation.owner != sendRole.getAffiliation()) {
                    throw new ForbiddenException();
                }
            }
            // Check if the desired nickname is already reserved for another member
            if (nickname != null && nickname.trim().length() > 0 && members.containsValue(nickname)) {
                if (!nickname.equals(members.get(bareJID))) {
                    throw new ConflictException();
                }
            }
            // Check that the room always has an owner
            if (owners.contains(bareJID) && owners.size() == 1) {
                throw new ConflictException();
            }
            // Associate the reserved nickname with the bareJID. If nickname is null then associate an
            // empty string
            members.put(bareJID, (nickname == null ? "" : nickname));
            // Remove the user from other affiliation lists
            if (removeOwner(bareJID)) {
                oldAffiliation = MUCRole.Affiliation.owner;
            }
            else if (removeAdmin(bareJID)) {
                oldAffiliation = MUCRole.Affiliation.admin;
            }
            else if (removeOutcast(bareJID)) {
                oldAffiliation = MUCRole.Affiliation.outcast;
            }
            // Update the DB if the room is persistent
            MUCPersistenceManager.saveAffiliationToDB(
                this,
                bareJID,
                nickname,
                MUCRole.Affiliation.member,
                oldAffiliation);
        }
        finally {
            lock.writeLock().unlock();
        }
        // Update other cluster nodes with new member
        CacheFactory.doClusterTask(new AddMember(this, bareJID, (nickname == null ? "" : nickname)));
        // Update the presence with the new affiliation and inform all occupants
        try {
            return changeOccupantAffiliation(bareJID, MUCRole.Affiliation.member,
                    MUCRole.Role.participant);
        }
        catch (NotAllowedException e) {
            // We should never receive this exception....in theory
            return null;
        }
    }

    private boolean removeMember(String bareJID) {
        boolean answer = members.containsKey(bareJID);
        members.remove(bareJID);
        return answer;
    }

    public List<Presence> addOutcast(String bareJID, String reason, MUCRole senderRole)
            throws NotAllowedException, ForbiddenException, ConflictException {
        lock.writeLock().lock();
        try {
            MUCRole.Affiliation oldAffiliation = MUCRole.Affiliation.none;
            if (MUCRole.Affiliation.admin != senderRole.getAffiliation()
                    && MUCRole.Affiliation.owner != senderRole.getAffiliation()) {
                throw new ForbiddenException();
            }
            // Check that the room always has an owner
            if (owners.contains(bareJID) && owners.size() == 1) {
                throw new ConflictException();
            }
            // Check if user is already an outcast
            if (outcasts.contains(bareJID)) {
                // Do nothing
                return Collections.emptyList();
            }

            // Update the affiliation lists
            outcasts.add(bareJID);
            // Remove the user from other affiliation lists
            if (removeOwner(bareJID)) {
                oldAffiliation = MUCRole.Affiliation.owner;
            }
            else if (removeAdmin(bareJID)) {
                oldAffiliation = MUCRole.Affiliation.admin;
            }
            else if (removeMember(bareJID)) {
                oldAffiliation = MUCRole.Affiliation.member;
            }
            // Update the DB if the room is persistent
            MUCPersistenceManager.saveAffiliationToDB(
                this,
                bareJID,
                null,
                MUCRole.Affiliation.outcast,
                oldAffiliation);
        }
        finally {
            lock.writeLock().unlock();
        }
        // Update the presence with the new affiliation and inform all occupants
        // actorJID will be null if the room itself (ie. via admin console) made the request
        JID actorJID = senderRole.getUserAddress();
        List<Presence> updatedPresences = changeOccupantAffiliation(
                bareJID,
                MUCRole.Affiliation.outcast,
                MUCRole.Role.none);
        Element frag;
        // Add the status code and reason why the user was banned to the presences that will
        // be sent to the room occupants (the banned user will not receive this presences)
        for (Presence presence : updatedPresences) {
            frag = presence.getChildElement("x", "http://jabber.org/protocol/muc#user");
            // Add the status code 301 that indicates that the user was banned
            frag.addElement("status").addAttribute("code", "301");
            // Add the reason why the user was banned
            if (reason != null && reason.trim().length() > 0) {
                frag.element("item").addElement("reason").setText(reason);
            }

            // Remove the banned users from the room. If a user has joined the room from
            // different client resources, he/she will be kicked from all the client resources
            // Effectively kick the occupant from the room
            kickPresence(presence, actorJID);
        }
        return updatedPresences;
    }

    private boolean removeOutcast(String bareJID) {
        return outcasts.remove(bareJID);
    }

    public List<Presence> addNone(String bareJID, MUCRole senderRole) throws ForbiddenException,
            ConflictException {
        List<Presence> updatedPresences = null;
        boolean wasMember = false;
        lock.writeLock().lock();
        try {
            MUCRole.Affiliation oldAffiliation = MUCRole.Affiliation.none;
            if (MUCRole.Affiliation.admin != senderRole.getAffiliation()
                    && MUCRole.Affiliation.owner != senderRole.getAffiliation()) {
                throw new ForbiddenException();
            }
            // Check that the room always has an owner
            if (owners.contains(bareJID) && owners.size() == 1) {
                throw new ConflictException();
            }
            wasMember = members.containsKey(bareJID) || admins.contains(bareJID) || owners.contains(bareJID);
            // Remove the user from ALL the affiliation lists
            if (removeOwner(bareJID)) {
                oldAffiliation = MUCRole.Affiliation.owner;
            }
            else if (removeAdmin(bareJID)) {
                oldAffiliation = MUCRole.Affiliation.admin;
            }
            else if (removeMember(bareJID)) {
                oldAffiliation = MUCRole.Affiliation.member;
            }
            else if (removeOutcast(bareJID)) {
                oldAffiliation = MUCRole.Affiliation.outcast;
            }
            // Remove the affiliation of this user from the DB if the room is persistent
            MUCPersistenceManager.removeAffiliationFromDB(this, bareJID, oldAffiliation);
        }
        finally {
            lock.writeLock().unlock();
        }
        // Update the presence with the new affiliation and inform all occupants
        try {
            MUCRole.Role newRole;
            if (isMembersOnly() && wasMember) {
                newRole = MUCRole.Role.none;
            }
            else {
                newRole = isModerated() ? MUCRole.Role.visitor : MUCRole.Role.participant;
            }
            updatedPresences = changeOccupantAffiliation(bareJID, MUCRole.Affiliation.none, newRole);
            if (isMembersOnly() && wasMember) {
                // If the room is members-only, remove the user from the room including a status
                // code of 321 to indicate that the user was removed because of an affiliation
                // change
                Element frag;
                // Add the status code to the presences that will be sent to the room occupants
                for (Presence presence : updatedPresences) {
                    // Set the presence as an unavailable presence
                    presence.setType(Presence.Type.unavailable);
                    presence.setStatus(null);
                    frag = presence.getChildElement("x", "http://jabber.org/protocol/muc#user");
                    // Add the status code 321 that indicates that the user was removed because of
                    // an affiliation change
                    frag.addElement("status").addAttribute("code", "321");

                    // Remove the ex-member from the room. If a user has joined the room from
                    // different client resources, he/she will be kicked from all the client
                    // resources.
                    // Effectively kick the occupant from the room
                    JID actorJID = senderRole.getUserAddress();
                    kickPresence(presence, actorJID);
                }
            }
        }
        catch (NotAllowedException e) {
            // We should never receive this exception....in theory
        }
        return updatedPresences;
    }

    public boolean isLocked() {
        return lockedTime > 0;
    }

    public boolean isManuallyLocked() {
        return lockedTime > 0 && creationDate.getTime() != lockedTime;
    }

    public void presenceUpdated(MUCRole occupantRole, Presence newPresence) {
        // Ask other cluster nodes to update the presence of the occupant
        UpdatePresence request = new UpdatePresence(this, newPresence.createCopy(), occupantRole.getNickname());
        CacheFactory.doClusterTask(request);

        // Update the presence of the occupant
        request = new UpdatePresence(this, newPresence.createCopy(), occupantRole.getNickname());
        request.setOriginator(true);
        request.run();

        // Broadcast new presence of occupant
        broadcastPresence(occupantRole.getPresence().createCopy());
    }

    /**
     * Updates the presence of an occupant with the new presence included in the request.
     *
     * @param updatePresence request to update an occupant's presence.
     */
    public void presenceUpdated(UpdatePresence updatePresence) {
        MUCRole occupantRole = occupants.get(updatePresence.getNickname().toLowerCase());
        if (occupantRole != null) {
            occupantRole.setPresence(updatePresence.getPresence());
        }
        else {
            Log.debug("LocalMUCRoom: Failed to update presence of room occupant. Occupant nickname: " + updatePresence.getNickname());
        }
    }

    public void occupantUpdated(UpdateOccupant update) {
        MUCRole occupantRole = occupants.get(update.getNickname().toLowerCase());
        if (occupantRole != null) {
            if (!occupantRole.isLocal()) {
                occupantRole.setPresence(update.getPresence());
                try {
                    occupantRole.setRole(update.getRole());
                    occupantRole.setAffiliation(update.getAffiliation());
                } catch (NotAllowedException e) {
                    // Ignore. Should never happen with remote roles
                }
            }
            else {
                Log.error("Tried to update local occupant with info of local occupant?. Occupant nickname: " +
                        update.getNickname() + " new role: " + update.getRole() + " new affiliation: " +
                        update.getAffiliation());
            }
        }
        else {
            Log.debug("LocalMUCRoom: Failed to update information of room occupant. Occupant nickname: " + update.getNickname());
        }
    }

    public Presence updateOccupant(UpdateOccupantRequest updateRequest) throws NotAllowedException {
        MUCRole occupantRole = occupants.get(updateRequest.getNickname().toLowerCase());
        if (occupantRole != null) {
            if (updateRequest.isAffiliationChanged()) {
                occupantRole.setAffiliation(updateRequest.getAffiliation());
            }
            occupantRole.setRole(updateRequest.getRole());
            // Notify the othe cluster nodes to update the occupant
            CacheFactory.doClusterTask(new UpdateOccupant(this, occupantRole));
            return occupantRole.getPresence();
        }
        else {
            Log.debug("LocalMUCRoom: Failed to update information of local room occupant. Occupant nickname: " +
                    updateRequest.getNickname());
        }
        return null;
    }

    public void memberAdded(AddMember addMember) {
        // Associate the reserved nickname with the bareJID
        members.put(addMember.getBareJID(), addMember.getNickname());
    }

    public void nicknameChanged(MUCRole occupantRole, Presence newPresence, String oldNick, String newNick) {
        // Ask other cluster nodes to update the nickname of the occupant
        ChangeNickname request = new ChangeNickname(this, oldNick,  newNick, newPresence.createCopy());
        CacheFactory.doClusterTask(request);

        // Update the nickname of the occupant
        request = new ChangeNickname(this, oldNick,  newNick, newPresence.createCopy());
        request.setOriginator(true);
        request.run();

        // Broadcast new presence of occupant
        broadcastPresence(occupantRole.getPresence().createCopy());
    }

    public void nicknameChanged(ChangeNickname changeNickname) {
        MUCRole occupantRole = occupants.get(changeNickname.getOldNick().toLowerCase());
        if (occupantRole != null) {
            // Update the role with the new info
            occupantRole.setPresence(changeNickname.getPresence());
            occupantRole.changeNickname(changeNickname.getNewNick());
            if (changeNickname.isOriginator()) {
                // Fire event that user changed his nickname
                MUCEventDispatcher.nicknameChanged(getRole().getRoleAddress(), occupantRole.getUserAddress(),
                        changeNickname.getOldNick(), changeNickname.getNewNick());
            }
            // Associate the existing MUCRole with the new nickname
            occupants.put(changeNickname.getNewNick().toLowerCase(), occupantRole);
            // Remove the old nickname
            occupants.remove(changeNickname.getOldNick().toLowerCase());
        }
    }

    public void changeSubject(Message packet, MUCRole role) throws ForbiddenException {
        if ((canOccupantsChangeSubject() && role.getRole().compareTo(MUCRole.Role.visitor) < 0) ||
                MUCRole.Role.moderator == role.getRole()) {
            // Do nothing if the new subject is the same as the existing one
            if (packet.getSubject().equals(subject)) {
                return;
            }
            // Set the new subject to the room
            subject = packet.getSubject();
            MUCPersistenceManager.updateRoomSubject(this);
            // Notify all the occupants that the subject has changed
            packet.setFrom(role.getRoleAddress());
            send(packet);

            // Fire event signifying that the room's subject has changed.
            MUCEventDispatcher.roomSubjectChanged(getJID(), role.getUserAddress(), subject);
        }
        else {
            throw new ForbiddenException();
        }
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public void sendInvitation(JID to, String reason, MUCRole senderRole, List<Element> extensions)
            throws ForbiddenException, CannotBeInvitedException {
        if (!isMembersOnly() || canOccupantsInvite()
                || MUCRole.Affiliation.admin == senderRole.getAffiliation()
                || MUCRole.Affiliation.owner == senderRole.getAffiliation()) {
            // If the room is not members-only OR if the room is members-only and anyone can send
            // invitations or the sender is an admin or an owner, then send the invitation
            Message message = new Message();
            message.setFrom(role.getRoleAddress());
            message.setTo(to);

            if (((MultiUserChatServiceImpl)mucService).getMUCDelegate() != null) {
                switch(((MultiUserChatServiceImpl)mucService).getMUCDelegate().sendingInvitation(this, to, senderRole.getUserAddress(), reason)) {
                    case HANDLED_BY_DELEGATE:
                        //if the delegate is taking care of it, there's nothing for us to do
                        return;
                    case HANDLED_BY_OPENFIRE:
                        //continue as normal if we're asked to handle it
                        break;
                    case REJECTED:
                        //we can't invite that person
                        throw new CannotBeInvitedException();
                }
            }

            // Add a list of extensions sent with the original message invitation (if any)
            if (extensions != null) {
                for(Element element : extensions) {
                    element.setParent(null);
                    message.getElement().add(element);
                }
            }
            Element frag = message.addChildElement("x", "http://jabber.org/protocol/muc#user");
            // ChatUser will be null if the room itself (ie. via admin console) made the request
            if (senderRole.getUserAddress() != null) {
                frag.addElement("invite").addAttribute("from", senderRole.getUserAddress().toBareJID());
            }
            if (reason != null && reason.length() > 0) {
                Element invite = frag.element("invite");
                if (invite == null) {
                    invite = frag.addElement("invite");
                }
                invite.addElement("reason").setText(reason);
            }
            if (isPasswordProtected()) {
                frag.addElement("password").setText(getPassword());
            }

            // Include the jabber:x:conference information for backward compatibility
            frag = message.addChildElement("x", "jabber:x:conference");
            frag.addAttribute("jid", role.getRoleAddress().toBareJID());

            // Send the message with the invitation
            router.route(message);
        }
        else {
            throw new ForbiddenException();
        }
    }

    public void sendInvitationRejection(JID to, String reason, JID sender) {
        Message message = new Message();
        message.setFrom(role.getRoleAddress());
        message.setTo(to);
        Element frag = message.addChildElement("x", "http://jabber.org/protocol/muc#user");
        frag.addElement("decline").addAttribute("from", sender.toBareJID());
        if (reason != null && reason.length() > 0) {
            frag.element("decline").addElement("reason").setText(reason);
        }

        // Send the message with the invitation
        router.route(message);
    }

    public IQOwnerHandler getIQOwnerHandler() {
        return iqOwnerHandler;
    }

    public IQAdminHandler getIQAdminHandler() {
        return iqAdminHandler;
    }

    public MUCRoomHistory getRoomHistory() {
        return roomHistory;
    }

    public Collection<String> getOwners() {
        return Collections.unmodifiableList(owners);
    }

    public Collection<String> getAdmins() {
        return Collections.unmodifiableList(admins);
    }

    public Collection<String> getMembers() {
        return Collections.unmodifiableMap(members).keySet();
    }

    public Collection<String> getOutcasts() {
        return Collections.unmodifiableList(outcasts);
    }

    public Collection<MUCRole> getModerators() {
        List<MUCRole> moderators = new ArrayList<MUCRole>();
        for (MUCRole role : occupants.values()) {
            if (MUCRole.Role.moderator == role.getRole()) {
                moderators.add(role);
            }
        }
        return moderators;
    }

    public Collection<MUCRole> getParticipants() {
        List<MUCRole> participants = new ArrayList<MUCRole>();
        for (MUCRole role : occupants.values()) {
            if (MUCRole.Role.participant == role.getRole()) {
                participants.add(role);
            }
        }
        return participants;
    }

    public Presence addModerator(JID jid, MUCRole senderRole) throws ForbiddenException {
        if (MUCRole.Affiliation.admin != senderRole.getAffiliation()
                && MUCRole.Affiliation.owner != senderRole.getAffiliation()) {
            throw new ForbiddenException();
        }
        // Update the presence with the new role and inform all occupants
        try {
            return changeOccupantRole(jid, MUCRole.Role.moderator);
        }
        catch (NotAllowedException e) {
            // We should never receive this exception....in theory
            return null;
        }
    }

    public Presence addParticipant(JID jid, String reason, MUCRole senderRole)
            throws NotAllowedException, ForbiddenException {
        if (MUCRole.Role.moderator != senderRole.getRole()) {
            throw new ForbiddenException();
        }
        // Update the presence with the new role and inform all occupants
        Presence updatedPresence = changeOccupantRole(jid, MUCRole.Role.participant);
        if (updatedPresence != null) {
            Element frag = updatedPresence.getChildElement(
                    "x", "http://jabber.org/protocol/muc#user");
            // Add the reason why the user was granted voice
            if (reason != null && reason.trim().length() > 0) {
                frag.element("item").addElement("reason").setText(reason);
            }
        }
        return updatedPresence;
    }

    public Presence addVisitor(JID jid, MUCRole senderRole) throws NotAllowedException,
            ForbiddenException {
        if (MUCRole.Role.moderator != senderRole.getRole()) {
            throw new ForbiddenException();
        }
        return changeOccupantRole(jid, MUCRole.Role.visitor);
    }

    public Presence kickOccupant(JID jid, JID actorJID, String reason)
            throws NotAllowedException {
        // Update the presence with the new role and inform all occupants
        Presence updatedPresence = changeOccupantRole(jid, MUCRole.Role.none);
        if (updatedPresence != null) {
            Element frag = updatedPresence.getChildElement(
                    "x", "http://jabber.org/protocol/muc#user");

            // Add the status code 307 that indicates that the user was kicked
            frag.addElement("status").addAttribute("code", "307");
            // Add the reason why the user was kicked
            if (reason != null && reason.trim().length() > 0) {
                frag.element("item").addElement("reason").setText(reason);
            }

            // Effectively kick the occupant from the room
            kickPresence(updatedPresence, actorJID);
        }
        return updatedPresence;
    }

    /**
     * Kicks the occupant from the room. This means that the occupant will receive an unavailable
     * presence with the actor that initiated the kick (if any). The occupant will also be removed
     * from the occupants lists.
     *
     * @param kickPresence the presence of the occupant to kick from the room.
     * @param actorJID The JID of the actor that initiated the kick or <tt>null</tt> if the info
     * was not provided.
     */
    private void kickPresence(Presence kickPresence, JID actorJID) {
        MUCRole kickedRole;
        // Get the role to kick
        kickedRole = occupants.get(kickPresence.getFrom().getResource().toLowerCase());
        if (kickedRole != null) {
            kickPresence = kickPresence.createCopy();
            // Add the actor's JID that kicked this user from the room
            if (actorJID != null && actorJID.toString().length() > 0) {
                Element frag = kickPresence.getChildElement(
                        "x", "http://jabber.org/protocol/muc#user");
                frag.element("item").addElement("actor").addAttribute("jid", actorJID.toBareJID());
            }
            // Send the unavailable presence to the banned user
            kickedRole.send(kickPresence);
            // Remove the occupant from the room's occupants lists
            OccupantLeftEvent event = new OccupantLeftEvent(this, kickedRole);
            event.setOriginator(true);
            event.run();

            // Remove the occupant from the room's occupants lists
            event = new OccupantLeftEvent(this, kickedRole);
            CacheFactory.doClusterTask(event);
        }
    }

    public boolean canAnyoneDiscoverJID() {
        return canAnyoneDiscoverJID;
    }

    public void setCanAnyoneDiscoverJID(boolean canAnyoneDiscoverJID) {
        this.canAnyoneDiscoverJID = canAnyoneDiscoverJID;
    }

    public boolean canOccupantsChangeSubject() {
        return canOccupantsChangeSubject;
    }

    public void setCanOccupantsChangeSubject(boolean canOccupantsChangeSubject) {
        this.canOccupantsChangeSubject = canOccupantsChangeSubject;
    }

    public boolean canOccupantsInvite() {
        return canOccupantsInvite;
    }

    public void setCanOccupantsInvite(boolean canOccupantsInvite) {
        this.canOccupantsInvite = canOccupantsInvite;
    }

    public String getNaturalLanguageName() {
        return naturalLanguageName;
    }

    public void setNaturalLanguageName(String naturalLanguageName) {
        this.naturalLanguageName = naturalLanguageName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isMembersOnly() {
        return membersOnly;
    }

    public List<Presence> setMembersOnly(boolean membersOnly) {
        List<Presence> presences = new ArrayList<Presence>();
        if (membersOnly && !this.membersOnly) {
            // If the room was not members-only and now it is, kick occupants that aren't member
            // of the room
            for (MUCRole occupant : occupants.values()) {
                if (occupant.getAffiliation().compareTo(MUCRole.Affiliation.member) > 0) {
                    try {
                        presences.add(kickOccupant(occupant.getRoleAddress(), null,
                                LocaleUtils.getLocalizedString("muc.roomIsNowMembersOnly")));
                    }
                    catch (NotAllowedException e) {
                        Log.error(e);
                    }
                }
            }
        }
        this.membersOnly = membersOnly;
        return presences;
    }

    public boolean isLogEnabled() {
        return logEnabled;
    }

    public void setLogEnabled(boolean logEnabled) {
        this.logEnabled = logEnabled;
    }

    public void setLoginRestrictedToNickname(boolean restricted) {
        this.loginRestrictedToNickname = restricted;
    }

    public boolean isLoginRestrictedToNickname() {
        return loginRestrictedToNickname;
    }

    public void setChangeNickname(boolean canChange) {
        this.canChangeNickname = canChange;
    }

    public boolean canChangeNickname() {
        return canChangeNickname;
    }

    public void setRegistrationEnabled(boolean registrationEnabled) {
        this.registrationEnabled = registrationEnabled;
    }

    public boolean isRegistrationEnabled() {
        return registrationEnabled;
    }

    public int getMaxUsers() {
        return maxUsers;
    }

    public void setMaxUsers(int maxUsers) {
        this.maxUsers = maxUsers;
    }

    public boolean isModerated() {
        return moderated;
    }

    public void setModerated(boolean moderated) {
        this.moderated = moderated;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isPasswordProtected() {
        return password != null && password.trim().length() > 0;
    }

    public boolean isPersistent() {
        return persistent;
    }

    public boolean wasSavedToDB() {
        return isPersistent() && savedToDB;
    }

    public void setSavedToDB(boolean saved) {
        this.savedToDB = saved;
    }

    public void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }

    public boolean isPublicRoom() {
        return !isDestroyed && publicRoom;
    }

    public void setPublicRoom(boolean publicRoom) {
        this.publicRoom = publicRoom;
    }

    public List<String> getRolesToBroadcastPresence() {
        return Collections.unmodifiableList(rolesToBroadcastPresence);
    }

    public void setRolesToBroadcastPresence(List<String> rolesToBroadcastPresence) {
        // TODO If the list changes while there are occupants in the room we must send available or
        // unavailable presences of the affected occupants to the rest of the occupants
        this.rolesToBroadcastPresence = rolesToBroadcastPresence;
    }

    /**
     * Returns true if we need to check whether a presence could be sent or not.
     *
     * @return true if we need to check whether a presence could be sent or not.
     */
    private boolean hasToCheckRoleToBroadcastPresence() {
        // For performance reasons the check is done based on the size of the collection.
        return rolesToBroadcastPresence.size() < 3;
    }

    public boolean canBroadcastPresence(String roleToBroadcast) {
        return "none".equals(roleToBroadcast) || rolesToBroadcastPresence.contains(roleToBroadcast);
    }

    public void lock(MUCRole senderRole) throws ForbiddenException {
        if (MUCRole.Affiliation.owner != senderRole.getAffiliation()) {
            throw new ForbiddenException();
        }
        if (isLocked()) {
            // Do nothing if the room was already locked
            return;
        }
        setLocked(true);
        if (senderRole.getUserAddress() != null) {
            // Send to the occupant that locked the room a message saying so
            Message message = new Message();
            message.setType(Message.Type.groupchat);
            message.setBody(LocaleUtils.getLocalizedString("muc.locked"));
            message.setFrom(getRole().getRoleAddress());
            senderRole.send(message);
        }
    }

    public void unlock(MUCRole senderRole) throws ForbiddenException {
        if (MUCRole.Affiliation.owner != senderRole.getAffiliation()) {
            throw new ForbiddenException();
        }
        if (!isLocked()) {
            // Do nothing if the room was already unlocked
            return;
        }
        setLocked(false);
        if (senderRole.getUserAddress() != null) {
            // Send to the occupant that unlocked the room a message saying so
            Message message = new Message();
            message.setType(Message.Type.groupchat);
            message.setBody(LocaleUtils.getLocalizedString("muc.unlocked"));
            message.setFrom(getRole().getRoleAddress());
            senderRole.send(message);
        }
    }

    private void setLocked(boolean locked) {
        if (locked) {
            this.lockedTime = System.currentTimeMillis();
        }
        else {
            this.lockedTime = 0;
        }
        MUCPersistenceManager.updateRoomLock(this);
    }

    /**
     * Sets the date when the room was locked. Initially when the room is created it is locked so
     * the locked date is the creation date of the room. Afterwards, the room may be manually
     * locked and unlocked so the locked date may be in these cases different than the creation
     * date. A Date with time 0 means that the the room is unlocked.
     *
     * @param lockedTime the date when the room was locked.
     */
    void setLockedDate(Date lockedTime) {
        this.lockedTime = lockedTime.getTime();
    }

    /**
     * Returns the date when the room was locked. Initially when the room is created it is locked so
     * the locked date is the creation date of the room. Afterwards, the room may be manually
     * locked and unlocked so the locked date may be in these cases different than the creation
     * date. When the room is unlocked a Date with time 0 is returned.
     *
     * @return the date when the room was locked.
     */
    Date getLockedDate() {
        return new Date(lockedTime);
    }

    public List<Presence> addAdmins(List<String> newAdmins, MUCRole senderRole)
            throws ForbiddenException, ConflictException {
        List<Presence> answer = new ArrayList<Presence>(newAdmins.size());
        for (String newAdmin : newAdmins) {
            if (newAdmin.trim().length() > 0 && !admins.contains(newAdmin)) {
                answer.addAll(addAdmin(newAdmin, senderRole));
            }
        }
        return answer;
    }

    public List<Presence> addOwners(List<String> newOwners, MUCRole senderRole)
            throws ForbiddenException {
        List<Presence> answer = new ArrayList<Presence>(newOwners.size());
        for (String newOwner : newOwners) {
            if (newOwner.trim().length() > 0 && !owners.contains(newOwner)) {
                answer.addAll(addOwner(newOwner, senderRole));
            }
        }
        return answer;
    }

    public void saveToDB() {
        // Make the room persistent
        MUCPersistenceManager.saveToDB(this);
        if (!savedToDB) {
            // Set that the room is now in the DB
            savedToDB = true;
            // Save the existing room owners to the DB
            for (String owner : owners) {
                MUCPersistenceManager.saveAffiliationToDB(
                    this,
                    owner,
                    null,
                    MUCRole.Affiliation.owner,
                    MUCRole.Affiliation.none);
            }
            // Save the existing room admins to the DB
            for (String admin : admins) {
                MUCPersistenceManager.saveAffiliationToDB(
                    this,
                    admin,
                    null,
                    MUCRole.Affiliation.admin,
                    MUCRole.Affiliation.none);
            }
            // Save the existing room members to the DB
            for (String bareJID : members.keySet()) {
                MUCPersistenceManager.saveAffiliationToDB(this, bareJID, members.get(bareJID),
                        MUCRole.Affiliation.member, MUCRole.Affiliation.none);
            }
            // Save the existing room outcasts to the DB
            for (String outcast : outcasts) {
                MUCPersistenceManager.saveAffiliationToDB(
                    this,
                    outcast,
                    null,
                    MUCRole.Affiliation.outcast,
                    MUCRole.Affiliation.none);
            }
        }
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeSafeUTF(out, name);
        ExternalizableUtil.getInstance().writeLong(out, startTime);
        ExternalizableUtil.getInstance().writeLong(out, lockedTime);
        ExternalizableUtil.getInstance().writeStringList(out, owners);
        ExternalizableUtil.getInstance().writeStringList(out, admins);
        ExternalizableUtil.getInstance().writeStringMap(out, members);
        ExternalizableUtil.getInstance().writeStringList(out, outcasts);
        ExternalizableUtil.getInstance().writeSafeUTF(out, naturalLanguageName);
        ExternalizableUtil.getInstance().writeSafeUTF(out, description);
        ExternalizableUtil.getInstance().writeBoolean(out, canOccupantsChangeSubject);
        ExternalizableUtil.getInstance().writeInt(out, maxUsers);
        ExternalizableUtil.getInstance().writeStringList(out, rolesToBroadcastPresence);
        ExternalizableUtil.getInstance().writeBoolean(out, publicRoom);
        ExternalizableUtil.getInstance().writeBoolean(out, persistent);
        ExternalizableUtil.getInstance().writeBoolean(out, moderated);
        ExternalizableUtil.getInstance().writeBoolean(out, membersOnly);
        ExternalizableUtil.getInstance().writeBoolean(out, canOccupantsInvite);
        ExternalizableUtil.getInstance().writeSafeUTF(out, password);
        ExternalizableUtil.getInstance().writeBoolean(out, canAnyoneDiscoverJID);
        ExternalizableUtil.getInstance().writeBoolean(out, logEnabled);
        ExternalizableUtil.getInstance().writeBoolean(out, loginRestrictedToNickname);
        ExternalizableUtil.getInstance().writeBoolean(out, canChangeNickname);
        ExternalizableUtil.getInstance().writeBoolean(out, registrationEnabled);
        ExternalizableUtil.getInstance().writeSafeUTF(out, subject);
        ExternalizableUtil.getInstance().writeLong(out, roomID);
        ExternalizableUtil.getInstance().writeLong(out, creationDate.getTime());
        ExternalizableUtil.getInstance().writeLong(out, modificationDate.getTime());
        ExternalizableUtil.getInstance().writeBoolean(out, emptyDate != null);
        if (emptyDate != null) {
            ExternalizableUtil.getInstance().writeLong(out, emptyDate.getTime());
        }
        ExternalizableUtil.getInstance().writeBoolean(out, savedToDB);
        ExternalizableUtil.getInstance().writeSafeUTF(out, mucService.getServiceName());
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        name = ExternalizableUtil.getInstance().readSafeUTF(in);
        startTime = ExternalizableUtil.getInstance().readLong(in);
        lockedTime = ExternalizableUtil.getInstance().readLong(in);
        owners.addAll(ExternalizableUtil.getInstance().readStringList(in));
        admins.addAll(ExternalizableUtil.getInstance().readStringList(in));
        members.putAll(ExternalizableUtil.getInstance().readStringMap(in));
        outcasts.addAll(ExternalizableUtil.getInstance().readStringList(in));
        naturalLanguageName = ExternalizableUtil.getInstance().readSafeUTF(in);
        description = ExternalizableUtil.getInstance().readSafeUTF(in);
        canOccupantsChangeSubject = ExternalizableUtil.getInstance().readBoolean(in);
        maxUsers = ExternalizableUtil.getInstance().readInt(in);
        rolesToBroadcastPresence.addAll(ExternalizableUtil.getInstance().readStringList(in));
        publicRoom = ExternalizableUtil.getInstance().readBoolean(in);
        persistent = ExternalizableUtil.getInstance().readBoolean(in);
        moderated = ExternalizableUtil.getInstance().readBoolean(in);
        membersOnly = ExternalizableUtil.getInstance().readBoolean(in);
        canOccupantsInvite = ExternalizableUtil.getInstance().readBoolean(in);
        password = ExternalizableUtil.getInstance().readSafeUTF(in);
        canAnyoneDiscoverJID = ExternalizableUtil.getInstance().readBoolean(in);
        logEnabled = ExternalizableUtil.getInstance().readBoolean(in);
        loginRestrictedToNickname = ExternalizableUtil.getInstance().readBoolean(in);
        canChangeNickname = ExternalizableUtil.getInstance().readBoolean(in);
        registrationEnabled = ExternalizableUtil.getInstance().readBoolean(in);
        subject = ExternalizableUtil.getInstance().readSafeUTF(in);
        roomID = ExternalizableUtil.getInstance().readLong(in);
        creationDate = new Date(ExternalizableUtil.getInstance().readLong(in));
        modificationDate = new Date(ExternalizableUtil.getInstance().readLong(in));
        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            emptyDate = new Date(ExternalizableUtil.getInstance().readLong(in));
        }
        savedToDB = ExternalizableUtil.getInstance().readBoolean(in);
        String subdomain = ExternalizableUtil.getInstance().readSafeUTF(in);
        mucService = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(subdomain);
        if (mucService == null) throw new IllegalArgumentException("MUC service not found for subdomain: " + subdomain);
        roomHistory = new MUCRoomHistory(this, new HistoryStrategy(mucService.getHistoryStrategy()));

        PacketRouter packetRouter = XMPPServer.getInstance().getPacketRouter();
        this.iqOwnerHandler = new IQOwnerHandler(this, packetRouter);
        this.iqAdminHandler = new IQAdminHandler(this, packetRouter);

        router = packetRouter;
    }

    public void updateConfiguration(LocalMUCRoom otherRoom) {
        startTime = otherRoom.startTime;
        lockedTime = otherRoom.lockedTime;
        owners = otherRoom.owners;
        admins = otherRoom.admins;
        members = otherRoom.members;
        outcasts = otherRoom.outcasts;
        naturalLanguageName = otherRoom.naturalLanguageName;
        description = otherRoom.description;
        canOccupantsChangeSubject = otherRoom.canOccupantsChangeSubject;
        maxUsers = otherRoom.maxUsers;
        rolesToBroadcastPresence = otherRoom.rolesToBroadcastPresence;
        publicRoom = otherRoom.publicRoom;
        persistent = otherRoom.persistent;
        moderated = otherRoom.moderated;
        membersOnly = otherRoom.membersOnly;
        canOccupantsInvite = otherRoom.canOccupantsInvite;
        password = otherRoom.password;
        canAnyoneDiscoverJID = otherRoom.canAnyoneDiscoverJID;
        logEnabled = otherRoom.logEnabled;
        loginRestrictedToNickname = otherRoom.loginRestrictedToNickname;
        canChangeNickname = otherRoom.canChangeNickname;
        registrationEnabled = otherRoom.registrationEnabled;
        subject = otherRoom.subject;
        roomID = otherRoom.roomID;
        creationDate = otherRoom.creationDate;
        modificationDate = otherRoom.modificationDate;
        emptyDate = otherRoom.emptyDate;
        savedToDB = otherRoom.savedToDB;
        mucService = otherRoom.mucService;
    }

    /*
     * (non-Javadoc)
     * @see org.jivesoftware.util.resultsetmanager.Result#getUID()
     */
	public String getUID()
	{
		// name is unique for each one particular MUC service.
		return name;
	}
}