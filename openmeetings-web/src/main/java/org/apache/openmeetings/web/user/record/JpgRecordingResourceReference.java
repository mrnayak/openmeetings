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

import static org.apache.openmeetings.util.OmFileHelper.EXTENSION_JPG;
import static org.apache.openmeetings.util.OmFileHelper.JPG_MIME_TYPE;
import static org.apache.openmeetings.util.OmFileHelper.recordingFileName;

import java.io.File;

import org.apache.openmeetings.db.entity.record.Recording;
import org.apache.wicket.request.resource.IResource.Attributes;

public class JpgRecordingResourceReference extends RecordingResourceReference {
	private static final long serialVersionUID = 1L;

	public JpgRecordingResourceReference() {
		super("jpg-recording-cover");
	}

	@Override
	public String getMimeType() {
		return JPG_MIME_TYPE;
	}

	@Override
	protected String getFileName(Recording r) {
		return String.format("%s%s.%s", recordingFileName, r.getId(), EXTENSION_JPG);
	}

	@Override
	protected File getFile(Recording r, Attributes attr) {
		return r.getFile(EXTENSION_JPG);
	}
}
