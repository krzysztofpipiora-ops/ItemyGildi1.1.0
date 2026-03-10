package pl.twojserwer.guilditems;

import net.dzikoysk.funnyguilds.user.User;
import net.dzikoysk.funnyguilds.guild.Guild;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import java.util.*;

public class GuildItemsPlugin extends JavaPlugin implements Listener {

    private final HashMap<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final NamespacedKey itemKey = new NamespacedKey(this, "guild_item_id");
    private final Map<Location, FlagData> placedFlags = new HashMap<>();

    private static class FlagData {
        String id;
        UUID ownerUUID;
        FlagData(String id, UUID ownerUUID) {
            this.id = id;
            this.ownerUUID = ownerUUID;
        }
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        registerAllItems();
        startAuraTask();
        getLogger().info("GuildItems (FunnyGuilds 4.14) wlaczony!");
    }

    private void registerAllItems() {
        createRecipe("strength_flag", Material.RED_BANNER, "§c§lSztandar Siły", 1011, "SSS", "SBS", " P ", 'S', Material.BLAZE_POWDER, 'B', Material.RED_BANNER, 'P', Material.BLAZE_ROD);
        createRecipe("defense_flag", Material.BLUE_BANNER, "§9§lSztandar Obrony", 1012, "III", "IBI", " P ", 'I', Material.IRON_BLOCK, 'B', Material.BLUE_BANNER, 'P', Material.BLAZE_ROD);
        createRecipe("vampire_sword", Material.NETHERITE_SWORD, "§4§lOstrze Wampira", 1001, "RNR", "RSR", " R ", 'R', Material.REDSTONE_BLOCK, 'N', Material.NETHERITE_INGOT, 'S', Material.NETHERITE_SWORD);
        createRecipe("thor_hammer", Material.NETHERITE_AXE, "§e§lMłot Thora", 1003, "III", "ISI", " S ", 'I', Material.IRON_BLOCK, 'S', Material.BLAZE_ROD);
    }

    private void createRecipe(String id, Material mat, String name, int cmd, String s1, String s2, String s3, Object... ing) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setCustomModelData(cmd);
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, id);
            item.setItemMeta(meta);
            ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(this, id), item);
            recipe.shape(s1, s2, s3);
            for (int i = 0; i < ing.length; i += 2) {
                recipe.setIngredient((Character) ing[i], (Material) ing[i + 1]);
            }
            Bukkit.addRecipe(recipe);
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        ItemStack item = e.getItemInHand();
        if (item.hasItemMeta()) {
            String id = item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
            if (id != null && (id.equals("strength_flag") || id.equals("defense_flag"))) {
                placedFlags.put(e.getBlock().getLocation(), new FlagData(id, e.getPlayer().getUniqueId()));
                e.getPlayer().sendMessage("§aPostawiono sztandar gildyjny!");
            }
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        placedFlags.remove(e.getBlock().getLocation());
    }

    private void startAuraTask() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                for (Map.Entry<Location, FlagData> entry : placedFlags.entrySet()) {
                    Location loc = entry.getKey();
                    if (loc.getWorld().equals(p.getWorld()) && loc.distance(p.getLocation()) <= 10) {
                        if (isSameGuild(p, entry.getValue().ownerUUID)) {
                            giveAuraEffect(p, entry.getValue().id);
                        }
                    }
                }
            }
        }, 20L, 20L);
    }

    private boolean isSameGuild(Player p, UUID ownerUUID) {
        if (p.getUniqueId().equals(ownerUUID)) return true;
        User user = User.get(p.getUniqueId());
        User owner = User.get(ownerUUID);
        if (user != null && owner != null) {
            Guild userGuild = user.getGuild();
            Guild ownerGuild = owner.getGuild();
            return userGuild != null && userGuild.equals(ownerGuild);
        }
        return false;
    }

    private void giveAuraEffect(Player p, String id) {
        if (id.equals("strength_flag")) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 45, 0, true, false));
        } else if (id.equals("defense_flag")) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 45, 0, true, false));
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        ItemStack item = e.getItem();
        if (item == null || !item.hasItemMeta()) return;
        String id = item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        if (id == null) return;
        Player p = e.getPlayer();
        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (!checkCooldown(p, id, 10)) return;
            if (id.equals("vampire_sword")) p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
            if (id.equals("thor_hammer")) {
                Block target = p.getTargetBlockExact(30);
                if (target != null) p.getWorld().strikeLightning(target.getLocation());
            }
        }
    }

    private boolean checkCooldown(Player p, String item, int sec) {
        cooldowns.putIfAbsent(p.getUniqueId(), new HashMap<>());
        long now = System.currentTimeMillis();
        long last = cooldowns.get(p.getUniqueId()).getOrDefault(item, 0L);
        if (now - last < sec * 1000L) {
            p.sendMessage("§cZaczekaj!");
            return false;
        }
        cooldowns.get(p.getUniqueId()).put(item, now);
        return true;
    }
}
