/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License") +  you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.openmeetings.web.room;

import static org.apache.openmeetings.util.OpenmeetingsVariables.webAppRootKey;
import static org.apache.openmeetings.web.app.Application.addUserToRoom;
import static org.apache.openmeetings.web.app.Application.exitRoom;
import static org.apache.openmeetings.web.app.Application.getBean;
import static org.apache.openmeetings.web.app.Application.getOnlineClient;
import static org.apache.openmeetings.web.app.Application.getRoomClients;
import static org.apache.openmeetings.web.app.WebSession.getDateFormat;
import static org.apache.openmeetings.web.app.WebSession.getUserId;
import static org.apache.openmeetings.web.util.CallbackFunctionHelper.getNamedFunction;
import static org.apache.wicket.ajax.attributes.CallbackParameter.explicit;

import java.util.Calendar;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import org.apache.directory.api.util.Strings;
import org.apache.openmeetings.core.data.whiteboard.WhiteboardCache;
import org.apache.openmeetings.core.remote.ConferenceLibrary;
import org.apache.openmeetings.core.remote.red5.ScopeApplicationAdapter;
import org.apache.openmeetings.core.util.WebSocketHelper;
import org.apache.openmeetings.db.dao.calendar.AppointmentDao;
import org.apache.openmeetings.db.dao.log.ConferenceLogDao;
import org.apache.openmeetings.db.dao.user.UserDao;
import org.apache.openmeetings.db.entity.basic.Client;
import org.apache.openmeetings.db.entity.calendar.Appointment;
import org.apache.openmeetings.db.entity.calendar.MeetingMember;
import org.apache.openmeetings.db.entity.file.FileItem;
import org.apache.openmeetings.db.entity.log.ConferenceLog;
import org.apache.openmeetings.db.entity.room.Room;
import org.apache.openmeetings.db.entity.room.Room.Right;
import org.apache.openmeetings.db.entity.room.Room.RoomElement;
import org.apache.openmeetings.db.entity.room.RoomGroup;
import org.apache.openmeetings.db.entity.server.SOAPLogin;
import org.apache.openmeetings.db.entity.user.GroupUser;
import org.apache.openmeetings.db.entity.user.User;
import org.apache.openmeetings.db.util.AuthLevelUtil;
import org.apache.openmeetings.util.message.RoomMessage;
import org.apache.openmeetings.util.message.RoomMessage.Type;
import org.apache.openmeetings.util.message.TextRoomMessage;
import org.apache.openmeetings.web.app.Application;
import org.apache.openmeetings.web.app.WebSession;
import org.apache.openmeetings.web.common.BasePanel;
import org.apache.openmeetings.web.room.activities.ActivitiesPanel;
import org.apache.openmeetings.web.room.activities.Activity;
import org.apache.openmeetings.web.room.menu.RoomMenuPanel;
import org.apache.openmeetings.web.room.sidebar.RoomSidebar;
import org.apache.openmeetings.web.user.record.JpgRecordingResourceReference;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.ajax.AbstractDefaultAjaxBehavior;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.authroles.authorization.strategies.role.annotations.AuthorizeInstantiation;
import org.apache.wicket.core.request.handler.IPartialPageRequestHandler;
import org.apache.wicket.event.IEvent;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.head.OnDomReadyHeaderItem;
import org.apache.wicket.markup.head.PriorityHeaderItem;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.protocol.ws.api.event.WebSocketPushPayload;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.request.resource.JavaScriptResourceReference;
import org.apache.wicket.request.resource.ResourceReference;
import org.red5.logging.Red5LoggerFactory;
import org.slf4j.Logger;

import com.googlecode.wicket.jquery.core.JQueryBehavior;
import com.googlecode.wicket.jquery.core.Options;
import com.googlecode.wicket.jquery.ui.interaction.droppable.Droppable;
import com.googlecode.wicket.jquery.ui.widget.dialog.DialogButton;
import com.googlecode.wicket.jquery.ui.widget.dialog.DialogButtons;
import com.googlecode.wicket.jquery.ui.widget.dialog.DialogIcon;
import com.googlecode.wicket.jquery.ui.widget.dialog.MessageDialog;

