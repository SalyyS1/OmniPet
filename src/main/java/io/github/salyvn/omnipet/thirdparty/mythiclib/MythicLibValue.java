package io.github.salyvn.omnipet.thirdparty.mythiclib;

import org.bukkit.entity.Player;

import io.github.nahkd123.tinyexpr.Value;
import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.skill.SimpleSkill;
import io.lumine.mythic.lib.skill.result.SkillResult;

public enum MythicLibValue implements Value {
	FACTORY {
		@Override
		public Value get(String name) {
			return switch (name) {
			case "cast" -> CAST;
			default -> super.get(name);
			};
		}
	},
	CAST {
		@Override
		public Value call(Value[] params) {
			if (params.length < 2) return Value.wrap(false);
			try {
				Player p = params[0].unwrapAs(Player.class);
				String id = params[1].unwrapAs(String.class).toUpperCase().replace('-', '_');
				SimpleSkill skill = new SimpleSkill(MythicLib.inst().getSkills().getHandlerOrThrow(id));
				SkillResult result = skill.cast(MMOPlayerData.get(p.getUniqueId()));
				return Value.wrap(result.isSuccessful());
			} catch (RuntimeException exception) {
				return Value.wrap(false);
			}
		}
	}
}
