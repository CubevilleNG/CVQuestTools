package org.cubeville.cvquesttools;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.utility.MinecraftReflection;
import com.griefcraft.lwc.LWC;
import com.griefcraft.lwc.LWCPlugin;
import com.griefcraft.model.Protection;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.citizensnpcs.api.CitizensAPI;
import org.betonquest.betonquest.BetonQuest;
import org.betonquest.betonquest.exceptions.ObjectNotFoundException;
import org.betonquest.betonquest.id.ConditionID;
import org.betonquest.betonquest.utils.PlayerConverter;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.cubeville.cvloadouts.CVLoadouts;
import org.cubeville.cvloadouts.loadout.LoadoutContainer;

public class CVQuestTools extends JavaPlugin implements Listener {
    final String QUESTWORLD = "questworld";

    BetonQuest betonQuest;

    LWC lwc;

    CVLoadouts cvloadouts;

    Map<String, String> questItems;

    Map<UUID, Integer> playerFishCount;

    Map<UUID, Location> obsidianBlockLocation = new HashMap<>();

    public void onEnable() {
        PluginManager pm = getServer().getPluginManager();
        this.betonQuest = (BetonQuest)pm.getPlugin("BetonQuest");
        this.lwc = ((LWCPlugin)pm.getPlugin("LWC")).getLWC();
        this.cvloadouts = (CVLoadouts)pm.getPlugin("CVLoadouts");
        pm.registerEvents(this, this);
        this.questItems = new HashMap<>();
        if (getConfig().getConfigurationSection("questitems") != null) {
            Set<String> l = getConfig().getConfigurationSection("questitems").getKeys(false);
            if (l != null) {
                for (String questItem : l) {
                    System.out.println("Adding quest item: " + l);
                    this.questItems.put(questItem, getConfig().getConfigurationSection("questitems").getString(questItem));
                }
                System.out.println("List of items after loading:");
                for (String questItem : this.questItems.keySet())
                    System.out.println(questItem + ": " + (String)this.questItems.get(questItem));
            }
        }
        this.playerFishCount = new HashMap<>();
        Bukkit.getServer().clearRecipes();
        Bukkit.getServer().addRecipe((Recipe)(new ShapedRecipe(new NamespacedKey((Plugin)this, "glistering_melon_recipe"), new ItemStack(Material.GLISTERING_MELON_SLICE))).shape(new String[] { "OOO", "OXO", "OOO" }).setIngredient('O', Material.GOLD_NUGGET).setIngredient('X', Material.MELON_SLICE));
        (new BukkitRunnable() {
            public void run() {
                World world = Bukkit.getWorld("questworld");
                List<Entity> entities = world.getEntities();
                List<Player> players = world.getPlayers();
                for (Entity e : entities) {
                    if (e.getType() == EntityType.BOAT &&
                            !CitizensAPI.getNPCRegistry().isNPC(e)) {
                        boolean playerIsClose = false;
                        for (Player p : players) {
                            if (p.getLocation().distance(e.getLocation()) < 100.0D) {
                                playerIsClose = true;
                                break;
                            }
                        }
                        if (!playerIsClose &&
                                e.isEmpty())
                            e.remove();
                    }
                }
            }
        }).runTaskTimer((Plugin)this, 100L, 100L);
    }

    private class BlockWithDistance implements Comparable<BlockWithDistance> {
        private int x;

        private int y;

        private int z;

        private double distance;

        public BlockWithDistance(int x, int y, int z, double distance) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.distance = distance;
        }

        public int compareTo(BlockWithDistance other) {
            if (other.getDistance() < this.distance)
                return 1;
            if (other.getDistance() > this.distance)
                return -1;
            return 0;
        }

        public int getX() {
            return this.x;
        }

        public int getY() {
            return this.y;
        }

        public int getZ() {
            return this.z;
        }

