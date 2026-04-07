package com.terpinheimer.party;

import com.terpinheimer.TerpinheimerConfig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.PartyChanged;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.util.Text;

/**
 * Shares NPC / PvP loot with RuneLite party members (same websocket party as Party Panel).
 * Your own drops are written to the list immediately (the party server often does not echo
 * your messages back to you).
 */
@Singleton
public class PartyLootTracker
{
	private static final int MAX_ROWS = 200;
	private static final int MAX_STACKS_PER_MESSAGE = 28;
	private static final long DEDUP_WINDOW_MS = 1_500L;

	private final Client client;
	private final TerpinheimerConfig config;
	private final PartyService partyService;
	private final WSClient wsClient;
	private final ItemManager itemManager;

	private final ArrayDeque<PartyLootRow> rows = new ArrayDeque<>();
	private final AtomicLong nextRowId = new AtomicLong(1L);
	private final Object dedupLock = new Object();
	private long dedupAtMs;
	private String dedupKey;

	private Runnable uiRefresh;

	@Inject
	PartyLootTracker(
		Client client,
		TerpinheimerConfig config,
		PartyService partyService,
		WSClient wsClient,
		ItemManager itemManager)
	{
		this.client = client;
		this.config = config;
		this.partyService = partyService;
		this.wsClient = wsClient;
		this.itemManager = itemManager;
	}

	public void start()
	{
		wsClient.registerMessage(TerpinheimerPartyLootMessage.class);
	}

	public void stop()
	{
		wsClient.unregisterMessage(TerpinheimerPartyLootMessage.class);
		synchronized (rows)
		{
			rows.clear();
		}
	}

	public void setUiRefresh(Runnable uiRefresh)
	{
		this.uiRefresh = uiRefresh;
	}

	/** Terpinheimer sidebar: show Group tab only when configured and in a party passphrase session. */
	public boolean isPartyLootTabVisible()
	{
		return config.partyLootShare() && partyService.isInParty();
	}

	public void syncVisibility()
	{
		fireUi();
	}

	public List<PartyLootRow> snapshotRows()
	{
		synchronized (rows)
		{
			return new ArrayList<>(rows);
		}
	}

	/** Sum of {@link PartyLootRow#getValueGp()} for rows still in this log (local only). */
	public long sumLoggedLootValueGp()
	{
		synchronized (rows)
		{
			long t = 0L;
			for (PartyLootRow r : rows)
			{
				t += r.getValueGp();
			}
			return t;
		}
	}

	/**
	 * Party member count from the RuneLite party session (same party as Party Panel), for split lines.
	 * At least 1 to avoid divide-by-zero.
	 */
	public int getPartySizeForSplit()
	{
		List<?> members = partyService.getMembers();
		int n = members == null ? 0 : members.size();
		return Math.max(1, n);
	}

	public void clearRows()
	{
		synchronized (rows)
		{
			rows.clear();
		}
		fireUi();
	}

	/** Remove one feed entry (local UI only). */
	public void removeRow(long rowId)
	{
		synchronized (rows)
		{
			rows.removeIf(r -> r.getId() == rowId);
		}
		fireUi();
	}

	private void fireUi()
	{
		if (uiRefresh != null)
		{
			SwingUtilities.invokeLater(uiRefresh);
		}
	}

	@Subscribe
	public void onPartyChanged(PartyChanged event)
	{
		clearRows();
		fireUi();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged ev)
	{
		if (!"terpinheimer".equals(ev.getGroup()))
		{
			return;
		}
		if ("partyLootShare".equals(ev.getKey()))
		{
			if (!config.partyLootShare())
			{
				clearRows();
			}
			fireUi();
		}
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		if (!shouldShareLoot())
		{
			return;
		}
		NPC npc = event.getNpc();
		String src = npc != null ? Text.removeTags(npc.getName()) : "NPC";
		shareLoot(src, event.getItems());
	}

	/**
	 * OSRS reports many kills here via the loot-tracker script; often pairs with {@link NpcLootReceived}
	 * so we dedupe in {@link #shareLoot}.
	 */
	@Subscribe
	public void onServerNpcLoot(ServerNpcLoot event)
	{
		if (!shouldShareLoot())
		{
			return;
		}
		String src = event.getComposition() != null
			? Text.removeTags(event.getComposition().getName()) : "NPC";
		if (src.isEmpty() || "null".equalsIgnoreCase(src))
		{
			src = "NPC";
		}
		shareLoot(src, event.getItems());
	}

