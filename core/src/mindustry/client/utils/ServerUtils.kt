@file:Suppress("EnumEntryName") @file:JvmName("ServerUtils")

package mindustry.client.utils

import arc.*
import arc.graphics.*
import arc.struct.*
import arc.util.*
import mindustry.Vars.*
import mindustry.client.*
import mindustry.client.ui.*
import mindustry.client.utils.*
import mindustry.content.*
import mindustry.content.Blocks.*
import mindustry.content.UnitTypes.*
import mindustry.entities.*
import mindustry.entities.abilities.*
import mindustry.entities.bullet.*
import mindustry.game.EventType.*
import mindustry.gen.*
import mindustry.graphics.*
import mindustry.mod.*
import mindustry.net.Packets.*
import mindustry.ui.fragments.ChatFragment.*
import mindustry.world.blocks.defense.turrets.*
import java.lang.reflect.*
import kotlin.properties.*

enum class Server( // FINISHME: This is horrible. Why have I done this?
    private val groupName: String?,
    private val mapVote: MapVote? = null,
    @JvmField val whisper: Cmd = Cmd("/w", -1), // FINISHME: This system still sucks despite my best efforts at making it good
    private val rtv: Cmd = Cmd("/rtv", -1),
    @JvmField val freeze: Cmd = Cmd("/freeze", -1),
    @JvmField val ghost: Boolean = false,
    private val votekickString: String = "Type[orange] /vote <y/n>[] to agree.",
    @JvmField var blockAnnoyances: Boolean = true
) {
    other(null),
    nydus("nydus"),
    cn("Chaotic Neutral", rtv = Cmd("/rtv")),
    io("io", MapVote(), Cmd("/w"), Cmd("/rtv"), object : Cmd("/freeze", 4) {
        override fun run(vararg args: String) { // Freeze command requires admin in game but the packet does not
            if (!player.admin) Call.serverPacketReliable("freeze_by_id", args[0]) // Yes this will cause a crash when args.size == 0, it shouldn't happen
            else super.run(*args)
        }
    }, votekickString = "Type[orange] /vote <y/n>[] to vote.") {
        val tdAgree: Cmd = Cmd("/agree", 0)
        val tdString: String = "Type [green]/agree[] to vote!"
        override fun handleBan(p: Player) {
            ui.showTextInput("@client.banreason.title", "@client.banreason.body", "Griefing.") { reason ->
                val id = p.trace?.uuid ?: p.serverID
                if (id != null) {
                    ui.showConfirm("@confirm", "@client.rollback.title") {
                        Call.sendChatMessage("/rollback $id 5-f")
                    }
                }
                Call.adminRequest(p, AdminAction.ban, reason)
            }
        }

        override fun adminui() = player.admin || ClientVars.rank >= 5
        override fun handleVoteButtons(msg: ChatMessage) {
            super.handleVoteButtons(msg)
            if (msg.sender == null && CustomMode.tower_defense() && tdString in msg.message) {
                msg.addButton(tdAgree.str, tdAgree::invoke)
            }
        }
    },
    phoenix("Phoenix Network", null, Cmd("/w"), Cmd("/rtv"), Cmd("/freeze", 9), votekickString = "Type [cyan]/vote y"),
    korea("Korea", ghost = true),
    fish("Fish", null, Cmd("/msg"), blockAnnoyances = Core.settings.getBool("blockfishannoyances")) {
        override fun handleMessage(msg: String?, unformatted: String?, sender: Player?): Boolean {
            msg ?: return false
            if (sender == null && ohnoTask != null) { // Very hacky way of handling autoOhno
                if ("Too close to an enemy tile!" in msg || "You cannot spawn ohnos while dead." in msg) return true // We don't care honestly
                if ("Sorry, the max number of ohno units has been reached." in msg || "Ohnos have been temporarily disabled." in msg || "Ohnos are disabled in PVP." in msg || "Ohnos cannot survive in this map." in msg) {
                    Time.run(60f) { // Null it out a second later, this is just to prevent any additional messages from bypassing the return below (only if it's the same one we just cancelled).
                        if (ohnoTask?.isScheduled != true) ohnoTask = null
                    }
                    ohnoTask!!.cancel()
                    return true
                }
            }

            if (sender == null && "Fish Membership" in msg) return true // Adblock

            return false // All other messages are okay
        }

        /** Fish staff spam obnoxious particle rings */
        override fun blockEffect(fx: Effect, rot: Float): Boolean {
            return blockAnnoyances && rot == 0F && fx == Fx.pointBeam
        }
    },
    darkdustry("Darkdustry")
    ;

    companion object {
        open class Cmd(val str: String, private val rank: Int = 0) { // 0 = anyone, -1 = disabled
            val enabled = rank != -1

            open fun canRun() = rank == 0 || enabled && ClientVars.rank >= rank

            operator fun invoke(p: Player, vararg args: String) = invoke(current.playerString(p), *args)

            open operator fun invoke(vararg args: String) = when {
                !enabled -> Log.err("Command $str is disabled on this server.")
                !canRun() -> Log.err("You do not have permission to run $str on this server.")
                else -> run(*args)
            }

            protected open fun run(vararg args: String) = Call.sendChatMessage("$str ${args.joinToString(" ")}")
        }

        private class MapVote(val down: String = "/downvote", val none: String = "/novote", val up: String = "/upvote") {
            operator fun get(i: Int) = if (i == 0) down else if (i == 1) none else if (i == 2) up else null // Yes this is horrible but it saves lines.
        }

        @JvmField var current = other
//        val ghostList by lazy { Core.settings.getJson("ghostmodeservers", Seq::class.java, String::class.java) { Seq<String>() } as Seq<String> }

        @JvmStatic
        fun onServerJoin() { // Called once on server join before WorldLoadEvent (and by extension ServerJoinEvent), the player will not be added here hence the need for ServerJoinEvent
            val grouped = ui.join.communityHosts.groupBy({ it.group }) { it.address }
            val address = ui.join.lastHost?.address ?: ""
            if (ui.join.lastHost?.name?.contains("nydus") == true) current = nydus
            else entries.forEach {
                if (it.groupName != null && grouped[it.groupName]?.contains(address) == true) {
                    current = it
                    return@forEach
                }
            }
            Log.debug("Joining server, override set to: $current")
        }

        init {
            Events.on(MenuReturnEvent::class.java) {
                current = other
                Log.debug("Returning to menu, server override cleared")
            }
        }

        // FINISHME: Should also add a new ohno on player join (not really useful currently though cause ohno limit is broken and this could permanently lose an ohno)
        @JvmField var ohnoTask: Timer.Task? = null // FINISHME: Yet another reason this enum should be a class since this could be put in the fish class and not muddy everything else

        /** The destination ip and port of the server that we will be sent to by [mindustry.core.NetClient.connect] */
        @JvmField var destinationServer: String? = null
    }

    @JvmName("b") operator fun invoke() = current === this

    /** Converts a player object into a string for use in commands */
    open fun playerString(p: Player) = p.id.toString()

    /** Handle clickable vote buttons */
    open fun handleVoteButtons(msg: ChatMessage) {
        if (rtv.canRun()) msg.addButton(rtv.str, rtv::invoke) // FINISHME: I believe cn has a no option? not too sure
//        if (kick.canRun()) msg.addButton(kick.str, kick::invoke) FINISHME: Implement votekick buttons here
//        FINISHME: Add cn excavate buttons
    }

    /** Run when banning [p] */
    open fun handleBan(p: Player) = Call.adminRequest(p, AdminAction.ban, null)

    /** Whether or not the player has access to the admin ui in the player list */
    open fun adminui() = player.admin

    /** Map like/dislike */
    fun mapVote(i: Int) {
        if (mapVote != null) Call.sendChatMessage(mapVote[i] ?: run { Log.err("Invalid vote $i"); return })
        else Log.warn("Map votes are not available on server $name")
    }

    fun isVotekick(msg: String) = votekickString in msg

    /** Handles a message on a server. If true is returned, the message will be discarded and not printed. */
    open fun handleMessage(msg: String?, unformatted: String?, sender: Player?): Boolean = false

    /** Used to block effects on servers that spam them. */
    open fun blockEffect(fx: Effect, rot: Float): Boolean = false
}

