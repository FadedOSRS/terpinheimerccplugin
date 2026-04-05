package com.terpinheimer.discord;

import javax.annotation.Nullable;

public final class WebhookPayload
{
	private final String jsonBody;
	@Nullable
	private final byte[] imagePng;

	public WebhookPayload(String jsonBody, @Nullable byte[] imagePng)
	{
		this.jsonBody = jsonBody;
		this.imagePng = imagePng;
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
}