@AuthorizeInstantiation("Room")
public class RoomPanel extends BasePanel {
	private static final long serialVersionUID = 1L;
	private static final Logger log = Red5LoggerFactory.getLogger(RoomPanel.class, webAppRootKey);
	private static final String ACCESS_DENIED_ID = "access-denied";
	private static final String EVENT_DETAILS_ID = "event-details";
	private static final String PARAM_WB_ID = "wbId";
	public enum Action {
		kick
		, settings
		, refresh
		, exclusive
	}
	private final Room r;
	private final WebMarkupContainer room = new WebMarkupContainer("roomContainer");
	private final AbstractDefaultAjaxBehavior roomEnter = new AbstractDefaultAjaxBehavior() {
		private static final long serialVersionUID = 1L;

		@Override
		protected void respond(AjaxRequestTarget target) {
			target.appendJavaScript("setRoomSizes();");
			getBean(ConferenceLogDao.class).add(
					ConferenceLog.Type.roomEnter
					, getUserId(), "0", r.getId()
					, WebSession.get().getClientInfo().getProperties().getRemoteAddress()
					, "" + r.getId());
			//TODO SID etc
			WebSocketHelper.sendRoom(new RoomMessage(r.getId(), getUserId(), RoomMessage.Type.roomEnter));
			getMainPanel().getChat().roomEnter(r, target);
			if (r.isFilesOpened()) {
				sidebar.setFilesActive(target);
			}
			if (Room.Type.restricted != r.getType()) {
				List<Client> mods = Application.getRoomClients(r.getId(), c -> c.hasRight(Room.Right.moderator));
				if (mods.isEmpty()) {
					waitApplyModeration.open(target);
				}
			}
		}
	};
	private final AbstractDefaultAjaxBehavior activeWb = new AbstractDefaultAjaxBehavior() {
		private static final long serialVersionUID = 1L;

		@Override
		protected void respond(AjaxRequestTarget target) {
			long wbId = getRequest().getRequestParameters().getParameterValue(PARAM_WB_ID).toLong(Long.MIN_VALUE);
			if (wbId != Long.MIN_VALUE) {
				activeWbId = wbId;
			}
		}
	};
	private RedirectMessageDialog roomClosed;
	private MessageDialog clientKicked, waitForModerator, waitApplyModeration;

	private RoomMenuPanel menu;
	private RoomSidebar sidebar;
	private ActivitiesPanel activities;
	private String sharingUser = null;
	private String recordingUser = null;
	private String publishingUser = null; //TODO add
	private long activeWbId = -1;

	public RoomPanel(String id, Room r) {
		super(id);
		this.r = r;
		//TODO check here and set
		//private String recordingUser = null;
		//private String sharingUser = null;
		//private String publishingUser = null;
	}

