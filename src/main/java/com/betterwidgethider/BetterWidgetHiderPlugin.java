/*
 * Copyright (c) 2026, Jake Vollkommer
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.betterwidgethider;

import com.google.inject.Provides;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.Value;
import net.runelite.api.Client;
import net.runelite.api.events.ClientTick;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;
import net.runelite.client.util.LinkBrowser;

@PluginDescriptor(
	name = "Better Widget Hider",
	description = "Hide specific parts of game interfaces (HUDs, overlays) by widget ID",
	tags = {"jake", "widget", "widgets", "interface", "hide", "hider", "hud", "overlay", "clean", "declutter", "component", "id", "gotr"}
)
public class BetterWidgetHiderPlugin extends Plugin
{
	private List<WidgetEntry> entriesToHide = Collections.emptyList();

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private BetterWidgetHiderConfig config;

	// The config panel cannot host real buttons, so the Feedback "buttons" are checkboxes
	// that act as buttons: any click of the box, tick or untick, opens the link.
	private void handleFeedbackButton(ConfigChanged event)
	{
		if (event.getNewValue() == null)
		{
			return;
		}

		if ("suggestButton".equals(event.getKey()))
		{
			LinkBrowser.browse("https://github.com/jakevollkommer/better-widget-hider/issues");
			return;
		}

		if ("supportButton".equals(event.getKey()))
		{
			LinkBrowser.browse("https://ko-fi.com/jakevollkommer");
		}
	}

	@Provides
	BetterWidgetHiderConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BetterWidgetHiderConfig.class);
	}

	@Override
	protected void startUp()
	{
		entriesToHide = parseEntries(config.widgetIds());
	}

	@Override
	protected void shutDown()
	{
		List<WidgetEntry> entriesToRestore = entriesToHide;
		entriesToHide = Collections.emptyList();
		clientThread.invokeLater(() -> entriesToRestore.forEach(this::showWidget));
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!BetterWidgetHiderConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}

		List<WidgetEntry> previousEntries = entriesToHide;
		entriesToHide = parseEntries(config.widgetIds());
		// un-hide anything that was removed from the list; the game's own scripts take
		// visibility back over from there
		List<WidgetEntry> removedEntries = previousEntries.stream()
			.filter(entry -> !entriesToHide.contains(entry))
			.collect(Collectors.toList());
		clientThread.invokeLater(() -> removedEntries.forEach(this::showWidget));
		handleFeedbackButton(event);
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		// interfaces are rebuilt by clientscripts whenever their values update, which
		// resets the hidden flag, so re-hide every client tick
		entriesToHide.forEach(this::hideWidget);
	}

	private void hideWidget(WidgetEntry entry)
	{
		setWidgetHidden(entry, true);
	}

	private void showWidget(WidgetEntry entry)
	{
		setWidgetHidden(entry, false);
	}

	private void setWidgetHidden(WidgetEntry entry, boolean hidden)
	{
		Widget widget = resolveWidget(entry);
		boolean alreadyInDesiredState = widget == null || widget.isHidden() == hidden;
		if (alreadyInDesiredState)
		{
			return;
		}

		widget.setHidden(hidden);
	}

	@Nullable
	private Widget resolveWidget(WidgetEntry entry)
	{
		Widget widget = client.getWidget(WidgetUtil.packComponentId(entry.getGroup(), entry.getChild()));
		if (widget == null || !entry.hasDynamicChildIndex())
		{
			return widget;
		}

		return widget.getChild(entry.getDynamicChildIndex());
	}

	private static List<WidgetEntry> parseEntries(String widgetIds)
	{
		String commaSeparated = widgetIds.replace('\n', ',');
		return Text.fromCSV(commaSeparated).stream()
			.map(BetterWidgetHiderPlugin::parseEntry)
			.filter(Objects::nonNull)
			.collect(Collectors.toList());
	}

	@Nullable
	private static WidgetEntry parseEntry(String token)
	{
		String[] segments = token.trim().split("\\.");
		boolean isGroupChildForm = segments.length == 2 || segments.length == 3;
		if (!isGroupChildForm)
		{
			return null;
		}

		try
		{
			int group = Integer.parseInt(segments[0]);
			int child = Integer.parseInt(segments[1]);
			boolean hasDynamicChildSegment = segments.length == 3;
			int dynamicChildIndex = hasDynamicChildSegment
				? Integer.parseInt(segments[2])
				: WidgetEntry.NO_DYNAMIC_CHILD;
			return new WidgetEntry(group, child, dynamicChildIndex);
		}
		catch (NumberFormatException invalidNumber)
		{
			return null;
		}
	}

	@Value
	private static class WidgetEntry
	{
		static final int NO_DYNAMIC_CHILD = -1;

		int group;
		int child;
		int dynamicChildIndex;

		boolean hasDynamicChildIndex()
		{
			return dynamicChildIndex != NO_DYNAMIC_CHILD;
		}
	}
}
