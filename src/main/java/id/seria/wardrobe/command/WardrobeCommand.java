package id.seria.wardrobe.command;

import id.seria.wardrobe.SeriaWardrobePlugin;
import id.seria.wardrobe.gui.WardrobeGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

/**
 * /wardrobe — opens the player's wardrobe GUI.
 */
public class WardrobeCommand implements CommandExecutor, TabCompleter {

    private final SeriaWardrobePlugin plugin;

    public WardrobeCommand(SeriaWardrobePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        if (!player.hasPermission("seriawardrobe.use")) {
            player.sendMessage(plugin.getConfig().getString(
                    "messages.no-permission", "§cYou don't have permission to use the wardrobe."));
            return true;
        }

        // Build and open the GUI
        WardrobeGUI gui = new WardrobeGUI(plugin, player);
        player.openInventory(gui.getInventory());

        player.sendMessage(plugin.getConfig().getString(
                "messages.wardrobe-opened", "§7Opening your wardrobe…"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
