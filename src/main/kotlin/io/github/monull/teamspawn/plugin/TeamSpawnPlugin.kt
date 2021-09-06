package io.github.monull.teamspawn.plugin

import io.github.monun.kommand.kommand
import net.kyori.adventure.text.Component.text
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scoreboard.Team
import java.io.File
import java.util.*

class TeamSpawnPlugin : JavaPlugin(), Listener {

    private lateinit var spawnFile: File
    private lateinit var spawns: TreeMap<String, Location>

    override fun onEnable() {
        val folder = dataFolder.also { it.mkdirs() }
        spawnFile = File(folder, "spawns.yml")

        spawns = TreeMap()

        if (spawnFile.exists()) {
            val config = YamlConfiguration.loadConfiguration(spawnFile)
            for ((key, section) in config.getValues(false)) {
                if (section is ConfigurationSection) {
                    val world = section.getString("world")!!.let { Bukkit.getWorld(it)!! }
                    val x = section.getDouble("x")
                    val y = section.getDouble("y")
                    val z = section.getDouble("z")
                    val yaw = section.getDouble("yaw").toFloat()
                    val pitch = section.getDouble("pitch").toFloat()
                    spawns[key] = Location(world, x, y, z, yaw, pitch)
                }
            }
        }

        setupKommand()
        server.pluginManager.registerEvents(this, this)
    }

    private fun setupKommand() = kommand {
        register("teamspawn", "ts") {
            then("setspawn") {
                then("team" to team()) {
                    requires { playerOrNull != null }
                    executes {
                        val player = sender as Player
                        val team: Team = it["team"]
                        val name = team.name
                        val loc = player.location
                        spawns[name] = loc

                        feedback(text("$name = $loc"))
                    }
                }
            }
            then("spawn") {
                then("team" to team()) {
                    executes {
                        val team: Team = it["team"]
                        val name = team.name
                        spawns[name]?.let { loc ->
                            team.entries.mapNotNull { playerName -> Bukkit.getPlayerExact(playerName) }
                                .forEach { player ->
                                    player.teleport(loc)
                                }
                        }
                    }
                }
            }
            then("spawnall") {
                executes {
                    val scoreboard = Bukkit.getScoreboardManager().mainScoreboard
                    for ((name, loc) in spawns) {
                        scoreboard.getTeam(name)?.entries?.forEach {
                            Bukkit.getPlayer(it)?.teleport(loc, PlayerTeleportEvent.TeleportCause.PLUGIN)
                        }
                    }
                }
            }
        }
    }

    override fun onDisable() {
        dataFolder.mkdirs()
        val config = YamlConfiguration()

        for ((key, loc) in spawns) {
            val section = config.createSection(key)
            section["world"] = loc.world.name
            section["x"] = loc.x
            section["y"] = loc.y
            section["z"] = loc.z
            section["yaw"] = loc.yaw
            section["pitch"] = loc.pitch
        }

        config.save(spawnFile)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        Bukkit.getScoreboardManager().mainScoreboard.getEntryTeam(player.name)?.let { team ->
            spawns[team.name]?.let { event.respawnLocation = it }
        }
    }
}