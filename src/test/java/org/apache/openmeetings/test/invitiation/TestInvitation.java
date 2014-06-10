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
package org.apache.openmeetings.test.invitiation;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.axis2.AxisFault;
import org.apache.openmeetings.axis.services.UserWebService;
import org.apache.openmeetings.db.dao.user.UserDao;
import org.apache.openmeetings.db.entity.server.Sessiondata;
import org.apache.openmeetings.db.entity.user.User;
import org.apache.openmeetings.remote.InvitationService;
import org.apache.openmeetings.remote.MainService;
import org.apache.openmeetings.test.AbstractJUnitDefaults;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class TestInvitation extends AbstractJUnitDefaults {
	@Autowired
	private InvitationService invitationService;
	@Autowired
	private MainService mService;
	@Autowired
	private UserWebService userWebService;
	@Autowired
	private UserDao userDao;
	
	@Test
	public void testSendInvitationLink() throws AxisFault {
		Sessiondata sessionData = mService.getsessiondata();
		
		Long uid = userWebService.loginUser(sessionData.getSession_id(), username, userpass);
		User us = userDao.get(uid);
		
		String date = new SimpleDateFormat("dd.MM.yyyy").format(new Date());
		invitationService.sendInvitationHash(sessionData.getSession_id(), "Testname", "Testlastname", "message", "sebawagner@apache.org", 
				"subject", 1L, "", false, "", 1, date, "12:00", date, "14:00", 1L, us.getTimeZoneId(), true);
	}
}
