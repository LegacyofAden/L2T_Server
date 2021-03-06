/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package handlers.admincommandhandlers;

import l2server.gameserver.datatables.ClanTable;
import l2server.gameserver.datatables.SkillTable;
import l2server.gameserver.handler.IAdminCommandHandler;
import l2server.gameserver.model.L2Clan;
import l2server.gameserver.model.L2Object;
import l2server.gameserver.model.L2Skill;
import l2server.gameserver.model.actor.instance.L2PcInstance;
import l2server.gameserver.network.SystemMessageId;
import l2server.gameserver.network.serverpackets.GMViewPledgeInfo;
import l2server.gameserver.network.serverpackets.PledgeSkillList;
import l2server.gameserver.network.serverpackets.SystemMessage;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

/**
 * <B>Pledge Manipulation:</B><BR>
 * <LI>With target in a character without clan:<BR>
 * //pledge create clanname
 * <LI>With target in a clan leader:<BR>
 * //pledge info<BR>
 * //pledge dismiss<BR>
 * //pledge setlevel level<BR>
 * //pledge rep reputation_points<BR>
 */
public class AdminPledge implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS = {"admin_pledge"};

	@Override
	public boolean useAdminCommand(String command, L2PcInstance activeChar)
	{
		L2Object target = activeChar.getTarget();
		L2PcInstance player = null;
		if (target instanceof L2PcInstance)
		{
			player = (L2PcInstance) target;
		}
		else
		{
			activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.INCORRECT_TARGET));
			showMainPage(activeChar);
			return false;
		}
		String name = player.getName();
		if (command.startsWith("admin_pledge"))
		{
			String action = null;
			String parameter = null;
			StringTokenizer st = new StringTokenizer(command);
			try
			{
				st.nextToken();
				action = st.nextToken(); // create|info|dismiss|setlevel|rep
				parameter = st.nextToken(); // clanname|nothing|nothing|level|rep_points
			}
			catch (NoSuchElementException nse)
			{
			}
			if (action.equals("create"))
			{
				long cet = player.getClanCreateExpiryTime();
				player.setClanCreateExpiryTime(0);
				L2Clan clan = ClanTable.getInstance().createClan(player, parameter);
				if (clan != null)
				{
					activeChar.sendMessage("Clan " + parameter + " created. Leader: " + player.getName());
				}
				else
				{
					player.setClanCreateExpiryTime(cet);
					activeChar.sendMessage("There was a problem while creating the clan.");
				}
			}
			else if (!player.isClanLeader())
			{
				activeChar.sendPacket(
						SystemMessage.getSystemMessage(SystemMessageId.S1_IS_NOT_A_CLAN_LEADER).addString(name));
				showMainPage(activeChar);
				return false;
			}
			else if (action.equals("dismiss"))
			{
				ClanTable.getInstance().destroyClan(player.getClanId());
				L2Clan clan = player.getClan();
				if (clan == null)
				{
					activeChar.sendMessage("Clan disbanded.");
				}
				else
				{
					activeChar.sendMessage("There was a problem while destroying the clan.");
				}
			}
			else if (action.equals("info"))
			{
				activeChar.sendPacket(new GMViewPledgeInfo(player.getClan(), player));
			}
			else if (action.startsWith("skills"))
			{
				Map<Integer, Integer> skills = new HashMap<Integer, Integer>();
				skills.put(370, 2);
				skills.put(371, 3);
				skills.put(372, 1);
				skills.put(373, 2);
				skills.put(374, 2);
				skills.put(375, 1);
				skills.put(376, 3);
				skills.put(377, 3);
				skills.put(378, 1);
				skills.put(379, 2);
				skills.put(380, 2);
				skills.put(381, 1);
				skills.put(382, 2);
				skills.put(383, 2);
				skills.put(384, 2);
				skills.put(385, 2);
				skills.put(386, 2);
				skills.put(387, 2);
				skills.put(388, 2);
				skills.put(389, 1);
				skills.put(390, 2);
				skills.put(391, 1);
				for (int id : skills.keySet())
				{
					L2Skill skill = SkillTable.getInstance().getInfo(id, skills.get(id));
					if (skill != null)
					{
						String skillname = skill.getName();
						SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.CLAN_SKILL_S1_ADDED);
						sm.addSkillName(skill);
						player.sendPacket(sm);
						player.getClan().broadcastToOnlineMembers(sm);
						player.getClan().addNewSkill(skill);
						activeChar.sendMessage(
								"You gave the Clan Skill: " + skillname + " to the clan " + player.getClan().getName() +
										".");

						activeChar.getClan().broadcastToOnlineMembers(new PledgeSkillList(activeChar.getClan()));
						for (L2PcInstance member : activeChar.getClan().getOnlineMembers(0))
						{
							member.sendSkillList();
						}
					}
				}
			}
			else if (parameter == null)
			{
				activeChar.sendMessage("Usage: //pledge <setlevel|rep> <number>");
			}
			else if (action.equals("setlevel"))
			{
				int level = Integer.parseInt(parameter);
				if (level >= 0 && level < 12)
				{
					player.getClan().changeLevel(level);
					activeChar.sendMessage("You set level " + level + " for clan " + player.getClan().getName());
				}
				else
				{
					activeChar.sendMessage("Level incorrect.");
				}
			}
			else if (action.startsWith("rep"))
			{
				try
				{
					int points = Integer.parseInt(parameter);
					L2Clan clan = player.getClan();
					if (clan.getLevel() < 5)
					{
						activeChar.sendMessage("Only clans of level 5 or above may receive reputation points.");
						showMainPage(activeChar);
						return false;
					}
					clan.addReputationScore(points, true);
					activeChar.sendMessage("You " + (points > 0 ? "add " : "remove ") + Math.abs(points) + " points " +
							(points > 0 ? "to " : "from ") + clan.getName() + "'s reputation. Their current score is " +
							clan.getReputationScore());
				}
				catch (Exception e)
				{
					activeChar.sendMessage("Usage: //pledge <rep> <number>");
				}
			}
		}
		showMainPage(activeChar);
		return true;
	}

	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}

	private void showMainPage(L2PcInstance activeChar)
	{
		AdminHelpPage.showHelpPage(activeChar, "game_menu.htm");
	}
}