	@Override
	protected void onInitialize() {
		super.onInitialize();
		//let's refresh user in client
		getClient().updateUser(getBean(UserDao.class));
		Component accessDenied = new WebMarkupContainer(ACCESS_DENIED_ID).setVisible(false);
		Component eventDetail = new WebMarkupContainer(EVENT_DETAILS_ID).setVisible(false);

		room.add(menu = new RoomMenuPanel("menu", this));
		room.add(AttributeModifier.append("data-room-id", r.getId()));
		Droppable<FileItem> wbArea = new Droppable<FileItem>("wb-area") {
			private static final long serialVersionUID = 1L;

			@Override
			public void onConfigure(JQueryBehavior behavior) {
				super.onConfigure(behavior);
				behavior.setOption("hoverClass", Options.asString("ui-state-hover"));
				behavior.setOption("accept", Options.asString(".recorditem, .fileitem, .readonlyitem"));
			}

			@Override
			public void onDrop(AjaxRequestTarget target, Component component) {
				Object o = component.getDefaultModelObject();
				if (activeWbId > -1 && o instanceof FileItem) {
					FileItem f = (FileItem)o;
					if (sidebar.getFilesPanel().isSelected(f)) {
						for (Entry<String, FileItem> e : sidebar.getFilesPanel().getSelected().entrySet()) {
							sendFileToWb(e.getValue(), false);
						}
					} else {
						sendFileToWb(f, false);
					}
				}
			}
		};
		room.add(wbArea.add(new SwfPanel("whiteboard", r.getId(), getClient().getUid())));
		room.add(roomEnter, activeWb);
		room.add(sidebar = new RoomSidebar("sidebar", this));
		room.add(activities = new ActivitiesPanel("activities", this));
		add(roomClosed = new RedirectMessageDialog("room-closed", "1098", r.isClosed(), r.getRedirectURL()));
		if (r.isClosed()) {
			room.setVisible(false);
		} else if (getRoomClients(r.getId()).size() >= r.getNumberOfPartizipants()) {
			accessDenied = new ExpiredMessageDialog(ACCESS_DENIED_ID, getString("99"), menu);
			room.setVisible(false);
		} else if (r.getId().equals(WebSession.get().getRoomId())) {
			// secureHash/invitationHash, already checked
		} else {
			boolean allowed = false;
			String deniedMessage = null;
			if (r.isAppointment()) {
				Appointment a = getBean(AppointmentDao.class).getByRoom(r.getId());
				if (a != null && !a.isDeleted()) {
					allowed = a.getOwner().getId().equals(getUserId());
					log.debug("appointed room, isOwner ? " + allowed);
					if (!allowed) {
						for (MeetingMember mm : a.getMeetingMembers()) {
							if (getUserId().equals(mm.getUser().getId())) {
								allowed = true;
								break;
							}
						}
					}
					if (allowed) {
						Calendar c = WebSession.getCalendar();
						if (c.getTime().after(a.getStart()) && c.getTime().before(a.getEnd())) {
							eventDetail = new EventDetailDialog(EVENT_DETAILS_ID, a);
						} else {
							allowed = false;
							deniedMessage = getString("1271") + String.format(" %s - %s", getDateFormat().format(a.getStart()), getDateFormat().format(a.getEnd()));
						}
					}
				}
			} else {
				allowed = r.getIspublic() || (r.getOwnerId() != null && r.getOwnerId().equals(getUserId()));
				log.debug("public ? " + r.getIspublic() + ", ownedId ? " + r.getOwnerId() + " " + allowed);
				if (!allowed) {
					User u = getClient().getUser();
					for (RoomGroup ro : r.getRoomGroups()) {
						for (GroupUser ou : u.getGroupUsers()) {
							if (ro.getGroup().getId().equals(ou.getGroup().getId())) {
								allowed = true;
								break;
							}
						}
						if (allowed) {
							break;
						}
					}
				}
			}
			if (!allowed) {
				if (deniedMessage == null) {
					deniedMessage = getString("1599");
				}
				accessDenied = new ExpiredMessageDialog(ACCESS_DENIED_ID, deniedMessage, menu);
				room.setVisible(false);
			}
		}
		waitForModerator = new MessageDialog("wait-for-moderator", getString("204"), getString("696"), DialogButtons.OK, DialogIcon.LIGHT) {
			private static final long serialVersionUID = 1L;

			@Override
			public void onClose(IPartialPageRequestHandler handler, DialogButton button) {
				// no-op
			}
		};
		waitApplyModeration = new MessageDialog("wait-apply-moderation", getString("204"), getString(r.isModerated() ? "641" : "498"), DialogButtons.OK, DialogIcon.LIGHT) {
			private static final long serialVersionUID = 1L;

			@Override
			public void onClose(IPartialPageRequestHandler handler, DialogButton button) {
				// no-op
			}
		};
		add(room, accessDenied, eventDetail, waitForModerator, waitApplyModeration);
		if (r.isWaitForRecording()) {
			add(new MessageDialog("wait-for-recording", getString("1316"), getString("1315"), DialogButtons.OK, DialogIcon.LIGHT) {
				private static final long serialVersionUID = 1L;

				@Override
				public void onConfigure(JQueryBehavior behavior) {
					super.onConfigure(behavior);
					behavior.setOption("autoOpen", true);
					behavior.setOption("resizable", false);
				}

				@Override
				public void onClose(IPartialPageRequestHandler handler, DialogButton button) {
				}
			});
		} else {
			add(new WebMarkupContainer("wait-for-recording").setVisible(false));
		}
		if (room.isVisible()) {
			add(new NicknameDialog("nickname", this));
		} else {
			add(new WebMarkupContainer("nickname").setVisible(false));
		}
		add(clientKicked = new MessageDialog("client-kicked", getString("797"), getString("606"), DialogButtons.OK, DialogIcon.ERROR) {
			private static final long serialVersionUID = 1L;

			@Override
			public void onClose(IPartialPageRequestHandler handler, DialogButton button) {
				menu.exit(handler);
			}
		});
	}

