package shortestpath;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import shortestpath.transport.parser.VarRequirement;

/**
 * Optional access requirements for a destination tile (e.g. bank booths). Empty requirements mean
 * the destination is always usable when reached.
 */
public final class DestinationRequirements
{
	/**
	 * All-zero skill array; no skill requirements.
	 */
	public static final DestinationRequirements EMPTY = new DestinationRequirements();

	@Getter
	private final int[] skillLevels;
	@Getter
	private final Set<Quest> quests;
	@Getter
	private final Set<VarRequirement> varbits;
	@Getter
	private final Set<VarRequirement> varPlayers;

	private DestinationRequirements()
	{
		this.skillLevels = new int[Skill.values().length + 3];
		this.quests = Collections.emptySet();
		this.varbits = Collections.emptySet();
		this.varPlayers = Collections.emptySet();
	}

	public DestinationRequirements(
		int[] skillLevels,
		Set<Quest> quests,
		Set<VarRequirement> varbits,
		Set<VarRequirement> varPlayers)
	{
		this.skillLevels = skillLevels != null ? skillLevels : new int[Skill.values().length + 3];
		this.quests = quests != null ? quests : Collections.emptySet();
		this.varbits = varbits != null ? varbits : Collections.emptySet();
		this.varPlayers = varPlayers != null ? varPlayers : Collections.emptySet();
	}

	/**
	 * @return a merged requirement when the same tile appears on multiple rows (max skills, union sets).
	 */
	public static DestinationRequirements merge(DestinationRequirements a, DestinationRequirements b)
	{
		if (a == null || a.isEmpty())
		{
			return b != null ? b : EMPTY;
		}
		if (b == null || b.isEmpty())
		{
			return a;
		}
		int[] skills = new int[Skill.values().length + 3];
		for (int i = 0; i < skills.length; i++)
		{
			skills[i] = Math.max(a.skillLevels[i], b.skillLevels[i]);
		}
		Set<Quest> q = new HashSet<>(a.quests);
		q.addAll(b.quests);
		Set<VarRequirement> vb = new HashSet<>(a.varbits);
		vb.addAll(b.varbits);
		Set<VarRequirement> vp = new HashSet<>(a.varPlayers);
		vp.addAll(b.varPlayers);
		return new DestinationRequirements(skills, q, vb, vp);
	}

	public boolean isEmpty()
	{
		if (!quests.isEmpty())
		{
			return false;
		}
		if (!varbits.isEmpty() || !varPlayers.isEmpty())
		{
			return false;
		}
		for (int level : skillLevels)
		{
			if (level > 0)
			{
				return false;
			}
		}
		return true;
	}
}
