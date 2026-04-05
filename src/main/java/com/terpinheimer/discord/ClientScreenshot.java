package com.terpinheimer.discord;

import java.awt.Canvas;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;

/**
 * Captures the client canvas from the screen (same idea as many RuneLite screenshot flows).
 */
@Singleton
public class ClientScreenshot
{
	private final Client client;
	private final DiscordLoginGrace loginGrace;

	@Inject
	ClientScreenshot(Client client, DiscordLoginGrace loginGrace)
	{
		this.client = client;
		this.loginGrace = loginGrace;
	}

	/**
	 * Must run on the AWT event thread.
	 */
	public byte[] capturePngOrNull()
	{
		if (loginGrace.inLoginGracePeriod())
		{
			return null;
		}
		Canvas canvas = client.getCanvas();
		if (canvas == null || !canvas.isShowing())
		{
			return null;
		}
		try
		{
			Rectangle bounds = new Rectangle(canvas.getLocationOnScreen(), canvas.getSize());
			BufferedImage shot = new Robot().createScreenCapture(bounds);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ImageIO.write(shot, "png", baos);
			return baos.toByteArray();
		}
		catch (Exception e)
		{
			return null;
		}
	}
}
