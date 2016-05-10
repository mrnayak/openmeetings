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


import static org.apache.wicket.RuntimeConfigurationType.DEVELOPMENT;

import org.apache.openmeetings.web.common.BasePanel;
import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.head.PriorityHeaderItem;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.request.mapper.parameter.PageParametersEncoder;
import org.apache.wicket.request.resource.JavaScriptResourceReference;
import org.apache.wicket.request.resource.ResourceReference;
import org.apache.wicket.util.string.StringValue;

public class SwfPanel extends BasePanel {
	private static final long serialVersionUID = 1L;
	public static final String SWF_TYPE_NETWORK = "network";
	
	public SwfPanel(String id) {
		this(id, new PageParameters());
	}
	
	public String getInitFunction() {
		return getInitFunction(new PageParameters());
	}
	
	public String getInitFunction(PageParameters pp) {
		String initStr = null;
		StringValue type = pp.get("swf");
		if (SWF_TYPE_NETWORK.equals(type.toString())) {
			String swf = String.format("networktesting%s.swf10.swf", DEVELOPMENT == getApplication().getConfigurationType() ? "debug" : "")
					+ new PageParametersEncoder().encodePageParameters(pp);
			initStr = String.format("initSwf('%s');", swf);
		}
		return initStr;
	}
	
	public SwfPanel(String id, PageParameters pp) {
		super(id);
		add(new Label("init", getInitFunction(pp)).setEscapeModelStrings(false));
	}

	private static ResourceReference newResourceReference() {
		return new JavaScriptResourceReference(SwfPanel.class, "swf-functions.js");
	}
	
	@Override
	public void renderHead(IHeaderResponse response) {
		super.renderHead(response);
		response.render(new PriorityHeaderItem(JavaScriptHeaderItem.forReference(newResourceReference())));
		response.render(new PriorityHeaderItem(JavaScriptHeaderItem.forUrl("js/history.js")));
		response.render(new PriorityHeaderItem(JavaScriptHeaderItem.forUrl("js/openmeetings_functions.js")));
		response.render(new PriorityHeaderItem(CssHeaderItem.forUrl("css/history.css")));
	}
}
