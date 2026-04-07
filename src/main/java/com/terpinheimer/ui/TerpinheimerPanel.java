package com.terpinheimer.ui;

import com.terpinheimer.TerpinheimerConfig;
import com.terpinheimer.TerpinheimerPlugin;
import com.terpinheimer.attendance.ClanAttendanceTracker;
import com.terpinheimer.discord.WikiLinks;
import com.terpinheimer.party.PartyLootRow;
import com.terpinheimer.party.PartyLootTracker;
import com.terpinheimer.site.ClanCalendarSummaryService.WebEventRow;
import com.terpinheimer.wom.WomLeaderboardModels;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JViewport;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;

public class TerpinheimerPanel extends PluginPanel
{
	private static final String CARD_HOME = "home";
	private static final String CARD_SOTW = "sotw";
	private static final String CARD_BOTW = "botw";
	private static final String CARD_WEB_EVENTS = "webevents";
	private static final String CARD_ATTENDANCE = "attendance";
	private static final String CARD_GROUP = "group";
	/** Bottom bar tabs only; attendance is opened from Home (no duplicate tab). */
	private static final String[] TAB_CARD_NAMES = {
		CARD_HOME, CARD_SOTW, CARD_BOTW, CARD_WEB_EVENTS
	};
	private static final int TAB_GROUP_INDEX = 4;
	/** Group loot table: column index for per-row delete. */
	private static final int GROUP_COL_REMOVE = 3;
	private static final int NO_TAB_SELECTED = -1;
	private static final String LOGO = "/terpinheimer-logo.png";
	/** Home events table: row 2 is Website (opens clan calendar URL). */
	private static final int HOME_ROW_WEBSITE = 2;

	private final TerpinheimerPlugin plugin;
	private final TerpinheimerConfig config;
	private final PartyLootTracker partyLootTracker;
	private final CardLayout cards = new CardLayout();
	private final JPanel cardHost = new JPanel(cards);

	private int currentTab = 0;
	private final JButton[] tabButtons = new JButton[4];
	private final JButton groupTabButton;
	private JPanel southTabBar;
	private boolean groupTabBarVisible;
	private DefaultTableModel groupLootModel;
	private JTable groupLootTable;
	private final List<Long> groupLootRowIds = new ArrayList<>();
	private JLabel groupLootTotalsLabel;

	private final JTextArea attendanceReportArea = new JTextArea();
	private final JButton attendanceStartStopBtn = FluxUi.pillButton("Start event");
	private final JButton attendanceCopyBtn = FluxUi.pillButton("Copy to clipboard");

	private final JLabel webEventsStatus = new JLabel("—", SwingConstants.CENTER);
	private final JLabel webEventsMeta = new JLabel(" ");
	private final JLabel webEventsLastUp = new JLabel(" ");
	private final JTable webEventsTable = new JTable();
	private DefaultTableModel webEventsModel;

	private final JTextArea announcementsArea = new JTextArea();

	private final JLabel sotwWinner = new JLabel(" ", SwingConstants.CENTER);
	private final JLabel sotwCountdown = new JLabel(" ", SwingConstants.CENTER);
	private final JLabel sotwMeta = new JLabel(" ");
	private final JLabel sotwLastUp = new JLabel(" ");
	private final JTable sotwTable = new JTable();
	private final List<String> sotwTooltips = new ArrayList<>();
	private DefaultTableModel sotwModel;

	private final JLabel botwWinner = new JLabel(" ", SwingConstants.CENTER);
	private final JLabel botwCountdown = new JLabel(" ", SwingConstants.CENTER);
	private final JLabel botwMeta = new JLabel(" ");
	private final JLabel botwLastUp = new JLabel(" ");
	private final JTable botwTable = new JTable();
	private final List<String> botwTooltips = new ArrayList<>();
	private DefaultTableModel botwModel;

	private WomLeaderboardModels.CompetitionSnapshot lastSotw =
		WomLeaderboardModels.CompetitionSnapshot.empty("");
	private WomLeaderboardModels.CompetitionSnapshot lastBotw =
		WomLeaderboardModels.CompetitionSnapshot.empty("");
	private String lastAnnouncements = "";

	private DefaultTableModel homeEventsModel;
	private JTable homeEventsTable;

	private Timer tickTimer;

	public TerpinheimerPanel(TerpinheimerPlugin plugin, TerpinheimerConfig config, PartyLootTracker partyLootTracker)
	{
		this.plugin = plugin;
		this.config = config;
		this.partyLootTracker = partyLootTracker;
		setLayout(new BorderLayout());
		setBackground(FluxUi.BG);

		cardHost.setBackground(FluxUi.BG);
		cardHost.add(buildHomeTab(), CARD_HOME);
		cardHost.add(buildCompetitionTab(true), CARD_SOTW);
		cardHost.add(buildCompetitionTab(false), CARD_BOTW);
		cardHost.add(buildWebEventsTab(), CARD_WEB_EVENTS);
		cardHost.add(buildAttendanceTab(), CARD_ATTENDANCE);
		cardHost.add(buildGroupTab(), CARD_GROUP);
		add(cardHost, BorderLayout.CENTER);

		southTabBar = new JPanel();
		southTabBar.setBackground(FluxUi.BG);
		southTabBar.setBorder(new EmptyBorder(4, 4, 6, 4));
		tabButtons[0] = FluxUi.tabToggle("Home", true);
		tabButtons[1] = FluxUi.tabToggle("SOTW", false);
		tabButtons[2] = FluxUi.tabToggle("BOTW", false);
		tabButtons[3] = FluxUi.tabToggle("Web Events", false);
		tabButtons[3].setToolTipText("Clan calendar on your website");
		groupTabButton = FluxUi.tabToggle("Group", false);
		groupTabButton.setToolTipText("Party loot (RuneLite party). Join a party with the Party plugin.");
		tabButtons[0].addActionListener(e -> selectTab(0));
		tabButtons[1].addActionListener(e -> selectTab(1));
		tabButtons[2].addActionListener(e -> selectTab(2));
		tabButtons[3].addActionListener(e -> selectTab(3));
		groupTabButton.addActionListener(e -> selectTab(TAB_GROUP_INDEX));
		for (int i = 0; i < tabButtons.length; i++)
		{
			refreshTabStyle(tabButtons[i], i == 0);
		}
		refreshTabStyle(groupTabButton, false);
		rebuildSouthTabBar();
		add(southTabBar, BorderLayout.SOUTH);

		plugin.getClanAttendanceTracker().setUiRefresh(this::syncAttendanceTab);

		tickTimer = new Timer(1000, e -> tickCountdowns());
		tickTimer.start();

		applyFromPlugin();
	}