enum class CustomMode {
    none,
    flood {
        val ioFloodCompatRepo = "mindustry-antigrief/FloodCompat"

        override fun enable() {
            super.enable()
            if (Server.io() && net.client()) {
                val floodcompatMod: Mods.LoadedMod? = mods.list().find { it.getRepo()?.lowercase() == ioFloodCompatRepo.lowercase() }
                Timer.schedule({
                if (floodcompatMod === null) {
                    ui.chatfrag.addMsg(
                        "[accent]Floodcompat mod [scarlet]not[] found! Installing the [white]${ioFloodCompatRepo}[] mod is recommended for a better game experience." +
                        "\n[green]INSTALL")
                    .addButton("INSTALL") {
                        Toast(3f).add("Downloading mod")
                        ui.mods.githubImportMod(ioFloodCompatRepo, false, null, "") {
                            Toast(3f).add("Downloaded!")
                            val mod: Mods.LoadedMod = mods.list().find { it.getRepo()?.lowercase() == ioFloodCompatRepo.lowercase() } ?: return@githubImportMod
                            mods.setEnabled(mod, true)
                            ui.chatfrag.addMsg("[accent]Mod downloaded! Restart game to apply mod.\n[red]RESTART").addButton("RESTART") { restartGame() };
                        }
                    }
                } else if (!floodcompatMod.enabled()){
                    ui.chatfrag.addMsg(
                        "[accent]Floodcompat mod [scarlet]disabled[]! Enabling it is recommended for a better game experience." +
                        "\n[green]ENABLE (requires restart)")
                    .addButton("ENABLE (requires restart)") {
                        mods.setEnabled(floodcompatMod, true)
                        restartGame()
                    }
                }
                }, 2f)
            }
        }
    },
    tower_defense;

