package net.runelite.client.plugins.microbot.util.leaguetransport;

/**
 * Dedupe key and method normalization for {@code kind=lock-catalogue} JSONL rows.
 */
final class LeaguesTransportLockCatalogue
{
	private LeaguesTransportLockCatalogue()
	{
	}

	/**
	 * Strip optional {@code |handler=...} suffix from attempt method labels.
	 */
	static String normalizeLockCatalogueMethod(String method)
	{
		if (method == null || method.isEmpty())
		{
			return "";
		}
		int idx = method.indexOf("|handler=");
		if (idx >= 0)
		{
			return method.substring(0, idx);
		}
		return method;
	}

	static String buildDedupeKey(int packedDest, String normalizedMethod)
	{
		String m = normalizedMethod != null ? normalizedMethod : "";
		return packedDest + "|" + m;
	}
}
