package net.runelite.client.plugins.microbot.bans;

import lombok.Value;

@Value
class IdentityMatch
{
	static final IdentityMatch UNKNOWN = new IdentityMatch(State.UNKNOWN, null);
	static final IdentityMatch AMBIGUOUS = new IdentityMatch(State.AMBIGUOUS, null);

	enum State
	{
		UNKNOWN,
		AMBIGUOUS,
		MATCHED
	}

	State state;
	String accountKey;

	static IdentityMatch of(String accountKey)
	{
		return new IdentityMatch(State.MATCHED, accountKey);
	}
}
