package com.terpinheimer.discord;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Validates Discord webhook URLs before dispatch.
 */
public final class WebhookUrlValidator
{
	private WebhookUrlValidator()
	{
	}

	public static boolean isValid(String url)
	{
		if (url == null)
		{
			return false;
		}
		String t = url.trim();
		if (t.isEmpty())
		{
			return false;
		}
		if (!t.startsWith("https://"))
		{
			return false;
		}
		try
		{
			URI u = new URI(t);
			if (!"https".equalsIgnoreCase(u.getScheme()))
			{
				return false;
			}
			String host = u.getHost();
			if (host == null)
			{
				return false;
			}
			String h = host.toLowerCase();
			boolean discordHost = h.equals("discord.com")
				|| h.equals("discordapp.com")
				|| h.equals("canary.discord.com")
				|| h.equals("ptb.discord.com");
			if (!discordHost)
			{
				return false;
			}
			String path = u.getPath();
			if (path == null || path.isEmpty())
			{
				return false;
			}
			// /api/webhooks/{id}/{token} or /api/v10/webhooks/{id}/{token}
			return path.matches("/api(?:/v\\d+)?/webhooks/\\d+/[^/]+");
		}
		catch (URISyntaxException e)
		{
			return false;
		}
	}
}
