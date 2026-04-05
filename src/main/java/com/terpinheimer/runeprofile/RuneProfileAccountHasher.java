package com.terpinheimer.runeprofile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import net.runelite.api.Client;

/**
 * Matches {@code AccountHash} from the RuneProfile plugin — stable profile id for api.runeprofile.com.
 */
final class RuneProfileAccountHasher
{
	private RuneProfileAccountHasher()
	{
	}

	static String hashedAccountId(Client client)
	{
		long accountHashLong = client.getAccountHash();
		if (accountHashLong == -1L)
		{
			return null;
		}
		String accountHashString = String.valueOf(accountHashLong);
		MessageDigest digest;
		try
		{
			digest = MessageDigest.getInstance("SHA-1");
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new RuntimeException(e);
		}
		byte[] hash = digest.digest(accountHashString.getBytes(StandardCharsets.UTF_8));
		return Base64.getEncoder().encodeToString(hash);
	}
}