	@Subscribe
	public void onPlayerLootReceived(PlayerLootReceived event)
	{
		if (!shouldShareLoot())
		{
			return;
		}
		Player p = event.getPlayer();
		String src = p != null ? Text.removeTags(p.getName()) : "Player loot";
		shareLoot(src, event.getItems());
	}

	@Subscribe
	public void onTerpinheimerPartyLootMessage(TerpinheimerPartyLootMessage message)
	{
		if (!config.partyLootShare() || !partyService.isInParty())
		{
			return;
		}
		PartyMember local = partyService.getLocalMember();
		if (local != null && message.getMemberId() == local.getMemberId())
		{
			return;
		}
		PartyMember member = partyService.getMemberById(message.getMemberId());
		String who = member != null && member.getDisplayName() != null
			? member.getDisplayName() : "Party member";
		appendRowsForStacks(who, message.getSrc() != null ? message.getSrc() : "—",
			message.getIds(), message.getQty(),
			message.getT() > 0 ? message.getT() : System.currentTimeMillis());
	}

	private boolean shouldShareLoot()
	{
		return config.partyLootShare()
			&& partyService.isInParty()
			&& client.getGameState() == GameState.LOGGED_IN;
	}

	private void shareLoot(String source, Collection<ItemStack> stacks)
	{
		if (stacks == null || stacks.isEmpty())
		{
			return;
		}
		List<ItemStack> list = new ArrayList<>(stacks);
		int n = Math.min(list.size(), MAX_STACKS_PER_MESSAGE);
		int[] ids = new int[n];
		int[] qty = new int[n];
		for (int i = 0; i < n; i++)
		{
			ItemStack s = list.get(i);
			ids[i] = s.getId();
			qty[i] = s.getQuantity();
		}
		String key = buildDedupKey(source, ids, qty);
		synchronized (dedupLock)
		{
			long now = System.currentTimeMillis();
			if (key.equals(dedupKey) && now - dedupAtMs < DEDUP_WINDOW_MS)
			{
				return;
			}
			dedupKey = key;
			dedupAtMs = now;
		}
		long when = System.currentTimeMillis();
		String self = localDisplayName();
		appendRowsForStacks(self, source != null ? source : "—", ids, qty, when);
		partyService.send(new TerpinheimerPartyLootMessage(source, ids, qty, when));
	}

	private static String buildDedupKey(String source, int[] ids, int[] qty)
	{
		StringBuilder sb = new StringBuilder(source != null ? source : "");
		sb.append('|');
		for (int i = 0; i < ids.length; i++)
		{
			sb.append(ids[i]).append('x').append(qty[i]).append(';');
		}
		return sb.toString();
	}

	private String localDisplayName()
	{
		PartyMember local = partyService.getLocalMember();
		if (local != null && local.getDisplayName() != null && !local.getDisplayName().isEmpty())
		{
			return local.getDisplayName();
		}
		if (client.getLocalPlayer() != null)
		{
			return Text.removeTags(client.getLocalPlayer().getName());
		}
		return "You";
	}

	/** One {@link PartyLootRow} per item stack so the table shows each drop on its own line. */
	private void appendRowsForStacks(String who, String src, int[] ids, int[] qty, long when)
	{
		if (ids == null || qty == null || ids.length != qty.length || ids.length == 0)
		{
			return;
		}
		String sourceNorm = src != null ? src : "—";
		synchronized (rows)
		{
			for (int i = 0; i < ids.length; i++)
			{
				int[] oneId = {ids[i]};
				int[] oneQty = {qty[i]};
				List<String> lines = formatStacks(oneId, oneQty);
				long gp = totalStackValueGp(oneId, oneQty);
				long rowId = nextRowId.getAndIncrement();
				rows.addLast(new PartyLootRow(rowId, when, who, sourceNorm, lines, gp));
			}
			while (rows.size() > MAX_ROWS)
			{
				rows.removeFirst();
			}
		}
		fireUi();
	}

	private long totalStackValueGp(int[] ids, int[] qty)
	{
		if (ids == null || qty == null || ids.length != qty.length)
		{
			return 0L;
		}
		long t = 0L;
		for (int i = 0; i < ids.length; i++)
		{
			int unit = itemManager.getItemPrice(ids[i]);
			t += (long) unit * qty[i];
		}
		return t;
	}

	private List<String> formatStacks(int[] ids, int[] qty)
	{
		if (ids == null || qty == null || ids.length != qty.length)
		{
			return Collections.emptyList();
		}
		List<String> out = new ArrayList<>(ids.length);
		for (int i = 0; i < ids.length; i++)
		{
			String name = itemManager.getItemComposition(ids[i]).getName();
			out.add(String.format("%,d x %s", qty[i], name));
		}
		return out;
	}
}
