// DungeonGenerator.java
// Save this as src/main/java/com/example/dungeongen/DungeonGenerator.java

package com.example.dungeongen;

import org.bukkit.*;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.generator.structure.StructureType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootTables;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class DungeonGenerator extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("Random Dungeon Generator loaded! Use /dungeon");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("dungeon") && sender.hasPermission("dungeon.generate")) {
            if (!(sender instanceof org.bukkit.entity.Player player)) {
                sender.sendMessage("Only players can use this command!");
                return true;
            }

            Location start = player.getLocation().clone().subtract(0, 15, 0); // 15 blocks below player
            player.sendMessage(ChatColor.GREEN + "Generating dungeon below you...");

            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                generateDungeon(start.getWorld(), start.getBlockX(), start.getBlockY(), start.getBlockZ());
                Bukkit.getScheduler().runTask(this, () ->
                        player.sendMessage(ChatColor.GREEN + "Dungeon generated! Go down to explore!"));
            });

            return true;
        }
        return false;
    }

    private void generateDungeon(World world, int originX, int originY, int originZ) {
        Random rand = new Random();
        int size = 7 + rand.nextInt(6); // 7–12 rooms

        List<Room> rooms = new ArrayList<>();
        List<Room> placed = new ArrayList<>();

        // Create first room
        Room first = new Room(originX, originY, originZ, 5 + rand.nextInt(6), 4, 5 + rand.nextInt(6));
        rooms.add(first);
        placed.add(first);
        first.carve(world);

        // Generate additional rooms
        for (int i = 0; i < size && !rooms.isEmpty(); i++) {
            Room room = rooms.remove(rand.nextInt(rooms.size()));
            int attempts = 0;

            while (attempts < 20) {
                int dir = rand.nextInt(4); // N E S W
                int width = 4 + rand.nextInt(7);
                int length = 4 + rand.nextInt(7);

                int nx = room.x;
                int nz = room.z;

                if (dir == 0) nz -= length + 3;      // North
                else if (dir == 1) nx += room.width + 3;  // East
                else if (dir == 2) nz += room.length + 3; // South
                else nx -= width + 3;                // West

                Room newRoom = new Room(nx, originY, nz, width, 4, length);
                if (!overlaps(placed, newRoom)) {
                    connectRooms(world, room, newRoom, dir);
                    newRoom.carve(world);
                    rooms.add(newRoom);
                    placed.add(newRoom);
                    break;
                }
                attempts++;
            }
        }
    }

    private boolean overlaps(List<Room> placed, Room newRoom) {
        for (Room r : placed) {
            if (Math.abs(r.x - newRoom.x) < (r.width + newRoom.width) / 2 + 5 &&
                Math.abs(r.z - newRoom.z) < (r.length + newRoom.length) / 2 + 5) {
                return true;
            }
        }
        return false;
    }

    private void connectRooms(World world, Room a, Room b, int direction) {
        int x1 = a.x + a.width / 2;
        int z1 = a.z + a.length / 2;
        int x2 = b.x + b.width / 2;
        int z2 = b.z + b.length / 2;

        int y = a.y + 1;

        while (x1 != x2 || z1 != z2) {
            if (x1 < x2) x1++;
            else if (x1 > x2) x1--;
            if (z1 < z2) z1++;
            else if (z1 > z2) z1--;

            for (int dy = 0; dy < 3; dy++) {
                world.getBlockAt(x1, y + dy, z1).setType(Material.AIR);
            }
            world.getBlockAt(x1, y - 1, z1).setType(Material.STONE_BRICKS);
        }
    }

    private static class Room {
        int x, y, z, width, height = 4, length;

        Room(int x, int y, int z, int w, int h, int l) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.width = w;
            this.height = h;
            this.length = l;
        }

        void carve(World world) {
            Random rand = new Random();
            Material wall = rand.nextBoolean() ? Material.STONE_BRICKS : Material.MOSSY_STONE_BRICKS;
            Material cracked = Material.CRACKED_STONE_BRICKS;

            // Floor
            for (int dx = -1; dx <= width + 1; dx++) {
                for (int dz = -1; dz <= length + 1; dz++) {
                    world.getBlockAt(x + dx, y - 1, z + dz).setType(Material.STONE_BRICKS);
                }
            }

            // Walls & Ceiling
            for (int dx = 0; dx <= width; dx++) {
                for (int dy = 0; dy <= height; dy++) {
                    world.getBlockAt(x + dx, y + dy, z).setType(dy == height ? Material.STONE_BRICKS : wall);
                    world.getBlockAt(x + dx, y + dy, z + length).setType(dy == height ? Material.STONE_BRICKS : wall);
                }
            }
            for (int dz = 0; dz <= length; dz++) {
                for (int dy = 0; dy <= height; dy++) {
                    world.getBlockAt(x, y + dy, z + dz).setType(dy == height ? Material.STONE_BRICKS : wall);
                    world.getBlockAt(x + width, y + dy, z + dz).setType(dy == height ? Material.STONE_BRICKS : wall);
                }
            }

            // Air inside
            for (int dx = 1; dx < width; dx++) {
                for (int dz = 1; dz < length; dz++) {
                    for (int dy = 0; dy < height; dy++) {
                        world.getBlockAt(x + dx, y + dy, z + dz).setType(Material.AIR);
                    }
                }
            }

            // Decorations
            if (rand.nextBoolean()) {
                int sx = x + 2 + rand.nextInt(width - 3);
                int sz = z + 2 + rand.nextInt(length - 3);
                world.getBlockAt(sx, y, sz).setType(Material.SPAWNER);
                CreatureSpawner spawner = (CreatureSpawner) world.getBlockAt(sx, y, sz).getState();
                spawner.setSpawnedType(rand.nextBoolean() ? EntityType.ZOMBIE : EntityType.SKELETON);
                spawner.update();
            }

            if (rand.nextInt(3) == 0) {
                int cx = x + 1 + rand.nextInt(width - 1);
                int cz = z + 1 + rand.nextInt(length - 1);
                world.getBlockAt(cx, y, cz).setType(Material.CHEST);
                org.bukkit.block.Chest chest = (org.bukkit.block.Chest) world.getBlockAt(cx, y, cz).getState();
                chest.setLootTable(LootTables.SIMPLE_DUNGEON.getLootTable());
                chest.update();
            }
        }
    }
}
