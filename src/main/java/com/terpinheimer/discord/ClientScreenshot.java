package com.terpinheimer.discord;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.BufferProvider;
import net.runelite.api.Client;

/**
 * Captures the client framebuffer for Discord embeds (reads the game buffer only; no screen automation).
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
	 * Must run on the client thread.
	 */
	public byte[] capturePngOrNull()
	{
		if (loginGrace.inLoginGracePeriod())
		{
			return null;
		}
		BufferProvider bufferProvider = client.getBufferProvider();
		if (bufferProvider == null)
		{
			return null;
		}
		int width = bufferProvider.getWidth();
		int height = bufferProvider.getHeight();
		if (width <= 0 || height <= 0)
		{
			return null;
		}
		int[] pixels = bufferProvider.getPixels();
		if (pixels == null || pixels.length < width * height)
		{
			return null;
		}
		try
		{
			BufferedImage shot = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
			shot.setRGB(0, 0, width, height, pixels, 0, width);
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
