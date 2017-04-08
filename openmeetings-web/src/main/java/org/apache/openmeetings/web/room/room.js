/**
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
function initVideo(_options) {
	return; //commented until video is implemented
	var options = $.extend({bgcolor: "#ffffff"
		, resolutions: JSON.stringify([{label: "4:3 (~6 KByte/sec)", width: 40, height: 30}
			, {label: "4:3 (~12 KByte/sec)", width: 80, height: 60}
			, {label: "4:3 (~20 KByte/sec)", width: 120, height: 90, "default": true}
			, {label: "QQVGA 4:3 (~36 KByte/sec)", width: 160, height: 120}
			, {label: "4:3 (~40 KByte/sec)", width: 240, height: 180}
			, {label: "HVGA 4:3 (~56 KByte/sec)", width: 320, height: 240}
			, {label: "4:3  (~60 KByte/sec)", width: 480, height: 360}
			, {label: "4:3 (~68 KByte/sec)", width: 640, height: 480}
			, {label: "XGA 4:3", width: 1024, height: 768}
			, {label: "16:9", width: 256, height: 150}
			, {label: "WQVGA 9:5", width: 432, height: 240}
			, {label: "pseudo 16:9", width: 480, height: 234}
			, {label: "16:9", width: 512, height: 300}
			, {label: "nHD 16:9", width: 640, height: 360}
			, {label: "16:9", width: 1024, height: 600}])
		}, _options);
	var type = 'application/x-shockwave-flash';
	var src = 'public/main.swf?cache' + new Date().getTime();
	var r = $('<div class="room video">').attr("id", "video" + options.uid);
	var o = $('<object>').attr('type', type).attr('data', src).attr('width', 640).attr('height', 480);
	o.append($('<param>').attr('name', 'quality').attr('value', 'best'))
		.append($('<param>').attr('name', 'wmode').attr('value', 'transparent'))
		.append($('<param>').attr('name', 'allowscriptaccess').attr('value', 'sameDomain'))
		.append($('<param>').attr('name', 'allowfullscreen').attr('value', 'false'))
		.append($('<param>').attr('name', 'flashvars').attr('value', $.param(options)));
	$('#roomMenu').parent().append(r.append(o));
	/*
			.attr('wmode', 'window').attr('allowfullscreen', true)
			.attr('width', options.width).attr('height', options.height)
			.attr('id', 'lzapp').attr('name', 'lzapp')
			.attr('flashvars', escape($.param(options)))
			.attr('swliveconnect', true).attr('align', 'middle')
			.attr('allowscriptaccess', 'sameDomain').attr('type', 'application/x-shockwave-flash')
			.attr('pluginspage', 'http://www.macromedia.com/go/getflashplayer')
	*/
	r.dialog({dialogClass: "video"});
}

function setRoomSizes() {
	var sb = $(".room.sidebar.left")
		, w = $(window).width() - sb.width() - 5
		, h = $(window).height() - $('#menu').height()
		, p = sb.find('.tabs');
	sb.height(h);
	var hh = h - 5;
	p.height(hh);
	$(".user.list", p).height(hh - $("ul", p).height() - $(".user.header", p).height() - 5);
	if (!!WbArea) {
		WbArea.resize(sb.width(), w, h);
	}
}
function roomReload(event, ui) {
	window.location.reload();
}
function roomClosed(jqEvent, msg) {
	roomUnload();
	$(".room.holder").remove();
	$("#chatPanel").remove();
	var dlg = $('#disconnected-dlg');
	dlg.dialog({
		modal: true
		, close: roomReload
		, buttons: [
			{
				text: dlg.data('reload')
				, icons: {primary: "ui-icon-refresh"}
				, click: function() {
					$(this).dialog("close");
				}
			}
		]
	});
}
function roomLoad() {
	$(".room.sidebar.left").ready(function() {
		setRoomSizes();
	});
	$(window).on('resize.openmeetings', function() {
		setRoomSizes();
	});
	$(".room.sidebar.left").resizable({
		handles: "e"
		, stop: function(event, ui) {
			setRoomSizes();
		}
	});
	VideoSettings.init();
	Wicket.Event.subscribe("/websocket/closed", roomClosed);
}
function roomUnload() {
	$(window).off('resize.openmeetings');
	Wicket.Event.unsubscribe("/websocket/closed", roomClosed);
	if (!!WbArea) {
		WbArea.destroy();
	}
	VideoSettings.close();
}
function startPrivateChat(el) {
	Chat.addTab('chatTab-u' + el.parent().parent().data("userid"), el.parent().parent().find('.user.name').text());
	Chat.open();
	$('#chatMessage .wysiwyg-editor').click();
}
var VideoSettings = (function() {
	var self = {}, vs, lm;
	function _init() {
		vs = $('#video-settings');
		lm = vs.find('.level-meter');
		vs.dialog({
			classes: {
				'ui-dialog': 'ui-corner-all video'
			}
			, width: 640
			, autoOpen: false
			, buttons: [
				{
					text: vs.data('btn-save')
					, icons: {
						primary: "ui-icon-disk"
					}
					, click: function() {
						vs.dialog("close");
					}
				}
				, {
					text: vs.data('btn-cancel')
					, click: function() {
						vs.dialog("close");
					}
				}
			]
		});
		lm.progressbar({ value: 0 });
		vs.find('button').button();
	}
	function _open(interview) {
		var rr = vs.find('.cam-resolution').parent('.sett-row');
		if (interview) {
			rr.show();
		} else {
			rr.hide();
		}
		vs.dialog('open');
	}
	return {
		init: _init
		, open: _open
		, close: function() { vs.dialog('close'); }
	};
})();

/***** functions required by SIP   ******/
function sipBtnClick() {
	var txt = $('.sip-number');
	txt.val(txt.val() + $(this).data('value'));
}
function sipBtnEraseClick() {
	var txt = $('.sip-number');
	var t = txt.val();
	if (!!t) {
		txt.val(t.substring(0, t.length -1));
	}
}
function sipGetKey(evt) {
	var k = -1;
	if (evt.keyCode > 47 && evt.keyCode < 58) {
		k = evt.keyCode - 48;
	}
	if (evt.keyCode > 95 && evt.keyCode < 106) {
		k = evt.keyCode - 96;
	}
	return k;
}
function sipKeyDown(evt) {
	var k = sipGetKey(evt);
	if (k > 0) {
		$('#sip-dialer-btn-' + k).addClass('ui-state-active');
	}
}
function sipKeyUp(evt) {
	var k = sipGetKey(evt);
	if (k > 0) {
		$('#sip-dialer-btn-' + k).removeClass('ui-state-active');
	}
}

/***** functions required by SWF   ******/
function audioActivity(uid, active) {
	var u = $('#user' + uid + ' .audio-activity.ui-icon');
	if (active) {
		u.addClass("speaking");
	} else {
		u.removeClass("speaking");
	}
}
function typingActivity(uid, active) {
	var u = $('#user' + uid + ' .typing-activity.ui-icon');
	if (active) {
		u.addClass("typing");
	} else {
		u.removeClass("typing");
	}
}