	public void shutdown()
	{
		if (tickTimer != null)
		{
			tickTimer.stop();
		}
		plugin.getClanAttendanceTracker().clearUiRefresh();
	}

	public void applyFromPlugin()
	{
		SwingUtilities.invokeLater(() ->
		{
			lastSotw = plugin.getSotwSnapshot();
			lastBotw = plugin.getBotwSnapshot();
			lastAnnouncements = plugin.getAnnouncementsText();
			refreshAnnouncements();
			fillCompetitionUi(true);
			fillCompetitionUi(false);
			tickCountdowns();
		});
	}

	private void selectTab(int idx)
	{
		if (idx == TAB_GROUP_INDEX)
		{
			if (!groupTabBarVisible)
			{
				return;
			}
			currentTab = TAB_GROUP_INDEX;
			for (JButton tabButton : tabButtons)
			{
				refreshTabStyle(tabButton, false);
			}
			refreshTabStyle(groupTabButton, true);
			cards.show(cardHost, CARD_GROUP);
			revalidate();
			repaint();
			return;
		}
		if (idx < 0 || idx >= TAB_CARD_NAMES.length)
		{
			return;
		}
		currentTab = idx;
		for (int i = 0; i < tabButtons.length; i++)
		{
			refreshTabStyle(tabButtons[i], i == idx);
		}
		if (groupTabBarVisible)
		{
			refreshTabStyle(groupTabButton, false);
		}
		cards.show(cardHost, TAB_CARD_NAMES[idx]);
		revalidate();
		repaint();
	}

	private void rebuildSouthTabBar()
	{
		boolean showGroup = partyLootTracker.isPartyLootTabVisible();
		if (showGroup == groupTabBarVisible && southTabBar.getComponentCount() > 0)
		{
			return;
		}
		groupTabBarVisible = showGroup;
		southTabBar.removeAll();
		int cols = groupTabBarVisible ? 5 : 4;
		southTabBar.setLayout(new GridLayout(1, cols, 1, 0));
		for (JButton tabButton : tabButtons)
		{
			southTabBar.add(tabButton);
		}
		if (groupTabBarVisible)
		{
			southTabBar.add(groupTabButton);
		}
		southTabBar.revalidate();
		southTabBar.repaint();
	}

	/** Called from {@link PartyLootTracker} when party membership or loot rows change. */
	public void syncPartyGroupTabUi()
	{
		boolean wasGroup = groupTabBarVisible;
		rebuildSouthTabBar();
		refreshGroupLootTable();
		if (wasGroup && !groupTabBarVisible && currentTab == TAB_GROUP_INDEX)
		{
			selectTab(0);
		}
		else if (groupTabBarVisible)
		{
			if (currentTab == TAB_GROUP_INDEX)
			{
				refreshTabStyle(groupTabButton, true);
			}
		}
		revalidate();
		repaint();
	}

	private void refreshGroupLootTable()
	{
		if (groupLootModel == null)
		{
			return;
		}
		groupLootModel.setRowCount(0);
		groupLootRowIds.clear();
		int rowIdx = 0;
		for (PartyLootRow row : partyLootTracker.snapshotRows())
		{
			groupLootRowIds.add(row.getId());
			String cost = row.getValueGp() > 0
				? WikiLinks.formatGpCompact(row.getValueGp()) + " gp"
				: "—";
			groupLootModel.addRow(new Object[]{
				row.getPlayer(),
				row.getDropSummary(),
				cost,
				"Remove"
			});
			groupLootTable.setRowHeight(rowIdx, 28);
			rowIdx++;
		}
		refreshGroupLootTotals();
	}

	private void refreshGroupLootTotals()
	{
		if (groupLootTotalsLabel == null)
		{
			return;
		}
		int tw = FluxUi.textWidth();
		long total = partyLootTracker.sumLoggedLootValueGp();
		int partyN = partyLootTracker.getPartySizeForSplit();
		if (partyLootTracker.snapshotRows().isEmpty())
		{
			groupLootTotalsLabel.setText("<html><div style='width:" + tw + "px;color:#a8a8a8'>"
				+ "Total and per-person split appear here once loot is logged. <b>Party</b> count uses your "
				+ "RuneLite party (same as Party Panel).</div></html>");
			return;
		}
		long each = total / partyN;
		long rem = total % partyN;
		String totalStr = WikiLinks.formatGpCompact(total) + " gp";
		String eachStr = WikiLinks.formatGpCompact(each) + " gp";
		String remPart = rem > 0
			? " <span style='color:#888'>(" + rem + " gp remainder)</span>"
			: "";
		groupLootTotalsLabel.setText("<html><div style='width:" + tw + "px;color:#e0e0e0'>"
			+ "<b style='color:#ffcc00'>Total</b> " + totalStr
			+ " &nbsp;·&nbsp; <b style='color:#ffcc00'>Party</b> " + partyN
			+ " &nbsp;·&nbsp; <b style='color:#ffcc00'>Each</b> " + eachStr + remPart
			+ "</div></html>");
	}

