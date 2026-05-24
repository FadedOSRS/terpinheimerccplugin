package com.terpinheimer.discord;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.BufferProvider;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.util.ImageUtil;

/**
 * Captures the client framebuffer for Discord embeds.
 * <p>
 * Uses {@link DrawManager#requestNextFrameListener} on the client thread so screenshots work with the
 * GPU plugin. Reading {@link BufferProvider#getPixels()} alone often yields a black game view.
 */
@Singleton
public class ClientScreenshot
{
	private final Client client;
	private final DrawManager drawManager;
	private final DiscordLoginGrace loginGrace;
	private final ClientThread clientThread;

	@Inject
	ClientScreenshot(
		Client client,
		DrawManager drawManager,
		DiscordLoginGrace loginGrace,
		ClientThread clientThread)
	{
		this.client = client;
		this.drawManager = drawManager;
		this.loginGrace = loginGrace;
		this.clientThread = clientThread;
	}

	/**
	 * Captures after the next fully drawn frame (waits two frames when possible for GPU stability).
	 * Callback may run on the client thread; safe to enqueue webhooks from it.
	 */
	public void capturePngAfterNextFrame(Consumer<byte[]> callback)
	{
		if (callback == null)
		{
			return;
		}
		if (loginGrace.inLoginGracePeriod())
		{
			callback.accept(null);
			return;
		}
		clientThread.invokeLater(() -> drawManager.requestNextFrameListener(firstImage ->
			clientThread.invokeLater(() -> drawManager.requestNextFrameListener(secondImage ->
			{
				Image image = secondImage != null ? secondImage : firstImage;
				callback.accept(imageToPng(image));
			}))));
	}

	private byte[] imageToPng(Image image)
	{
		if (image == null)
		{
			return captureFromBufferProviderFallback();
		}
		try
		{
			BufferedImage shot = ImageUtil.bufferedImageFromImage(image);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ImageIO.write(shot, "png", baos);
			return baos.toByteArray();
		}
		catch (Exception e)
		{
			return captureFromBufferProviderFallback();
		}
	}

	/** Software renderer only; kept as fallback when DrawManager returns no image. */
	private byte[] captureFromBufferProviderFallback()
	{
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
