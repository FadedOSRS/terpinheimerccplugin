package com.terpinheimer.discord;

import javax.annotation.Nullable;

public final class WebhookPayload
{
	private final String jsonBody;
	@Nullable
	private final byte[] imagePng;
	@Nullable
	private final String webhookUrlOverride;

	public WebhookPayload(String jsonBody, @Nullable byte[] imagePng)
	{
		this(jsonBody, imagePng, null);
	}

	public WebhookPayload(String jsonBody, @Nullable byte[] imagePng, @Nullable String webhookUrlOverride)
	{
		this.jsonBody = jsonBody;
		this.imagePng = imagePng;
		this.webhookUrlOverride = webhookUrlOverride;
	}

	public String getJsonBody()
	{
		return jsonBody;
	}

	@Nullable
	public byte[] getImagePng()
	{
		return imagePng;
	}

	@Nullable
	public String getWebhookUrlOverride()
	{
		return webhookUrlOverride;
	}
}