	private JPanel buildGroupTab()
	{
		final int tw = FluxUi.textWidth();
		JPanel root = new JPanel(new BorderLayout());
		root.setBackground(FluxUi.BG);
		root.setBorder(new EmptyBorder(4, 6, 4, 6));

		JPanel north = new JPanel();
		north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
		north.setOpaque(false);

		JLabel title = new JLabel("<html><div style='width:" + tw + "px;text-align:center'><b>Party loot log</b></div></html>");
		title.setForeground(FluxUi.HEADER_GOLD);
		title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
		title.setAlignmentX(Component.CENTER_ALIGNMENT);
		north.add(title);

		JLabel blurb = new JLabel("<html><div style='width:" + tw + "px;color:#a8a8a8'>Join a RuneLite party (Party plugin) with the same passphrase as your friends. Your own NPC/PvP drops appear here immediately; other members need Terpinheimer with Party loot enabled to share. <b>Cost</b> uses GE guide prices. Click <b>Remove</b> on a row to delete it from this log only. The footer shows <b>Total</b> loot in this log and an equal <b>Each</b> split by party size.</div></html>");
		blurb.setAlignmentX(Component.CENTER_ALIGNMENT);
		north.add(blurb);

		JButton clearBtn = FluxUi.pillButton("Clear list");
		clearBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		clearBtn.addActionListener(e ->
		{
			partyLootTracker.clearRows();
			refreshGroupLootTable();
		});
		north.add(Box.createVerticalStrut(6));
		north.add(clearBtn);

		root.add(north, BorderLayout.NORTH);

		String[] cols = {"User", "Drop", "Cost", "Remove"};
		groupLootModel = new DefaultTableModel(cols, 0)
		{
			@Override
			public boolean isCellEditable(int r, int c)
			{
				return false;
			}
		};
		groupLootTable = new JTable(groupLootModel);
		FluxUi.styleDataTable(groupLootTable);
		TableColumnModel tcm = groupLootTable.getColumnModel();
		if (tcm.getColumnCount() >= 4)
		{
			tcm.getColumn(0).setPreferredWidth(Math.min(100, tw / 4));
			tcm.getColumn(1).setPreferredWidth(tw - 200);
			tcm.getColumn(2).setPreferredWidth(72);
			tcm.getColumn(3).setPreferredWidth(56);
		}
		groupLootTable.setRowHeight(32);
		groupLootTable.setFillsViewportHeight(true);
		groupLootTable.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				int r = groupLootTable.rowAtPoint(e.getPoint());
				int c = groupLootTable.columnAtPoint(e.getPoint());
				if (r < 0 || c != GROUP_COL_REMOVE || r >= groupLootRowIds.size())
				{
					return;
				}
				partyLootTracker.removeRow(groupLootRowIds.get(r));
			}
		});

		JScrollPane scroll = new JScrollPane(groupLootTable);
		scroll.setBorder(FluxUi.tableBorder());
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

		groupLootTotalsLabel = new JLabel(" ");
		groupLootTotalsLabel.setOpaque(false);
		groupLootTotalsLabel.setBorder(new EmptyBorder(8, 4, 4, 4));

		JPanel tableArea = new JPanel(new BorderLayout());
		tableArea.setBackground(FluxUi.BG);
		tableArea.setOpaque(true);
		tableArea.add(scroll, BorderLayout.CENTER);
		tableArea.add(groupLootTotalsLabel, BorderLayout.SOUTH);
		root.add(tableArea, BorderLayout.CENTER);

		refreshGroupLootTotals();

		return root;
	}

	/** Opens the embedded attendance view from Home (not a bottom-tab duplicate). */
	private void showClanAttendanceCard()
	{
		currentTab = NO_TAB_SELECTED;
		for (JButton tabButton : tabButtons)
		{
			refreshTabStyle(tabButton, false);
		}
		if (groupTabBarVisible)
		{
			refreshTabStyle(groupTabButton, false);
		}
		cards.show(cardHost, CARD_ATTENDANCE);
		revalidate();
		repaint();
	}

	private void refreshTabStyle(JButton b, boolean sel)
	{
		b.setBackground(sel ? FluxUi.TAB_SELECTED : FluxUi.TAB_NORMAL);
		BorderStyle.setTabBorder(b, sel);
	}

	private JPanel buildHomeTab()
	{
		final int tw = FluxUi.textWidth();

		JPanel root = new JPanel(new BorderLayout());
		root.setBackground(FluxUi.BG);
		root.setBorder(new EmptyBorder(4, 6, 4, 6));

		JPanel north = new JPanel();
		north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
		north.setOpaque(false);

		JLabel title = new JLabel("<html><div style='width:" + tw + "px;text-align:center'><b>Welcome to Terpinheimer!</b></div></html>");
		title.setForeground(FluxUi.HEADER_GOLD);
		title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
		title.setAlignmentX(Component.CENTER_ALIGNMENT);
		north.add(title);

		BufferedImage logo = readLogo();
		if (logo != null)
		{
			int w = Math.min(tw - 4, logo.getWidth());
			int h = logo.getHeight() * w / Math.max(1, logo.getWidth());
			JLabel img = new JLabel(new ImageIcon(ImageUtil.resizeImage(logo, w, h)));
			img.setAlignmentX(Component.CENTER_ALIGNMENT);
			north.add(img);
		}

		root.add(north, BorderLayout.NORTH);

		JPanel body = new JPanel(new GridBagLayout());
		body.setOpaque(false);
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.weightx = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.anchor = GridBagConstraints.NORTHWEST;
		gbc.insets = new Insets(0, 0, 0, 0);

		gbc.gridy = 0;
		body.add(FluxUi.sectionHeader("Announcements", tw), gbc);

		gbc.gridy = 1;
		gbc.insets = new Insets(0, 0, 6, 0);
		announcementsArea.setEditable(false);
		announcementsArea.setLineWrap(true);
		announcementsArea.setWrapStyleWord(true);
		announcementsArea.setTabSize(4);
		announcementsArea.setBackground(FluxUi.BG_PANEL);
		announcementsArea.setForeground(FluxUi.TEXT);
		announcementsArea.setBorder(BorderFactory.createCompoundBorder(
			FluxUi.tableBorder(), new EmptyBorder(6, 6, 6, 6)));
		announcementsArea.setRows(5);
		announcementsArea.setColumns(0);
		announcementsArea.setMinimumSize(new Dimension(50, 72));
		announcementsArea.setPreferredSize(new Dimension(0, 92));
		announcementsArea.addMouseWheelListener(this::forwardWheelToPluginScrollPane);
		body.add(announcementsArea, gbc);

		gbc.gridy = 2;
		gbc.insets = new Insets(0, 0, 0, 0);
		body.add(FluxUi.sectionHeader("Events", tw), gbc);

		String[] col = {"Event", "Status"};
		homeEventsModel = new DefaultTableModel(col, 0)
		{
			@Override
			public boolean isCellEditable(int r, int c)
			{
				return false;
			}
		};
		homeEventsModel.addRow(new Object[]{"SOTW", "—"});
		homeEventsModel.addRow(new Object[]{"BOTW", "—"});
		homeEventsModel.addRow(new Object[]{"Website", "—"});
		homeEventsTable = new JTable(homeEventsModel);
		FluxUi.styleDataTable(homeEventsTable);
		homeEventsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		applyTwoColWidths(homeEventsTable, FluxUi.contentWidth());
		homeEventsTable.setPreferredScrollableViewportSize(new Dimension(0, 81));
		homeEventsTable.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				int row = homeEventsTable.rowAtPoint(e.getPoint());
				if (row == HOME_ROW_WEBSITE)
				{
					openUrl(config.clanCalendarPageUrl());
				}
			}
		});

		gbc.gridy = 3;
		gbc.insets = new Insets(0, 0, 6, 0);
		body.add(wrapScroll(homeEventsTable), gbc);

		gbc.gridy = 4;
		gbc.insets = new Insets(0, 0, 0, 0);
		body.add(FluxUi.sectionHeader("Links", tw), gbc);

		gbc.gridy = 5;
		gbc.insets = new Insets(0, 0, 4, 0);
		body.add(linkButton("Discord", config.linkDiscord()), gbc);
		gbc.gridy = 6;
		body.add(linkButton("Name Changes", config.linkNameChanges()), gbc);
		gbc.gridy = 7;
		body.add(linkButton("Announcements", config.linkAnnouncements()), gbc);
		gbc.gridy = 8;
		body.add(linkButton("Events", config.linkEvents()), gbc);
		gbc.gridy = 9;
		body.add(linkButton("Website", config.linkWebsite()), gbc);
		gbc.gridy = 10;
		gbc.insets = new Insets(0, 0, 4, 0);
		JButton clanEventTracker = FluxUi.pillButton("Clan Event tracker");
		clanEventTracker.setHorizontalAlignment(SwingConstants.LEFT);
		clanEventTracker.addActionListener(e -> showClanAttendanceCard());
		body.add(clanEventTracker, gbc);
		gbc.gridy = 11;
		gbc.insets = new Insets(0, 0, 4, 0);
		body.add(linkButton("Wise Old Man", config.linkWiseOldManGroup()), gbc);
		gbc.gridy = 12;
		gbc.insets = new Insets(0, 0, 6, 0);
		body.add(linkButton("Live clan map", config.linkLiveClanMap()), gbc);

		JButton manual = FluxUi.pillButton("Refresh data now");
		manual.addActionListener(e -> plugin.requestFullRefresh());
		gbc.gridy = 13;
		gbc.insets = new Insets(0, 0, 0, 0);
		body.add(manual, gbc);

		gbc.gridy = 14;
		gbc.weighty = 1;
		gbc.fill = GridBagConstraints.BOTH;
		body.add(Box.createGlue(), gbc);

		// No nested JScrollPane: PluginPanel already wraps this panel in a scroll pane.
		root.add(body, BorderLayout.CENTER);
		return root;
	}

	/** Non-editable {@link JTextArea} still consumes wheel events; scroll the sidebar {@link JScrollPane} instead. */
	private void forwardWheelToPluginScrollPane(MouseWheelEvent e)
	{
		JScrollPane sp = getScrollPane();
		if (sp == null)
		{
			return;
		}
		JScrollBar bar = sp.getVerticalScrollBar();
		if (bar == null)
		{
			return;
		}
		int delta;
		if (e.getScrollType() == MouseWheelEvent.WHEEL_UNIT_SCROLL)
		{
			int u = e.getUnitsToScroll();
			delta = u * bar.getUnitIncrement();
		}
		else
		{
			delta = e.getWheelRotation() * bar.getBlockIncrement();
		}
		if (delta == 0)
		{
			delta = e.getWheelRotation() * bar.getUnitIncrement() * 3;
		}
		int next = bar.getValue() + delta;
		int max = Math.max(bar.getMinimum(), bar.getMaximum() - bar.getVisibleAmount());
		next = Math.max(bar.getMinimum(), Math.min(max, next));
		bar.setValue(next);
		e.consume();
	}

	private static void applyTwoColWidths(JTable t, int cw)
	{
		TableColumnModel cm = t.getColumnModel();
		if (cm.getColumnCount() >= 2)
		{
			int w0 = Math.min(64, Math.max(48, cw / 3));
			int w1 = Math.max(40, cw - w0 - 6);
			cm.getColumn(0).setPreferredWidth(w0);
			cm.getColumn(1).setPreferredWidth(w1);
		}
	}

	/**
	 * Sizes Rank / Player / XP(KC) so headers are readable and the table spans the viewport width.
	 */
	private static void distributeLeaderboardColumns(JTable table, int viewportInnerWidth)
	{
		TableColumnModel cm = table.getColumnModel();
		if (cm.getColumnCount() < 3)
		{
			return;
		}
		int inner = Math.max(168, viewportInnerWidth - 4);
		int rankW = 52;
		int gainW = Math.max(82, Math.min(102, (int) Math.round(inner * 0.30)));
		int playerW = inner - rankW - gainW;
		if (playerW < 52)
		{
			playerW = 52;
		}
		gainW = inner - rankW - playerW;
		if (gainW < 72)
		{
			gainW = 72;
			playerW = Math.max(48, inner - rankW - gainW);
		}
		cm.getColumn(0).setPreferredWidth(rankW);
		cm.getColumn(1).setPreferredWidth(playerW);
		cm.getColumn(2).setPreferredWidth(inner - rankW - playerW);
	}

	private static void syncLeaderboardColumnWidths(JTable table)
	{
		int w = 0;
		Container p = table.getParent();
		if (p instanceof JViewport)
		{
			w = p.getWidth();
		}
		distributeLeaderboardColumns(table, w > 0 ? w : FluxUi.contentWidth());
	}

	private void attachLeaderboardViewportSync(JScrollPane tableScroll, JTable table)
	{
		tableScroll.getViewport().addComponentListener(new ComponentAdapter()
		{
			@Override
			public void componentResized(ComponentEvent e)
			{
				syncLeaderboardColumnWidths(table);
			}
		});
	}

	private static String htmlEscape(String s)
	{
		if (s == null)
		{
			return "";
		}
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private JButton linkButton(String label, String url)
	{
		JButton b = FluxUi.pillButton(label);
		b.setHorizontalAlignment(SwingConstants.LEFT);
		boolean ok = url != null && !url.isBlank();
		b.setEnabled(ok);
		if (ok)
		{
			b.addActionListener(e -> openUrl(url));
		}
		return b;
	}

	private static void setWinnerBanner(JLabel winner, String htmlOrNull)
	{
		if (htmlOrNull == null || htmlOrNull.isEmpty())
		{
			winner.setVisible(false);
			winner.setOpaque(false);
			winner.setText("");
		}
		else
		{
			winner.setVisible(true);
			winner.setOpaque(true);
			winner.setBackground(FluxUi.TAB_SELECTED);
			winner.setForeground(FluxUi.HEADER_GOLD);
			winner.setFont(winner.getFont().deriveFont(Font.BOLD, 13f));
			winner.setBorder(new EmptyBorder(8, 4, 8, 4));
			winner.setText(htmlOrNull);
		}
	}

	private JPanel buildCompetitionTab(boolean sotw)
	{
		final int tw = FluxUi.textWidth();

		JLabel winner = sotw ? sotwWinner : botwWinner;
		winner.setHorizontalAlignment(SwingConstants.CENTER);
		setWinnerBanner(winner, null);

		JLabel countdown = sotw ? sotwCountdown : botwCountdown;
		countdown.setForeground(FluxUi.TEXT);
		countdown.setFont(countdown.getFont().deriveFont(Font.BOLD, 12f));
		countdown.setBorder(new EmptyBorder(2, 6, 4, 6));

		JLabel meta = sotw ? sotwMeta : botwMeta;
		meta.setForeground(FluxUi.MUTED);
		meta.setVerticalAlignment(SwingConstants.TOP);
		meta.setBorder(new EmptyBorder(2, 6, 0, 6));
		JLabel lastUp = sotw ? sotwLastUp : botwLastUp;
		lastUp.setForeground(FluxUi.MUTED);
		lastUp.setFont(lastUp.getFont().deriveFont(11f));
		lastUp.setBorder(new EmptyBorder(2, 6, 0, 6));

		JTable table = sotw ? sotwTable : botwTable;
		String gainLabel = sotw ? "XP gained" : "KC / gained";
		if (sotw)
		{
			sotwModel = new DefaultTableModel(new String[]{"Rank", "Player", gainLabel}, 0)
			{
				@Override
				public boolean isCellEditable(int r, int c)
				{
					return false;
				}
			};
			table.setModel(sotwModel);
		}
		else
		{
			botwModel = new DefaultTableModel(new String[]{"Rank", "Player", gainLabel}, 0)
			{
				@Override
				public boolean isCellEditable(int r, int c)
				{
					return false;
				}
			};
			table.setModel(botwModel);
		}
		FluxUi.styleLeaderboardTable(table);
		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		table.getTableHeader().setReorderingAllowed(false);
		table.setPreferredScrollableViewportSize(new Dimension(0, 200));
		installTooltipOnTable(table, sotw);

		JButton goWom = FluxUi.pillButton("Open in Wise Old Man");
		goWom.addActionListener(e ->
		{
			WomLeaderboardModels.CompetitionSnapshot snap = sotw ? lastSotw : lastBotw;
			if (snap.getCompetitionId() > 0)
			{
				openUrl("https://wiseoldman.net/competitions/" + snap.getCompetitionId());
			}
			else
			{
				openUrl(config.linkWiseOldManGroup());
			}
		});

		JButton refresh = FluxUi.pillButton("Refresh now");
		refresh.addActionListener(e -> plugin.requestFullRefresh());

		JPanel top = new JPanel(new GridBagLayout());
		top.setOpaque(false);
		top.setBorder(new EmptyBorder(0, 2, 0, 2));
		GridBagConstraints tg = new GridBagConstraints();
		tg.gridx = 0;
		tg.weightx = 1;
		tg.fill = GridBagConstraints.HORIZONTAL;
		tg.anchor = GridBagConstraints.NORTHWEST;
		tg.insets = new Insets(0, 0, 0, 0);
		int row = 0;
		tg.gridy = row++;
		top.add(winner, tg);
		tg.insets = new Insets(0, 0, 2, 0);
		tg.gridy = row++;
		top.add(countdown, tg);
		tg.insets = new Insets(0, 0, 0, 0);
		tg.gridy = row++;
		top.add(meta, tg);
		tg.gridy = row++;
		top.add(lastUp, tg);
		tg.insets = new Insets(10, 0, 4, 0);
		tg.gridy = row++;
		top.add(refresh, tg);
		tg.insets = new Insets(0, 0, 0, 0);
		tg.gridy = row++;
		top.add(goWom, tg);

		JScrollPane tableScroll = wrapScroll(table, 10);
		attachLeaderboardViewportSync(tableScroll, table);
		SwingUtilities.invokeLater(() -> syncLeaderboardColumnWidths(table));

		JPanel center = new JPanel(new BorderLayout());
		center.setOpaque(false);
		center.add(top, BorderLayout.NORTH);
		center.add(tableScroll, BorderLayout.CENTER);

		JLabel head = new JLabel("<html><div style='width:" + tw + "px;text-align:center'><b>" + (sotw ? "Skill of the Week" : "Boss of the Week") + "</b></div></html>");
		head.setForeground(FluxUi.HEADER_GOLD);
		head.setFont(head.getFont().deriveFont(Font.BOLD, 14f));
		head.setBorder(new EmptyBorder(0, 0, 4, 0));
		head.setHorizontalAlignment(SwingConstants.CENTER);

		JPanel inner = new JPanel(new BorderLayout());
		inner.setBackground(FluxUi.BG);
		inner.setBorder(new EmptyBorder(4, 8, 4, 8));
		inner.add(head, BorderLayout.NORTH);
		inner.add(center, BorderLayout.CENTER);
		return inner;
	}

	/**
	 * Fourth tab: clan website calendar — same layout pattern as SOTW/BOTW (status, meta, last updated,
	 * buttons, leaderboard-style table).
	 */
	private JPanel buildWebEventsTab()
	{
		final int tw = FluxUi.textWidth();

		webEventsStatus.setHorizontalAlignment(SwingConstants.CENTER);
		webEventsStatus.setForeground(FluxUi.TEXT);
		webEventsStatus.setFont(webEventsStatus.getFont().deriveFont(Font.BOLD, 12f));
		webEventsStatus.setBorder(new EmptyBorder(2, 6, 4, 6));

		webEventsMeta.setForeground(FluxUi.MUTED);
		webEventsMeta.setVerticalAlignment(SwingConstants.TOP);
		webEventsMeta.setBorder(new EmptyBorder(2, 6, 0, 6));

		webEventsLastUp.setForeground(FluxUi.MUTED);
		webEventsLastUp.setFont(webEventsLastUp.getFont().deriveFont(11f));
		webEventsLastUp.setBorder(new EmptyBorder(2, 6, 0, 6));

		webEventsModel = new DefaultTableModel(new String[]{"Rank", "Event", "When"}, 0)
		{
			@Override
			public boolean isCellEditable(int r, int c)
			{
				return false;
			}
		};
		webEventsTable.setModel(webEventsModel);
		FluxUi.styleLeaderboardTable(webEventsTable);
		webEventsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		webEventsTable.getTableHeader().setReorderingAllowed(false);
		webEventsTable.setPreferredScrollableViewportSize(new Dimension(0, 200));

		JButton openCal = FluxUi.pillButton("Open clan calendar");
		openCal.addActionListener(e -> openUrl(config.clanCalendarPageUrl()));

		JButton refresh = FluxUi.pillButton("Refresh now");
		refresh.addActionListener(e -> plugin.requestFullRefresh());

		JPanel top = new JPanel(new GridBagLayout());
		top.setOpaque(false);
		top.setBorder(new EmptyBorder(0, 2, 0, 2));
		GridBagConstraints tg = new GridBagConstraints();
		tg.gridx = 0;
		tg.weightx = 1;
		tg.fill = GridBagConstraints.HORIZONTAL;
		tg.anchor = GridBagConstraints.NORTHWEST;
		tg.insets = new Insets(0, 0, 0, 0);
		int row = 0;
		tg.gridy = row++;
		top.add(webEventsStatus, tg);
		tg.insets = new Insets(0, 0, 2, 0);
		tg.gridy = row++;
		top.add(webEventsMeta, tg);
		tg.insets = new Insets(0, 0, 0, 0);
		tg.gridy = row++;
		top.add(webEventsLastUp, tg);
		tg.insets = new Insets(10, 0, 4, 0);
		tg.gridy = row++;
		top.add(refresh, tg);
		tg.insets = new Insets(0, 0, 0, 0);
		tg.gridy = row++;
		top.add(openCal, tg);

		JScrollPane tableScroll = wrapScroll(webEventsTable, 10);
		attachLeaderboardViewportSync(tableScroll, webEventsTable);
		SwingUtilities.invokeLater(() -> syncLeaderboardColumnWidths(webEventsTable));

		JPanel center = new JPanel(new BorderLayout());
		center.setOpaque(false);
		center.add(top, BorderLayout.NORTH);
		center.add(tableScroll, BorderLayout.CENTER);

		JLabel head = new JLabel("<html><div style='width:" + tw + "px;text-align:center'><b>Web Events</b></div></html>");
		head.setForeground(FluxUi.HEADER_GOLD);
		head.setFont(head.getFont().deriveFont(Font.BOLD, 14f));
		head.setBorder(new EmptyBorder(0, 0, 4, 0));
		head.setHorizontalAlignment(SwingConstants.CENTER);

		JPanel inner = new JPanel(new BorderLayout());
		inner.setBackground(FluxUi.BG);
		inner.setBorder(new EmptyBorder(4, 8, 4, 8));
		inner.add(head, BorderLayout.NORTH);
		inner.add(center, BorderLayout.CENTER);
		return inner;
	}

	/**
	 * Clan event attendance (Plugin Hub “Clan Event Attendance”–style), embedded in the sidebar; opened from Home only.
	 */
	private JPanel buildAttendanceTab()
	{
		final int tw = FluxUi.textWidth();

		attendanceReportArea.setEditable(false);
		attendanceReportArea.setLineWrap(false);
		attendanceReportArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		attendanceReportArea.setBackground(FluxUi.BG_PANEL);
		attendanceReportArea.setForeground(FluxUi.TEXT);
		attendanceReportArea.setBorder(new EmptyBorder(6, 6, 6, 6));

		JScrollPane reportScroll = new JScrollPane(attendanceReportArea);
		reportScroll.setPreferredSize(new Dimension(0, 200));
		reportScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		reportScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
		reportScroll.setBorder(FluxUi.tableBorder());
		reportScroll.getViewport().setBackground(FluxUi.BG_PANEL);

		attendanceStartStopBtn.addActionListener(e -> onAttendanceStartStop());
		attendanceCopyBtn.addActionListener(e -> onAttendanceCopy());

		JPanel buttons = new JPanel(new GridLayout(2, 1, 0, 6));
		buttons.setOpaque(false);
		buttons.setBorder(new EmptyBorder(8, 0, 0, 0));
		buttons.add(attendanceStartStopBtn);
		buttons.add(attendanceCopyBtn);

		JPanel center = new JPanel(new BorderLayout());
		center.setOpaque(false);
		center.add(reportScroll, BorderLayout.CENTER);
		center.add(buttons, BorderLayout.SOUTH);

		JLabel head = new JLabel("<html><div style='width:" + tw + "px;text-align:center'><b>Clan Event tracker</b></div></html>");
		head.setForeground(FluxUi.HEADER_GOLD);
		head.setFont(head.getFont().deriveFont(Font.BOLD, 14f));
		head.setBorder(new EmptyBorder(0, 0, 4, 0));
		head.setHorizontalAlignment(SwingConstants.CENTER);

		JPanel inner = new JPanel(new BorderLayout());
		inner.setBackground(FluxUi.BG);
		inner.setBorder(new EmptyBorder(4, 8, 4, 8));
		inner.add(head, BorderLayout.NORTH);
		inner.add(center, BorderLayout.CENTER);
		return inner;
	}

	private void syncAttendanceTab()
	{
		ClanAttendanceTracker tracker = plugin.getClanAttendanceTracker();
		attendanceReportArea.setText(tracker.getCurrentReport());
		attendanceStartStopBtn.setText(tracker.isEventRunning() ? "Stop event" : "Start event");
		boolean block = config.attendanceBlockCopyWhileRunning() && tracker.isEventRunning();
		attendanceCopyBtn.setEnabled(!block);
	}

	private void onAttendanceStartStop()
	{
		ClanAttendanceTracker tracker = plugin.getClanAttendanceTracker();
		if (config.attendanceConfirmStartStop())
		{
			boolean stopping = tracker.isEventRunning();
			String msg = stopping
				? "Stop the event and finalize the attendance report?"
				: "Start a new event? Current tracking data will be cleared.";
			int r = JOptionPane.showConfirmDialog(this, msg, "Clan Event tracker",
				JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
			if (r != JOptionPane.YES_OPTION)
			{
				return;
			}
		}

		plugin.runOnClientThread(() ->
		{
			if (tracker.isEventRunning())
			{
				tracker.stopEvent();
			}
			else
			{
				tracker.startEvent();
			}
		});
	}

	private void onAttendanceCopy()
	{
		String t = attendanceReportArea.getText();
		if (t == null || t.isBlank())
		{
			return;
		}
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(t), null);
	}

	private void updateWebEventsPanel()
	{
		if (webEventsModel == null)
		{
			return;
		}
		int tw = FluxUi.textWidth();
		String st = plugin.getClanCalendarSummaryStatus();
		if (st == null || st.isEmpty())
		{
			st = "—";
		}
		webEventsStatus.setText("<html><div style='width:" + tw + "px;text-align:center'>" + htmlEscape(st) + "</div></html>");

		String metaLine;
		if ("Unavailable".equals(st))
		{
			metaLine = "Could not reach calendar API. Check URL and network.";
		}
		else if ("—".equals(st))
		{
			metaLine = "Set Links → Clan calendar page URL & summary API (?format=array for the table).";
		}
		else
		{
			metaLine = "Website events · status: " + st + " (from start/end times, ISO-8601).";
		}
		webEventsMeta.setText("<html><div style='width:" + tw + "px'>" + htmlEscape(metaLine) + "</div></html>");

		long fetchMs = plugin.getClanCalendarLastFetchMs();
		if (fetchMs > 0L)
		{
			String ts = DateTimeFormatter.ofPattern("MMM d, HH:mm:ss")
				.withZone(ZoneId.systemDefault())
				.format(Instant.ofEpochMilli(fetchMs));
			webEventsLastUp.setText("<html><div style='width:" + tw + "px'>" + htmlEscape("Last updated: " + ts) + "</div></html>");
		}
		else
		{
			webEventsLastUp.setText("");
		}

		webEventsModel.setRowCount(0);
		for (WebEventRow row : plugin.getClanCalendarWebEventRows())
		{
			webEventsModel.addRow(new Object[]{
				row.getRank(),
				row.getTitle(),
				row.getWhen()
			});
		}
		syncLeaderboardColumnWidths(webEventsTable);
		webEventsTable.repaint();
	}

	private void installTooltipOnTable(JTable table, boolean sotw)
	{
		table.addMouseMotionListener(new MouseAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				int r = table.rowAtPoint(e.getPoint());
				List<String> tips = sotw ? sotwTooltips : botwTooltips;
				if (r >= 0 && r < tips.size())
				{
					table.setToolTipText(tips.get(r));
				}
				else
				{
					table.setToolTipText(null);
				}
			}
		});
	}

	private JScrollPane wrapScroll(JTable t)
	{
		return wrapScroll(t, 0);
	}

	private JScrollPane wrapScroll(JTable t, int marginTop)
	{
		JScrollPane sp = new JScrollPane(t);
		sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		Border outer = marginTop > 0
			? BorderFactory.createCompoundBorder(new EmptyBorder(marginTop, 0, 0, 0), FluxUi.tableBorder())
			: FluxUi.tableBorder();
		sp.setBorder(outer);
		sp.getViewport().setBackground(FluxUi.BG_PANEL);
		return sp;
	}

	private String statusLabel(WomLeaderboardModels.CompetitionSnapshot s)
	{
		if (!s.isAvailable())
		{
			if (s.getError() != null && !s.getError().isEmpty())
			{
				return "Error";
			}
			return "No data";
		}
		switch (s.getPhase())
		{
			case ACTIVE:
				return "Active";
			case UPCOMING:
				return "Upcoming";
			case FINISHED:
				return "Finished";
			default:
				return "—";
		}
	}

	private void refreshAnnouncements()
	{
		if (!config.announcementsEnabled())
		{
			announcementsArea.setVisible(false);
			return;
		}
		announcementsArea.setVisible(true);
		announcementsArea.setText(lastAnnouncements);
	}

	private void fillCompetitionUi(boolean sotw)
	{
		WomLeaderboardModels.CompetitionSnapshot s = sotw ? lastSotw : lastBotw;
		JLabel winner = sotw ? sotwWinner : botwWinner;
		JLabel meta = sotw ? sotwMeta : botwMeta;
		JLabel lastUp = sotw ? sotwLastUp : botwLastUp;
		DefaultTableModel model = sotw ? sotwModel : botwModel;
		List<String> tips = sotw ? sotwTooltips : botwTooltips;
		JTable table = sotw ? sotwTable : botwTable;

		int tw = FluxUi.textWidth();
		if (!s.isAvailable())
		{
			setWinnerBanner(winner, null);
			if (s.getError() != null && !s.getError().isEmpty())
			{
				meta.setText("<html><div style='width:" + tw + "px'>" + htmlEscape("Data unavailable: " + s.getError()) + "</div></html>");
			}
			else
			{
				String h = s.getCountdownHint();
				String msg = h != null && !h.isEmpty() ? h : "Set your Wise Old Man group ID under General, or ensure the group has SOTW / BOTW competitions.";
				meta.setText("<html><div style='width:" + tw + "px'>" + htmlEscape(msg) + "</div></html>");
			}
			lastUp.setText("");
			clearModel(model, tips, table);
			syncLeaderboardColumnWidths(table);
			return;
		}
		if (s.getPhase() == WomLeaderboardModels.EventPhase.FINISHED && s.getWinner() != null)
		{
			WomLeaderboardModels.LeaderRow w = s.getWinner();
			String line = "Winner: " + w.getPlayerName() + " — " + formatGain(w.getGained());
			setWinnerBanner(winner, "<html><div style='width:" + tw + "px;text-align:center'>" + htmlEscape(line) + "</div></html>");
		}
		else if (s.getPhase() == WomLeaderboardModels.EventPhase.FINISHED && !s.getTop10().isEmpty())
		{
			WomLeaderboardModels.LeaderRow w = s.getTop10().get(0);
			String line = "Winner: " + w.getPlayerName() + " — " + formatGain(w.getGained());
			setWinnerBanner(winner, "<html><div style='width:" + tw + "px;text-align:center'>" + htmlEscape(line) + "</div></html>");
		}
		else
		{
			setWinnerBanner(winner, null);
		}

		String title = s.getTitle().isEmpty() ? "(no title)" : s.getTitle();
		String metric = s.getMetric().isEmpty() ? "" : " · " + s.getMetric();
		meta.setText("<html><div style='width:" + tw + "px'>" + htmlEscape(title + metric + (s.getCompetitionId() > 0 ? " · ID " + s.getCompetitionId() : "")) + "</div></html>");

		if (s.getLastUpdatedEpochMs() > 0)
		{
			String ts = DateTimeFormatter.ofPattern("MMM d, HH:mm:ss")
				.withZone(ZoneId.systemDefault())
				.format(Instant.ofEpochMilli(s.getLastUpdatedEpochMs()));
			lastUp.setText("<html><div style='width:" + tw + "px'>" + htmlEscape("Last updated: " + ts) + "</div></html>");
		}
		else
		{
			lastUp.setText("");
		}

		tips.clear();
		model.setRowCount(0);
		for (WomLeaderboardModels.LeaderRow row : s.getTop10())
		{
			model.addRow(new Object[]{
				row.getRank(),
				row.getPlayerName(),
				formatGain(row.getGained())
			});
			tips.add(row.getTooltip());
		}
		syncLeaderboardColumnWidths(table);
		table.repaint();
	}

	private static String formatGain(long g)
	{
		return String.format(Locale.US, "%,d", g);
	}

	private void clearModel(DefaultTableModel model, List<String> tips, JTable table)
	{
		tips.clear();
		model.setRowCount(0);
		table.repaint();
	}

	private void tickCountdowns()
	{
		updateCountdownLabel(lastSotw, sotwCountdown);
		updateCountdownLabel(lastBotw, botwCountdown);
		// refresh home status table if visible
		updateHomeEventTable();
		updateWebEventsPanel();
		syncAttendanceTab();
	}

	private void updateCountdownLabel(WomLeaderboardModels.CompetitionSnapshot s, JLabel lbl)
	{
		Instant start = s.getStartsAt();
		Instant end = s.getEndsAt();
		Instant now = Instant.now();
		WomLeaderboardModels.EventPhase ph = WomLeaderboardModels.phaseFor(start, end, now);
		String txt = WomLeaderboardModels.formatCountdown(start, end, now, ph);
		int tw = FluxUi.textWidth();
		lbl.setText("<html><div style='width:" + tw + "px;text-align:center'>" + htmlEscape(txt) + "</div></html>");
	}

	private void updateHomeEventTable()
	{
		if (homeEventsModel == null)
		{
			return;
		}
		homeEventsModel.setValueAt(statusLabel(lastSotw), 0, 1);
		homeEventsModel.setValueAt(statusLabel(lastBotw), 1, 1);
		if (homeEventsModel.getRowCount() > HOME_ROW_WEBSITE)
		{
			homeEventsModel.setValueAt(plugin.getClanCalendarSummaryStatus(), HOME_ROW_WEBSITE, 1);
		}
	}

	private void openUrl(String url)
	{
		if (url == null || url.isBlank())
		{
			return;
		}
		try
		{
			if (Desktop.isDesktopSupported())
			{
				Desktop.getDesktop().browse(URI.create(url.trim()));
			}
		}
		catch (Exception ignored)
		{
		}
	}

	private static BufferedImage readLogo()
	{
		try (InputStream in = TerpinheimerPanel.class.getResourceAsStream(LOGO))
		{
			if (in == null)
			{
				return null;
			}
			return ImageIO.read(in);
		}
		catch (IOException e)
		{
			return null;
		}
	}

	public static BufferedImage loadToolbarIcon()
	{
		BufferedImage src = readLogo();
		if (src == null)
		{
			int s = 16;
			BufferedImage fb = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
			for (int y = 0; y < s; y++)
			{
				for (int x = 0; x < s; x++)
				{
					fb.setRGB(x, y, ((x + y) % 2 == 0) ? 0xffcc9900 : 0xff1e1e1e);
				}
			}
			return fb;
		}
		return ImageUtil.resizeImage(src, 16, 16);
	}

	@Override
	public Dimension getPreferredSize()
	{
		return new Dimension(PluginPanel.PANEL_WIDTH, super.getPreferredSize().height);
	}

	private static final class BorderStyle
	{
		static void setTabBorder(JButton b, boolean sel)
		{
			b.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(sel ? FluxUi.HEADER_GOLD : FluxUi.BORDER, sel ? 2 : 1, true),
				new EmptyBorder(8, 12, 8, 12)));
		}
	}
}
