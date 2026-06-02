package cn.lanink.situationpuzzlegame.command;

import cn.lanink.situationpuzzlegame.SituationPuzzleGame;
import cn.lanink.situationpuzzlegame.game.GameRoom;
import cn.lanink.situationpuzzlegame.i18n.Texts;
import cn.lanink.situationpuzzlegame.ui.UIFactory;
import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.command.data.CommandParameter;
import cn.nukkit.lang.LangCode;

public class MainCommand extends Command {

    private final SituationPuzzleGame plugin;

    public MainCommand(SituationPuzzleGame plugin) {
        super("hgt", Texts.t(plugin, LangCode.zh_CN, "title.app").replaceAll("§.", ""));
        this.plugin = plugin;
        this.setPermission("situationpuzzlegame.use");

        this.commandParameters.clear();
        this.commandParameters.put(
                "rank",
                new CommandParameter[] {CommandParameter.newEnum("rank", new String[] {"rank", "top"})});
        this.commandParameters.put(
                "stats",
                new CommandParameter[] {CommandParameter.newEnum("stats", new String[] {"stats"})});
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Texts.t(plugin, LangCode.zh_CN, "message.command-player-only"));
            return true;
        }

        if (args.length > 0 && plugin.getPluginConfig().isStatsEnabled()) {
            switch (args[0].toLowerCase()) {
                case "rank", "top" -> {
                    if (UIFactory.supportsDdui(player)) {
                        UIFactory.showLeaderboardMenu(plugin, player);
                    } else {
                        plugin.getLegacyUIFactory().showLeaderboardMenu(player);
                    }
                    return true;
                }
                case "stats" -> {
                    if (UIFactory.supportsDdui(player)) {
                        UIFactory.showPersonalStats(plugin, player);
                    } else {
                        plugin.getLegacyUIFactory().showPersonalStats(player);
                    }
                    return true;
                }
            }
        }

        GameRoom room = plugin.getPlayerRoom(player);
        if (!UIFactory.supportsDdui(player)) {
            plugin.getLegacyUIFactory().showContext(player);
        } else if (room == null) {
            UIFactory.showMainMenu(plugin, player);
        } else {
            UIFactory.showContextForm(plugin, player, room);
        }
        return true;
    }
}
