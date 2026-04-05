package com.terpinheimer.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import net.runelite.client.ui.PluginPanel;

final class FluxUi
{
	static final Color BG = new Color(0x1e1e1e);
	static final Color BG_PANEL = new Color(0x252526);
	static final Color HEADER_GOLD = new Color(0xffcc00);
	static final Color TEXT = new Color(0xe0e0e0);
	static final Color MUTED = new Color(0xa8a8a8);
	static final Color BORDER = new Color(0x404040);
	static final Color TAB_SELECTED = new Color(0x3d4f2d);
	static final Color TAB_NORMAL = new Color(0x333333);

	/** Dark gold row tint for 1st place */
	private static final Color PODIUM_1_BG = new Color(0x4a3d0c);
	private static final Color PODIUM_1_BG_SEL = new Color(0x5c4f12);
	private static final Color PODIUM_1_FG = new Color(0xffe066);

	/** Steel / silver row tint for 2nd */
	private static final Color PODIUM_2_BG = new Color(0x343c48);
	private static final Color PODIUM_2_BG_SEL = new Color(0x3f4858);
	private static final Color PODIUM_2_FG = new Color(0xd8e4f0);

	/** Bronze row tint for 3rd */
	private static final Color PODIUM_3_BG = new Color(0x4a3220);
	private static final Color PODIUM_3_BG_SEL = new Color(0x5c3f28);
	private static final Color PODIUM_3_FG = new Color(0xf0c090);

	private FluxUi()
	{
	}

	/**
	 * Usable width for fixed-size hints (HTML, tables). Conservative so content stays inside the
	 * viewport with scrollbar + RuneLite chrome.
	 */
	static int contentWidth()
	{
		return Math.max(156, PluginPanel.PANEL_WIDTH - 52);
	}

	/** Slightly narrower for wrapped HTML blocks (inner padding). */
	static int textWidth()
	{
		return Math.max(140, contentWidth() - 8);
	}

	static JLabel sectionHeader(String text)
	{
		return sectionHeader(text, contentWidth());
	}

	static JLabel sectionHeader(String text, int wrapWidth)
	{
		String t = text.toUpperCase(java.util.Locale.ROOT).replace("&", "&amp;").replace("<", "&lt;");
		JLabel l = new JLabel("<html><div style='width:" + wrapWidth + "px;text-align:left'>" + t + "</div></html>");
		l.setForeground(HEADER_GOLD);
		l.setFont(l.getFont().deriveFont(Font.BOLD, 12f));
		l.setBorder(new EmptyBorder(10, 0, 4, 0));
		return l;
	}

	static Border tableBorder()
	{
		return BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(BORDER, 1, true),
			new EmptyBorder(4, 4, 4, 4));
	}

	static void styleDataTable(JTable table)
	{
		applyCommonTableStyle(table);
		table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer()
		{
			@Override
			public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c)
			{
				Component comp = super.getTableCellRendererComponent(t, v, sel, foc, r, c);
				comp.setBackground(sel ? TAB_SELECTED : BG_PANEL);
				comp.setForeground(TEXT);
				return comp;
			}
		});
	}

	/**
	 * SOTW / BOTW leaderboard: highlights ranks 1–3 (reads rank from column 0).
	 */
	static void styleLeaderboardTable(JTable table)
	{
		applyCommonTableStyle(table);
		table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer()
		{
			@Override
			public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int row, int col)
			{
				Component comp = super.getTableCellRendererComponent(t, v, sel, foc, row, col);
				int place = podiumPlace(t, row);
				if (place == 1)
				{
					comp.setBackground(sel ? PODIUM_1_BG_SEL : PODIUM_1_BG);
					comp.setForeground(PODIUM_1_FG);
				}
				else if (place == 2)
				{
					comp.setBackground(sel ? PODIUM_2_BG_SEL : PODIUM_2_BG);
					comp.setForeground(PODIUM_2_FG);
				}
				else if (place == 3)
				{
					comp.setBackground(sel ? PODIUM_3_BG_SEL : PODIUM_3_BG);
					comp.setForeground(PODIUM_3_FG);
				}
				else
				{
					comp.setBackground(sel ? TAB_SELECTED : BG_PANEL);
					comp.setForeground(TEXT);
				}
				return comp;
			}
		});
	}

	private static void applyCommonTableStyle(JTable table)
	{
		table.setBackground(BG_PANEL);
		table.setForeground(TEXT);
		table.setGridColor(BORDER);
		table.setRowHeight(22);
		table.setShowGrid(true);
		table.setFillsViewportHeight(true);
		table.getTableHeader().setBackground(BG);
		table.getTableHeader().setForeground(HEADER_GOLD);
		table.setSelectionBackground(TAB_SELECTED);
		table.setSelectionForeground(TEXT);
	}

	private static int podiumPlace(JTable table, int row)
	{
		if (row < 0 || row >= table.getRowCount() || table.getColumnCount() < 1)
		{
			return 0;
		}
		Object rv = table.getModel().getValueAt(row, 0);
		if (rv instanceof Number)
		{
			int r = ((Number) rv).intValue();
			return (r >= 1 && r <= 3) ? r : 0;
		}
		if (rv != null)
		{
			try
			{
				int r = Integer.parseInt(rv.toString().trim());
				return (r >= 1 && r <= 3) ? r : 0;
			}
			catch (NumberFormatException ignored)
			{
				return 0;
			}
		}
		return 0;
	}

	static javax.swing.JButton pillButton(String text)
	{
		javax.swing.JButton b = new javax.swing.JButton(text);
		b.setOpaque(true);
		b.setBackground(TAB_NORMAL);
		b.setForeground(TEXT);
		b.setFocusPainted(false);
		b.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(BORDER, 1, true),
			new EmptyBorder(6, 8, 6, 8)));
		b.setAlignmentX(Component.LEFT_ALIGNMENT);
		return b;
	}

	static javax.swing.JButton tabToggle(String text, boolean selected)
	{
		javax.swing.JButton b = new javax.swing.JButton(text);
		b.setOpaque(true);
		b.setBackground(selected ? TAB_SELECTED : TAB_NORMAL);
		b.setForeground(TEXT);
		b.setFont(b.getFont().deriveFont(Font.PLAIN, 11f));
		b.setFocusPainted(false);
		Border outer = BorderFactory.createLineBorder(selected ? HEADER_GOLD : BORDER, selected ? 2 : 1, true);
		b.setBorder(BorderFactory.createCompoundBorder(outer, new EmptyBorder(6, 4, 6, 4)));
		return b;
	}
}
