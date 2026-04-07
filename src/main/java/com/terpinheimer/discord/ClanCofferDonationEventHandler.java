package com.terpinheimer.discord;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ClanCofferDonationEventHandler
{
	private static final Logger log = LoggerFactory.getLogger(ClanCofferDonationEventHandler.class);

	/** Fallback if Jagex tweaks spacing or wording but keeps {@code Name has deposit…} shape. */
	private static final Pattern NAME_BEFORE_COFFER_DEPOSIT = Pattern.compile(
		"^(.+?)\\s+has\\s+deposit(?:ed)?\\b",
		Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

	private static final String CLAN_COFFER_WEBHOOK_URL =
		"https://discord.com/api/webhooks/1490706578884526080/qCBOTE9Gtm93dKB9hY0pXJ9RceCnS03jif44cOmqMA7bsTVHOBjomGd2Hw7I-f9wvATA";

	private static final String COFFER_DONATION_FOOTNOTE = "Thank You for your Contribution to future Events!";

	private final Client client;
	private final WebhookMessageBuilder messageBuilder;
	private final WebhookDispatcher dispatcher;

	@Inject
	ClanCofferDonationEventHandler(
		Client client,
		WebhookMessageBuilder messageBuilder,
		WebhookDispatcher dispatcher)
	{
		this.client = client;
		this.messageBuilder = messageBuilder;
		this.dispatcher = dispatcher;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		try
		{
			if (!isCofferRelevantChatType(event.getType()) || client.getGameState() != GameState.LOGGED_IN)
			{
				return;
			}

			String message = Text.removeTags(event.getMessage());
			if (!isLocalPlayerCofferDeposit(message))
			{
				return;
			}

			Player local = client.getLocalPlayer();
			String playerName = local != null ? Text.removeTags(local.getName()) : "Player";
			String description = message + "\n\n" + COFFER_DONATION_FOOTNOTE;
			String json = messageBuilder.toWebhookJson(
				messageBuilder.dinkChromeEmbed(
					0x2ECC71,
					playerName,
					"Clan Coffer Donation",
					description,
					null,
					null
				)
			);

			dispatcher.enqueue(new WebhookPayload(json, null, CLAN_COFFER_WEBHOOK_URL));
		}
		catch (RuntimeException e)
		{
			log.warn("Terpinheimer: clan coffer webhook skipped: {}", e.toString());
		}
	}

	/**
	 * Coffer deposit/withdraw lines are {@link ChatMessageType#CLAN_MESSAGE} (or GIM / guest clan
	 * system), not {@link ChatMessageType#GAMEMESSAGE}. Those were previously filtered out entirely.
	 */
	private static boolean isCofferRelevantChatType(ChatMessageType type)
	{
		if (type == null)
		{
			return false;
		}
		switch (type)
		{
			case GAMEMESSAGE:
			case ENGINE:
			case MESBOX:
			case DIALOG:
			case CONSOLE:
			case PLAYERRELATED:
			case CLAN_MESSAGE:
			case CLAN_GIM_MESSAGE:
			case CLAN_GUEST_MESSAGE:
				return true;
			default:
				return false;
		}
	}

	/**
	 * OSRS uses: {@code [ClanName] YourName has deposited 1,000 coins into the coffer.}
	 * Only the local player's deposits (not withdrawals, not other members) trigger the webhook.
	 */
	private boolean isLocalPlayerCofferDeposit(String rawMessage)
	{
		if (rawMessage == null || rawMessage.isEmpty())
		{
			return false;
		}

		Player local = client.getLocalPlayer();
		if (local == null)
		{
			return false;
		}
		String localStd = Text.standardize(Text.removeTags(local.getName()));

		String plain = Text.removeTags(rawMessage).trim();
		if (plain.startsWith("[") && plain.contains("]"))
		{
			int close = plain.indexOf(']');
			if (close > 0)
			{
				plain = plain.substring(close + 1).trim();
			}
		}
		plain = plain.replaceAll("[\\s\\u00A0]+", " ").trim();

		String lower = plain.toLowerCase(Locale.ENGLISH);
		if (!lower.contains("coffer"))
		{
			return false;
		}
		if (lower.contains("withdraw"))
		{
			return false;
		}
		boolean depositWording = lower.contains("deposit") || lower.contains("donat");
		if (!depositWording)
		{
			return false;
		}

		if (lower.startsWith("you ") || lower.startsWith("you have ") || lower.startsWith("you've "))
		{
			return true;
		}

		String namePart = null;
		int idx = lower.indexOf(" has deposited ");
		if (idx > 0)
		{
			namePart = plain.substring(0, idx).trim();
		}
		if (namePart == null || namePart.isEmpty())
		{
			idx = lower.indexOf(" has donated ");
			if (idx > 0)
			{
				namePart = plain.substring(0, idx).trim();
			}
		}
		if (namePart == null || namePart.isEmpty())
		{
			idx = lower.indexOf(" has added ");
			if (idx > 0)
			{
				namePart = plain.substring(0, idx).trim();
			}
		}
		if (namePart == null || namePart.isEmpty())
		{
			Matcher m = NAME_BEFORE_COFFER_DEPOSIT.matcher(plain);
			if (m.find())
			{
				namePart = m.group(1).trim();
			}
		}
		if (namePart == null || namePart.isEmpty())
		{
			return false;
		}
		return Text.standardize(namePart).equals(localStd);
	}
}
