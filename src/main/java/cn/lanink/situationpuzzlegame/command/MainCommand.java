package cn.lanink.situationpuzzlegame.command;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.command.data.CommandParameter;
import cn.lanink.situationpuzzlegame.SituationPuzzleGame;
import cn.lanink.situationpuzzlegame.game.GameRoom;
import cn.lanink.situationpuzzlegame.ui.UIFactory;

public class MainCommand extends Command {

    private final SituationPuzzleGame plugin;

    public MainCommand(SituationPuzzleGame plugin) {
        super("hgt", "海龟汤游戏");
        this.plugin = plugin;
        this.setPermission("situationpuzzlegame.use");

        this.commandParameters.clear();
        this.commandParameters.put("rank", new CommandParameter[]{
                CommandParameter.newEnum("rank", new String[]{"rank", "top"})
        });
        this.commandParameters.put("stats", new CommandParameter[]{
                CommandParameter.newEnum("stats", new String[]{"stats"})
        });
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c该命令只能由玩家执行！");
            return true;
        }

        if (args.length > 0 && plugin.getPluginConfig().isStatsEnabled()) {
            switch (args[0].toLowerCase()) {
                case "rank", "top" -> {
                    UIFactory.showLeaderboardMenu(plugin, player);
                    return true;
                }
                case "stats" -> {
                    UIFactory.showPersonalStats(plugin, player);
                    return true;
                }
            }
        }

        GameRoom room = plugin.getPlayerRoom(player);
        if (room == null) {
            UIFactory.showMainMenu(plugin, player);
        } else {
            UIFactory.showContextForm(plugin, player, room);
        }
        return true;
    }
}