	@Override
	public void onEvent(IEvent<?> event) {
		if (event.getPayload() instanceof WebSocketPushPayload) {
			WebSocketPushPayload wsEvent = (WebSocketPushPayload) event.getPayload();
			if (wsEvent.getMessage() instanceof RoomMessage) {
				RoomMessage m = (RoomMessage)wsEvent.getMessage();
				IPartialPageRequestHandler handler = wsEvent.getHandler();
				switch (m.getType()) {
					case pollCreated:
						menu.updatePoll(handler, m.getUserId());
						break;
					case pollUpdated:
						menu.updatePoll(handler, null);
						break;
					case recordingStoped:
						{
							String uid = ((TextRoomMessage)m).getText();
							if (Strings.isEmpty(uid) || !uid.equals(recordingUser)) {
								log.error("Not existing/BAD user has stopped recording {} != {} !!!!", uid, recordingUser);
							}
							recordingUser = null;
							menu.update(handler);
							Client c = getOnlineClient(uid);
							if (c == null) {
								log.error("Not existing user has stopped recording {} !!!!", uid);
								return;
							}
							c.getActivities().remove(Client.Activity.record);
						}
						break;
					case recordingStarted:
						{
							recordingUser = ((TextRoomMessage)m).getText();
							menu.update(handler);
							Client c = getOnlineClient(recordingUser);
							if (c == null) {
								log.error("Not existing user has started recording {} !!!!", recordingUser);
								return;
							}
							c.getActivities().add(Client.Activity.record);
						}
						break;
					case sharingStoped:
						//TODO check sharingUser == ((TextRoomMessage)m).getText();
						sharingUser = null;
						menu.update(handler);
						break;
					case sharingStarted:
						{
							sharingUser = ((TextRoomMessage)m).getText();
							menu.update(handler);
						}
						break;
					case rightUpdated:
						sidebar.update(handler);
						menu.update(handler);
						break;
					case roomEnter:
						sidebar.update(handler);
						menu.update(handler);
						// TODO should this be fixed?
						//activities.addActivity(new Activity(m, Activity.Type.roomEnter), handler);
						break;
					case roomExit:
						//TODO check user/remove tab
						sidebar.update(handler);
						activities.add(new Activity(m, Activity.Type.roomExit), handler);
						break;
					case roomClosed:
						handler.add(room.setVisible(false));
						roomClosed.open(handler);
						break;
					case requestRightModerator:
						activities.add(new Activity((TextRoomMessage)m, Activity.Type.reqRightModerator), handler);
						break;
					case requestRightWb:
						activities.add(new Activity((TextRoomMessage)m, Activity.Type.reqRightWb), handler);
						break;
					case requestRightShare:
						activities.add(new Activity((TextRoomMessage)m, Activity.Type.reqRightShare), handler);
						break;
					case requestRightRemote:
						activities.add(new Activity((TextRoomMessage)m, Activity.Type.reqRightRemote), handler);
						break;
					case requestRightA:
						activities.add(new Activity((TextRoomMessage)m, Activity.Type.reqRightA), handler);
						break;
					case requestRightAv:
						activities.add(new Activity((TextRoomMessage)m, Activity.Type.reqRightAv), handler);
						break;
					case requestRightMute:
						activities.add(new Activity((TextRoomMessage)m, Activity.Type.reqRightMute), handler);
						break;
					case requestRightExclusive:
						activities.add(new Activity((TextRoomMessage)m, Activity.Type.reqRightExclusive), handler);
						break;
					case activityRemove:
						activities.remove(((TextRoomMessage)m).getText(), handler);
						break;
					case haveQuestion:
						if (getClient().hasRight(Room.Right.moderator) || getUserId().equals(m.getUserId())) {
							activities.add(new Activity((TextRoomMessage)m, Activity.Type.haveQuestion), handler);
						}
						break;
					case kick:
						{
							//FIXME TODO add line to activities about user kick
							//activities.add(new Activity(m, Activity.Type.roomExit), handler);
							String uid = ((TextRoomMessage)m).getText();
							if (getClient().getUid().equals(uid)) {
								handler.add(room.setVisible(false));
								getMainPanel().getChat().toggle(handler, false);
								clientKicked.open(handler);
								exitRoom(getClient());
							}
						}
						break;
				}
			}
		}
		super.onEvent(event);
	}

