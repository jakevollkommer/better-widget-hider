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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.ClientTick;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;

@PluginDescriptor(
	name = "Better Widget Hider",
	description = "Hide specific parts of game interfaces (HUDs, overlays) by widget ID",
	tags = {"widget", "interface", "hide", "hider", "hud", "overlay", "gotr"}
)
@Slf4j
public class BetterWidgetHiderPlugin extends Plugin
{
	private List<WidgetEntry> entries = Collections.emptyList();

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private BetterWidgetHiderConfig config;

	@Provides
	BetterWidgetHiderConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BetterWidgetHiderConfig.class);
	}

	@Override
	protected void startUp()
	{
		entries = parse(config.widgetIds());
	}

	@Override
	protected void shutDown()
	{
		List<WidgetEntry> toRestore = entries;
		entries = Collections.emptyList();
		clientThread.invokeLater(() -> toRestore.forEach(e -> setHidden(e, false)));
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!BetterWidgetHiderConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}

		List<WidgetEntry> previous = entries;
		entries = parse(config.widgetIds());
		// un-hide anything that was removed from the list; the game's own scripts take
		// visibility back over from there
		List<WidgetEntry> removed = new ArrayList<>(previous);
		removed.removeAll(entries);
		clientThread.invokeLater(() -> removed.forEach(e -> setHidden(e, false)));
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		// interfaces are rebuilt by clientscripts whenever their values update, which
		// resets the hidden flag, so re-hide every client tick
		for (WidgetEntry entry : entries)
		{
			setHidden(entry, true);
		}
	}

	private void setHidden(WidgetEntry entry, boolean hidden)
	{
		Widget widget = client.getWidget(entry.getGroup() << 16 | entry.getChild());
		if (widget != null && entry.getIndex() >= 0)
		{
			widget = widget.getChild(entry.getIndex());
		}

		if (widget != null && widget.isHidden() != hidden)
		{
			widget.setHidden(hidden);
		}
	}

	private static List<WidgetEntry> parse(String widgetIds)
	{
		List<WidgetEntry> parsed = new ArrayList<>();
		for (String token : Text.fromCSV(widgetIds.replace('\n', ',')))
		{
			String[] parts = token.trim().split("\\.");
			if (parts.length < 2 || parts.length > 3)
			{
				continue;
			}

			try
			{
				parsed.add(new WidgetEntry(
					Integer.parseInt(parts[0]),
					Integer.parseInt(parts[1]),
					parts.length == 3 ? Integer.parseInt(parts[2]) : -1));
			}
			catch (NumberFormatException ex)
			{
				log.debug("ignoring invalid widget entry '{}'", token);
			}
		}
		return parsed;
	}

	@Value
	private static class WidgetEntry
	{
		int group;
		int child;
		int index;
	}
}