    companion object {
        @JvmStatic var current by Delegates.observable(none) { _, oldValue, newValue ->
            if (oldValue == newValue) return@observable // This can happen.
            Log.debug("Swapping custom gamemode from $oldValue to $newValue")
            oldValue.disable()
            newValue.enable()
        }

        init {
            Events.on(WorldLoadEvent::class.java) {
                val modeName = if (!net.client() || ui.join.lastHost?.modeName?.isBlank() != false) state.rules.modeName?.lowercase() else ui.join.lastHost.modeName.lowercase()
                current = entries.find { it.name == modeName?.replace(" ", "_") } ?: none
            }

            Events.on(MenuReturnEvent::class.java) {
                Log.debug("Returning to menu")
                current = none
            }
        }

        private var defaults: MutableList<Any> = mutableListOf()

        /** Convenient way of adding multiple overwrites at once */
        private fun overwrites(vararg args: Any) =
            args.indices.step(3).forEach { overwrite(args[it], args[it + 1] as String, args[it + 2]) }

        private fun <O : Any, T : Any> overwrite(obj: O, name: String, value: T) {
            val split = name.split('.', limit = 2)
            val field = obj::class.java.getField(split[0])
            field.isAccessible = true

            // In the case of a string with periods, run the function recursively until we get to the last item which is then set
            if (split.size > 1) return overwrite(field.get(obj), split[1], value)

            defaults.add(obj)
            defaults.add(field)
            defaults.add(field.get(obj))
            field.set(obj, value)
        }
    }

    @JvmName("b") operator fun invoke() = CustomMode.current === this

    /** Called when this gamemode is detected */
    protected open fun enable() {
        defaults = mutableListOf()
    }

    /** Called when switching to a different gamemode */
    protected open fun disable() = // Don't have to worry about clearing defaults as it is replaced with a blank mutable list when the new gamemode is applied
        defaults.indices.step(3).forEach { (defaults[it + 1] as Field).set(defaults[it], defaults[it + 2]) } // (obj, field, value) -> field.set(obj, value)
}

fun handleKick(reason: String) {
    Log.debug("Kicked from server '${ui.join.lastHost?.name ?: "unknown"}' for: '$reason'.")
    if (reason == "Custom client detected.") {

    }
}

// FINISHME: The jank is growing worse. The servers really need their own classes
fun Server.Companion.ohno(): Timer.Task = Timer.schedule({ if (!player.blockOn().solid && alpha.supportsEnv(state.rules.env)) Call.sendChatMessage("/ohno") }, 3f, 0.5f)