	@Override
	protected void onBeforeRender() {
		super.onBeforeRender();
		if (room.isVisible()) {
			//We are setting initial rights here
			Client c = getClient();
			addUserToRoom(c.setRoomId(getRoom().getId()));
			SOAPLogin soap = WebSession.get().getSoapLogin();
			if (soap != null && soap.isModerator()) {
				c.allow(Right.superModerator);
			} else {
				//FIXME TODO !!! c.getUser != getUserId
				Set<Right> rr = AuthLevelUtil.getRoomRight(c.getUser(), r, r.isAppointment() ? getBean(AppointmentDao.class).getByRoom(r.getId()) : null, getRoomClients(r.getId()).size());
				if (!rr.isEmpty()) {
					c.allow(rr);
				}
			}
		}
	}

	public static boolean isModerator(long userId, long roomId) {
		return hasRight(userId, roomId, Right.moderator);
	}

	public static boolean hasRight(long userId, long roomId, Right r) {
		for (Client c : getRoomClients(roomId)) {
			if (c.getUserId().equals(userId) && c.hasRight(r)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public BasePanel onMenuPanelLoad(IPartialPageRequestHandler handler) {
		getBasePage().getHeader().setVisible(false);
		getMainPanel().getTopControls().setVisible(false);
		if (r.isHidden(RoomElement.Chat) || !isVisible()) {
			getMainPanel().getChat().toggle(handler, false);
		}
		if (handler != null) {
			handler.add(getBasePage().getHeader(), getMainPanel().getTopControls());
			if (isVisible()) {
				handler.appendJavaScript("roomLoad();");
			}
		}
		return this;
	}

	public void show(IPartialPageRequestHandler handler) {
		if (!r.isHidden(RoomElement.Chat)) {
			getMainPanel().getChat().toggle(handler, true);
		}
		handler.add(this.setVisible(true));
		handler.appendJavaScript("roomLoad();");
	}

	@Override
	public void cleanup(IPartialPageRequestHandler handler) {
		handler.add(getBasePage().getHeader().setVisible(true), getMainPanel().getTopControls().setVisible(true));
		if (r.isHidden(RoomElement.Chat)) {
			getMainPanel().getChat().toggle(handler, true);
		}
		handler.appendJavaScript("if (typeof roomUnload == 'function') { roomUnload(); }");
		Application.exitRoom(getClient());
		getMainPanel().getChat().roomExit(r, handler);
	}

	private static ResourceReference newResourceReference() {
		return new JavaScriptResourceReference(RoomPanel.class, "room.js");
	}

	@Override
	public void renderHead(IHeaderResponse response) {
		super.renderHead(response);
		response.render(new PriorityHeaderItem(JavaScriptHeaderItem.forReference(newResourceReference())));
		WebSession ws = WebSession.get();
		if (!Strings.isEmpty(r.getRedirectURL()) && (ws.getSoapLogin() != null || ws.getInvitation() != null)) {
			response.render(new PriorityHeaderItem(JavaScriptHeaderItem.forScript(
					String.format("function roomReload(event, ui) {window.location.href='%s';}", r.getRedirectURL())
					, String.format("room-reload-%s", UUID.randomUUID()))));
		}
		if (room.isVisible()) {
			response.render(OnDomReadyHeaderItem.forScript(roomEnter.getCallbackScript()));
			response.render(new PriorityHeaderItem(getNamedFunction("setActiveWbId", activeWb, explicit(PARAM_WB_ID))));
		}
	}

	public void requestRight(Right right, IPartialPageRequestHandler handler) {
		RoomMessage.Type reqType = null;
		List<Client> mods = Application.getRoomClients(r.getId(), c -> c.hasRight(Room.Right.moderator));
		if (mods.size() == 0) {
			if (r.isModerated()) {
				//dialog
				waitForModerator.open(handler);
				return;
			} else {
				// we found no-one we can ask, allow right
				broadcast(getClient().allow(right));
			}
		}
		// ask
		switch (right) {
			case moderator:
				reqType = Type.requestRightModerator;
				break;
			case whiteBoard:
				reqType = Type.requestRightWb;
				break;
			case share:
				reqType = Type.requestRightWb;
				break;
			case audio:
				reqType = Type.requestRightA;
				break;
			case exclusive:
				reqType = Type.requestRightExclusive;
				break;
			case mute:
				reqType = Type.requestRightMute;
				break;
			case remoteControl:
				reqType = Type.requestRightRemote;
				break;
			case video:
				reqType = Type.requestRightAv;
				break;
			default:
				break;
		}
		if (reqType != null) {
			WebSocketHelper.sendRoom(new TextRoomMessage(getRoom().getId(), getUserId(), reqType, getClient().getUid()));
		}
	}

	public void allowRight(Client client, Right... rights) {
		client.allow(rights);
		broadcast(client);
	}

	public void denyRight(Client client, Right... rights) {
		for (Right right : rights) {
			client.deny(right);
		}
		if (client.hasActivity(Client.Activity.broadcastA) && !client.hasRight(Right.audio)) {
			client.remove(Client.Activity.broadcastA);
		}
		if (client.hasActivity(Client.Activity.broadcastV) && !client.hasRight(Right.video)) {
			client.remove(Client.Activity.broadcastV);
		}
		broadcast(client);
	}

	public void kickUser(Client client) {
		WebSocketHelper.sendRoom(new TextRoomMessage(client.getRoomId(), client.getUserId(), Type.kick, client.getUid()));
	}

	public void broadcast(Client client) {
		WebSocketHelper.sendRoom(new RoomMessage(getRoom().getId(), getUserId(), RoomMessage.Type.rightUpdated));
		RoomBroadcaster.sendUpdatedClient(client);
	}

	public Room getRoom() {
		return r;
	}

	public Client getClient() {
		return getMainPanel().getClient();
	}

	public boolean screenShareAllowed() {
		Room r = getRoom();
		org.apache.openmeetings.db.entity.room.Client rcl = RoomBroadcaster.getClient(getMainPanel().getClient().getUid());
		return Room.Type.interview != r.getType() && !r.isHidden(RoomElement.ScreenSharing)
				&& r.isAllowRecording() && getClient().hasRight(Right.share)
				&& getSharingUser() == null && rcl != null && rcl.getUserId() != null;
	}

	public RoomSidebar getSidebar() {
		return sidebar;
	}

	public ActivitiesPanel getActivities() {
		return activities;
	}

	public String getSharingUser() {
		return sharingUser;
	}

	public String getRecordingUser() {
		return recordingUser;
	}

	public String getPublishingUser() {
		return publishingUser;
	}

	public void sendFileToWb(FileItem fi, boolean clean) {
		if (activeWbId > -1 && fi.getId() != null && FileItem.Type.Folder != fi.getType()) {
			if (FileItem.Type.WmlFile == fi.getType()) {
				getBean(ConferenceLibrary.class).sendToWhiteboard(getClient().getUid(), activeWbId, fi);
			} else {
				String url = null;
				PageParameters pp = new PageParameters();
				pp.add("id", fi.getId())
					.add("ruid", getBean(WhiteboardCache.class).get(r.getId()).getUid());
				switch (fi.getType()) {
					case Video:
						pp.add("preview", true);
						url = urlFor(new RoomResourceReference(), pp).toString();
						break;
					case Recording:
						url = urlFor(new JpgRecordingResourceReference(), pp).toString();
						break;
					default:
						url = urlFor(new RoomResourceReference(), pp).toString();
						break;
				}
				getBean(ScopeApplicationAdapter.class).sendToWhiteboard(getClient().getUid(), activeWbId, fi, url, clean);
			}
		}
	}
}
