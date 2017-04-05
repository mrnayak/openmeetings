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
package org.apache.openmeetings.web.user.record;

import static org.apache.openmeetings.util.OmFileHelper.getHumanSize;
import static org.apache.openmeetings.web.app.Application.getBean;
import static org.apache.openmeetings.web.app.WebSession.getUserId;

import org.apache.openmeetings.db.dao.record.RecordingDao;
import org.apache.openmeetings.db.dto.record.RecordingContainerData;
import org.apache.openmeetings.db.entity.file.FileItem;
import org.apache.openmeetings.web.app.Application;
import org.apache.openmeetings.web.common.NameDialog;
import org.apache.openmeetings.web.common.UserPanel;
import org.apache.openmeetings.web.common.tree.FileTreePanel;
import org.apache.wicket.ajax.AjaxRequestTarget;

public class RecordingsPanel extends UserPanel {
	private static final long serialVersionUID = 1L;
	private final VideoPlayer video = new VideoPlayer("video");
	private final VideoInfo info = new VideoInfo("info");
	private final NameDialog addFolder = new NameDialog("addFolder", Application.getString(712)) {
		private static final long serialVersionUID = 1L;

		@Override
		protected void onSubmit(AjaxRequestTarget target) {
			fileTree.createFolder(target, getModelObject());
		}
	};
	private final FileTreePanel fileTree;

	public RecordingsPanel(String id) {
		super(id);
		add(fileTree = new FileTreePanel("tree", null, addFolder, null) {
			private static final long serialVersionUID = 1L;

			@Override
			public void updateSizes() {
				RecordingContainerData sizeData = getBean(RecordingDao.class).getContainerData(getUserId());
				if (sizeData != null) {
					homeSize.setObject(getHumanSize(sizeData.getUserHomeSize()));
					publicSize.setObject(getHumanSize(sizeData.getPublicFileSize()));
				}
			}

			@Override
			protected void update(AjaxRequestTarget target, FileItem f) {
				video.update(target, f);
				info.update(target, f);
			}
		});
		add(video, info, addFolder);
	}
}