        public double getDistance() {
            return this.distance;
        }
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equals("clearitems")) {
            if (args.length == 0) {
                clearItems((Player)sender);
            } else {
                if (!sender.hasPermission("cvquesttools.markitems")) {
                    sender.sendMessage("§c/clearitems");
                    return true;
                }
                Player player = (Player)sender;
                String itemName = player.getInventory().getItemInMainHand().getItemMeta().getDisplayName();
                if (args.length <= 2 && args[0].equals("mark")) {
                    if (args.length == 1) {
                        this.questItems.put(itemName, "");
                    } else {
                        this.questItems.put(itemName, args[1]);
                    }
                    updateConfig();
                    sender.sendMessage("§aItem §r" + itemName + "§r§a marked as quest item.");
                } else if (args.length == 1 && args[0].equals("unmark")) {
                    this.questItems.remove(itemName);
                    updateConfig();
                    sender.sendMessage("§aItem §r" + itemName + "§r§a removed from quest items.");
                } else {
                    sender.sendMessage("§c/clearitems unmark or /clearitems mark [condition]");
                }
            }
            return true;
        }
        if (command.getName().equals("tweeclea")) {
            int radius = 5;
            if (args.length == 1)
                radius = Integer.valueOf(args[0]).intValue();
            Player player = (Player)sender;
            Location loc = player.getLocation();
            World world = loc.getWorld();
            int px = loc.getBlockX();
            int py = loc.getBlockY();
            int pz = loc.getBlockZ();
            ItemStack hItem = player.getInventory().getItemInMainHand();
            for (int x = px - radius; x <= px + radius; x++) {
                for (int y = py - radius; y <= py + radius; y++) {
                    for (int z = pz - radius; z <= pz + radius; z++) {
                        if (world.getBlockAt(x, y, z).getType() == hItem.getType())
                            for (int i = 0; i < 15; i++) {
                                if (world.getBlockAt(x, y + i, z).getType() == Material.AIR) {
                                    for (int j = 0; j < i; j++)
                                        world.getBlockAt(x, y + j, z).setType(Material.AIR);
                                    break;
                                }
                                if (world.getBlockAt(x, y + i, z).getType() != hItem.getType() && world.getBlockAt(x, y + i, z).getType() != Material.NETHER_BRICK_FENCE)
                                    break;
                                if (world.getBlockAt(x, y + i, z + 1).getType() != Material.AIR || world.getBlockAt(x, y + i, z - 1).getType() != Material.AIR || world
                                        .getBlockAt(x + 1, y + i, z).getType() != Material.AIR || world.getBlockAt(x - 1, y + i, z).getType() != Material.AIR)
                                    break;
                            }
                    }
                }
            }
        } else if (command.getName().equals("openbook")) {
            Player player = Bukkit.getPlayer(args[0]);
            if (player == null) {
                sender.sendMessage("not found!");
                return true;
            }
            String loadoutName = args[1];
            LoadoutContainer lc = this.cvloadouts.getLoadoutManager().getLoadoutByName(loadoutName);
            if (lc == null) {
                sender.sendMessage("not found!");
                return true;
            }
            ItemStack book = lc.getMainInventory().getItem(Integer.valueOf(args[2]).intValue());
            if (book == null) {
                sender.sendMessage("not found!");
                return true;
            }
            if (book.getType() != Material.WRITTEN_BOOK) {
                sender.sendMessage("must be a book!");
                return true;
            }
            int slot = player.getInventory().getHeldItemSlot();
            ItemStack oldItem = player.getInventory().getItem(slot);
            player.getInventory().setItem(slot, book);
            try {
                PacketContainer pc = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.CUSTOM_PAYLOAD);
                pc.getModifier().writeDefaults();
                ByteBuf bf = Unpooled.buffer(256);
                bf.setByte(0, 0);
                bf.writerIndex(1);
                pc.getModifier().write(1, MinecraftReflection.getPacketDataSerializer(bf));
                pc.getStrings().write(0, "MC|BOpen");
                ProtocolLibrary.getProtocolManager().sendServerPacket(player, pc);
            } catch (Exception e) {
                e.printStackTrace();
            }
            player.getInventory().setItem(slot, oldItem);
        } else if (command.getName().equals("findunlocked")) {
            List<Material> doorMaterials = new ArrayList<>();
            List<Material> fenceMaterials = new ArrayList<>();
            Player player = (Player)sender;
            Location loc = player.getLocation();
            int xc = loc.getBlockX();
            int yc = loc.getBlockY();
            int zc = loc.getBlockZ();
            int ymin = yc - 50;
            if (ymin < 0)
                ymin = 0;
            int ymax = yc + 50;
            if (ymax > 254)
                ymax = 254;
            List<BlockWithDistance> blocks = new ArrayList<>();
            for (int x = xc - 50; x <= xc + 50; x++) {
                for (int y = ymin; y <= ymax; y++) {
                    for (int z = zc - 50; z <= zc + 50; z++) {
                        Block block = loc.getWorld().getBlockAt(x, y, z);
                        if (y > 0 && doorMaterials.contains(block.getType())) {
                            Block blockUnder = loc.getWorld().getBlockAt(x, y - 1, z);
                            if (block.getType() == blockUnder.getType())
                                continue;
                        }
                        if ((doorMaterials.contains(block.getType()) || fenceMaterials.contains(block.getType())) &&
                                this.lwc.findProtection(block) == null)
                            blocks.add(new BlockWithDistance(x, y, z, loc.distance(new Location(player.getWorld(), x, y, z))));
                        if (doorMaterials.contains(block.getType()) && this.lwc.findProtection(block) != null && block.getData() <= 3 &&
                                this.lwc.findProtection(block).getType() == Protection.Type.PRIVATE)
                            player.sendMessage("closed door at " + block.getLocation());
                        continue;
                    }
                }
            }
            if (blocks.size() == 0) {
                player.sendMessage("unlocked blocks found. :D");
            } else {
                Collections.sort(blocks);
                for (int i = 0; i < blocks.size(); i++) {
                    if (i == 5) {
                        player.sendMessage("" + (blocks.size() - 5) + " more.");
                        break;
                    }
                    BlockWithDistance b = blocks.get(i);
                    String tr = "tellraw " + player.getName() + " [\"\",{\"text\":\"Wooden door at \"},{\"text\":\"";
                    tr = tr + b.getX() + "/" + b.getY() + "/" + b.getZ();
                    tr = tr + "\",\"color\":\"red\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/tppos ";
                    tr = tr + b.getX() + " " + b.getY() + " " + b.getZ();
                    tr = tr + "\"}}]";
                    Bukkit.dispatchCommand((CommandSender)player, tr);
                }
            }
        } else if (command.getName().equals("clearobbyaltair")) {
            UUID playerId = Bukkit.getPlayer(args[0]).getUniqueId();
            Location location = this.obsidianBlockLocation.get(playerId);
            World world = Bukkit.getWorld("questworld");
            if (location != null) {
                Block block = world.getBlockAt(location);
                block.setType(Material.AIR);
                location.add(0.0D, 1.0D, 0.0D);
                block = world.getBlockAt(location);
                block.setType(Material.AIR);
                this.obsidianBlockLocation.remove(playerId);
            }
        }
        return false;
    }

    private void updateConfig() {
        getConfig().set("questitems", this.questItems);
        saveConfig();
    }

    private void clearItems(Player player) {
        PlayerInventory playerInventory = player.getInventory();
        for (int i = 0; i < playerInventory.getSize(); i++) {
            ItemStack item = playerInventory.getItem(i);
            if (item != null) {
                String itemName = item.getItemMeta().getDisplayName();
                if (this.questItems.containsKey(itemName)) {
                    String condition = this.questItems.get(itemName);
                    if (condition != null && !condition.equals(""))
                        try {
                            boolean result = BetonQuest.condition(PlayerConverter.getID(player), new ConditionID(null, condition));
                            if (!result)
                                playerInventory.setItem(i, null);
                        } catch (Exception exception) {}
                } else {
                    playerInventory.setItem(i, null);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        boolean tutorialFinished = false;
        try {
            tutorialFinished = BetonQuest.condition(PlayerConverter.getID(player), new ConditionID(null, "tutorial.finished"));
        } catch (Exception e) {
            System.out.println("Could not get player's tutorial status: " + e.getMessage());
        }
        if (!tutorialFinished) {
            System.out.println("Teleporting player " + player.getName() + " to the tutorial start.");
            (new BukkitRunnable() {
                public void run() {
                    Bukkit.dispatchCommand((CommandSender)Bukkit.getConsoleSender(), "cvportal trigger tutorial_spawn player:" + player.getName() + " force");
                }
            }).runTaskLater((Plugin)this, 3L);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getPlayer().getGameMode() == GameMode.SURVIVAL)
            if (block.getType() == Material.ANVIL) {
                event.setCancelled(true);
            } else if (block.getType() == Material.ENDER_CHEST || block.getType() == Material.HOPPER || block.getType() == Material.CAULDRON) {
                event.setCancelled(true);
            }
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Material item = event.getMaterial();
            if (item == Material.WHEAT)
                event.getPlayer().openInventory(event.getPlayer().getEnderChest());
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (entity.getType() == EntityType.BOAT &&
                CitizensAPI.getNPCRegistry().isNPC(entity)) {
            event.getPlayer().sendMessage("That's not your boat, you scallywag!");
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        clearItems(event.getPlayer());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        event.setKeepInventory(true);
    }

    @EventHandler
    public void onBlockGrow(BlockGrowEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onStructureGrow(StructureGrowEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {}

    @EventHandler
    public void onCraftItem(CraftItemEvent event) {}

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreakEvent(BlockBreakEvent event) {
        if (event.isCancelled())
            return;
        if (event.getPlayer().getGameMode() != GameMode.SURVIVAL)
            return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockPlaceEvent(BlockPlaceEvent event) {
        if (event.isCancelled())
            return;
        if (event.getPlayer().getGameMode() != GameMode.SURVIVAL)
            return;
        Block block = event.getBlock();
        Location location = block.getLocation();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        if (y == 148 && x >= -525 && x <= -507 && z >= 1094 && z <= 1116)
            try {
                if (BetonQuest.condition(PlayerConverter.getID(event.getPlayer()), new ConditionID(null, "dr_quest.dr_witchdr_moon_started"))) {
                    ItemStack item = event.getItemInHand();
                    if (item.getType() == Material.OBSIDIAN &&
                            item.hasItemMeta() && item.getItemMeta().hasDisplayName() && item.getItemMeta().getDisplayName().equals("§r§6Obsidian")) {
                    long currentTime = Bukkit.getWorld("questworld").getTime();
                    if (currentTime >= 23000L || currentTime <= 12000L) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "cvtools title " + event.getPlayer().getName() + " \"\" \"&cThe infusion must be performed at night!\"");
                    } else {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "minecraft:clone -510 134 1106 -510 134 1106 " + x + " " + (y + 1) + " " + z);
                        this.obsidianBlockLocation.put(event.getPlayer().getUniqueId(), location);
                        String cmd = "q e " + event.getPlayer().getName() + " dr_quest.dr_witchdr_moon_placed";
                        System.out.println("Running: " + cmd);
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "fx particleplayer dr_wd_pearlinfusion .1 .1 questworld " + (x + 0.5D) + "," + (y + 1) + "," + (z + 0.5D) + " 0 -90");
                        return;
                    }
                }
            }
    } catch(ObjectNotFoundException ignored) {}
    event.setCancelled(true);
}
}
