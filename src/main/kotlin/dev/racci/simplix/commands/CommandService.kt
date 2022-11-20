package dev.racci.simplix.commands

import cloud.commandframework.ArgumentDescription
import cloud.commandframework.arguments.flags.CommandFlag
import cloud.commandframework.arguments.parser.ArgumentParseResult
import cloud.commandframework.arguments.standard.IntegerArgument
import cloud.commandframework.arguments.standard.StringArgument
import cloud.commandframework.bukkit.parsers.PlayerArgument
import cloud.commandframework.bukkit.parsers.WorldArgument
import cloud.commandframework.context.CommandContext
import cloud.commandframework.exceptions.InvalidCommandSenderException
import cloud.commandframework.execution.AsynchronousCommandExecutionCoordinator
import cloud.commandframework.kotlin.extension.buildAndRegister
import cloud.commandframework.meta.CommandMeta.Key
import cloud.commandframework.minecraft.extras.MinecraftExceptionHandler
import cloud.commandframework.minecraft.extras.RichDescription
import cloud.commandframework.paper.PaperCommandManager
import cloud.commandframework.permission.Permission
import com.destroystokyo.paper.block.TargetBlockInfo
import dev.racci.minix.api.annotations.MappedExtension
import dev.racci.minix.api.data.enums.LiquidType.Companion.liquidType
import dev.racci.minix.api.extension.Extension
import dev.racci.minix.api.extensions.displayName
import dev.racci.minix.api.extensions.message
import dev.racci.minix.api.extensions.msg
import dev.racci.minix.api.extensions.onlinePlayers
import dev.racci.minix.api.extensions.parse
import dev.racci.minix.api.extensions.reflection.castOrThrow
import dev.racci.simplix.Simplix
import net.kyori.adventure.audience.Audience.audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.World
import org.bukkit.attribute.Attribute
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

@MappedExtension(Simplix::class, "Command Service")
class CommandService(override val plugin: Simplix) : Extension<Simplix>() {
    private val attributeKey = Key.of(Attribute::class.java, "attribute")
    private lateinit var manager: PaperCommandManager<CommandSender>
    private lateinit var playerFlag: CommandFlag.Builder<Player>

    override suspend fun handleEnable() {
        if (!::manager.isInitialized) loadManager()

        this.broadcastCommand()
        this.attributeCommands()
        this.debuggingCommands()
        this.itemCommands()
    }

    private fun loadManager() {
        val coordinator = AsynchronousCommandExecutionCoordinator
            .newBuilder<CommandSender>()
            .withExecutor(dispatcher.get().executor)
            .withAsynchronousParsing()
            .build()

        manager = PaperCommandManager.createNative(plugin, coordinator)

        MinecraftExceptionHandler<CommandSender>()
            .withHandler(MinecraftExceptionHandler.ExceptionType.INVALID_SYNTAX) { _, e ->
                val exception = e.castOrThrow<InvalidCommandSenderException>()
                MiniMessage.miniMessage().deserialize("Invalid syntax: ${exception.currentChain.getOrNull(0)?.name} - ${exception.command?.arguments?.joinToString(", ")}")
            }
            .withHandler(MinecraftExceptionHandler.ExceptionType.COMMAND_EXECUTION) { _, e ->
                logger.error(e) { "Error while executing command" }
                MiniMessage.miniMessage().deserialize("An error occurred while executing this command: ${e.message}")
            }
            .withHandler(MinecraftExceptionHandler.ExceptionType.NO_PERMISSION) { _, e ->
                MiniMessage.miniMessage().deserialize("You do not have permission to execute this command: ${e.message}")
            }
            .withHandler(MinecraftExceptionHandler.ExceptionType.INVALID_SENDER) { _, e ->
                MiniMessage.miniMessage().deserialize("You cannot execute this command: ${e.message}")
//            }
//            .withDecorator { component ->
//                MiniMessage.miniMessage().deserialize(lang.prefixes.firstNotNullOf { it.value }).append(component)
            }.apply(manager) { it }

        playerFlag = manager.flagBuilder("player")
            .withDescription(RichDescription.of(MiniMessage.miniMessage().deserialize("The target player else the command sender.")))
            .withPermission(Permission.of("simplix.target.others"))
            .withAliases("p")
            .withArgument(PlayerArgument.newBuilder<Player>("player").asOptional().build())
    }

    private fun broadcastCommand() {
        manager.buildAndRegister(
            "broadcast",
            RichDescription.empty(),
            arrayOf("bc", "announce")
        ) {
            this.permission("simplix.broadcast")
            this.flag("world", arrayOf("w"), RichDescription.empty(), WorldArgument.newBuilder<CommandSender>("world").asOptional())
            this.flag("permission", arrayOf("p"), RichDescription.empty(), StringArgument.optional("permission"))
            this.argument(StringArgument.newBuilder<CommandSender?>("message").greedy().asOptional())
            this.handler { ctx ->
                val permission = ctx.getOrDefault<String>("permission", null)
                val world = ctx.getOrDefault<World>("world", null)
                val message = ctx.get<String>("message")

                val audience = when (world) {
                    null -> onlinePlayers
                    else -> world.players
                }.filter { player -> permission == null || player.hasPermission(permission) }.let(::audience)

                audience.sendMessage(message.parse())
            }
        }
    }

    private fun attributeCommands() {
        val attributeFlag = manager.flagBuilder("attribute")
            .withAliases("a")
            .withArgument(
                manager.argumentBuilder(Attribute::class.java, "attribute")
                    .withSuggestionsProvider { _, _ -> Attribute.values().map(Attribute::name).toMutableList() }
                    .withParser { _, inputQueue -> runCatching { ArgumentParseResult.success(Attribute.valueOf(inputQueue.poll().uppercase())) }.getOrElse { ArgumentParseResult.failure(it) } }
                    .asRequired()
            )

        manager.buildAndRegister("attributes", RichDescription.empty()) {
            this.registerCopy("value") {
                this.mutate { it.flag(attributeFlag) }
                this.mutate { it.flag(playerFlag) }
                this.handler { ctx ->
                    val player = getTargetOrThrow(ctx)
                    val attribute = ctx.flags().get<Attribute>("attribute")!!

                    ctx.sender.msg("Value: ${player.getAttribute(attribute)?.value}")
                }
            }

            this.registerCopy("modifiers") {
                this.mutate { it.flag(attributeFlag) }
                this.mutate { it.flag(playerFlag) }
                this.handler { ctx ->
                    val player = getTargetOrThrow(ctx)
                    val attribute = ctx.flags().get<Attribute>("attribute")!!

                    ctx.sender.msg("Modifiers:")
                    for (modifier in player.getAttribute(attribute)?.modifiers.orEmpty()) {
                        ctx.sender.msg("| $modifier")
                    }
                }
            }

            this.registerCopy("clearModifiers") {
                this.mutate { it.flag(attributeFlag) }
                this.mutate { it.flag(playerFlag) }
                this.handler { ctx ->
                    val player = getTargetOrThrow(ctx)
                    val attribute = ctx.flags().get<Attribute>("attribute")!!

                    player.getAttribute(attribute)?.modifiers?.clear()
                    ctx.sender.msg("Cleared modifiers for ${attribute.name}")
                }
            }
        }
    }

    private fun debuggingCommands() {
        manager.buildAndRegister("debug", RichDescription.empty()) {
            this.registerCopy("blockInfo") {
                this.senderType(Player::class.java)
                this.handler { ctx ->
                    val player = ctx.castOrThrow<CommandContext<Player>>().sender
                    val block = player.getTargetBlock(10, TargetBlockInfo.FluidMode.ALWAYS)

                    if (block == null) {
                        ctx.sender.msg("No block in range")
                        return@handler
                    }

                    ctx.sender.msg("Block: $block")
                    ctx.sender.msg("Block Type: ${block.type}")
                    ctx.sender.msg("Block Data: ${block.blockData}")
                    ctx.sender.msg("Block State: ${block.state}")
                    ctx.sender.msg("Block Location: ${block.location}")
                    ctx.sender.msg("Block Biome: ${block.biome}")
                    ctx.sender.msg("Block liquidType: ${block.liquidType}")
                }
            }
        }
    }

    private fun itemCommands() {
        manager.buildAndRegister("rename", RichDescription.empty()) {
            this.permission("simplix.rename")
            this.senderType<Player>()
            this.argument(StringArgument.newBuilder<CommandSender>("name").greedy().asRequired())
            this.handler { ctx ->
                val player = getTargetOrThrow(ctx)
                val name = ctx.get<String>("name")

                val item = player.inventory.itemInMainHand
                val formatted = MiniMessage.miniMessage().deserialize(name)

                item.displayName(formatted)

                Component.text("You have renamed ")
                    .append(Component.text("item").hoverEvent(item.asHoverEvent()))
                    .append(Component.text("to "))
                    .append(formatted)
                    .append(Component.text('.'))
                    .colorIfAbsent(NamedTextColor.AQUA) message player
            }
        }

        manager.buildAndRegister("editLore", RichDescription.empty()) {
            this.permission("simplix.lore")
            this.senderType<Player>()
            this.flag(
                "line",
                arrayOf("l"),
                ArgumentDescription.empty(),
                IntegerArgument.optional("line")
            )
            this.flag(
                "remove",
                arrayOf("r"),
                ArgumentDescription.empty()
            )
            this.flag(
                "offhand",
                arrayOf("o"),
                ArgumentDescription.empty()
            )
            this.argument(StringArgument.newBuilder<CommandSender>("lore").greedy().asOptional())
            this.handler { ctx ->
                val player = getTargetOrThrow(ctx)
                val line = ctx.flags().getValue<Int>("line")
                val removing = ctx.flags().contains("remove")
                val offhand = ctx.flags().contains("offhand")

                val item = if (offhand) player.inventory.itemInOffHand else player.inventory.itemInMainHand
                val existingLore = item.lore().orEmpty().toMutableList()

                if (removing) {
                    when {
                        line.isEmpty -> player.msg("You must provide a line number to remove.")
                        existingLore.size < line.get() -> player.msg("Selected item doesn't have lore line ${line.get()}.")
                        else -> {
                            val removedLine = existingLore[line.get()]
                            item.lore(existingLore - removedLine)
                            Component.text("Remove line ")
                                .append(removedLine)
                                .append(Component.text(" from "))
                                .append(Component.text("item").hoverEvent(item.asHoverEvent()))
                                .append(Component.text('.'))
                                .colorIfAbsent(NamedTextColor.AQUA) message player
                        }
                    }

                    return@handler
                }

                val lore = ctx.getOrDefault<String>("lore", null)

                when {
                    lore == null -> player.msg("You must provide lore to add.")
                    line.isEmpty -> item.lore(existingLore + MiniMessage.miniMessage().deserialize(lore))
                    existingLore.size < line.get() -> player.msg("Selected item doesn't have lore line ${line.get()}.")
                    else -> {
                        val replacedLine = existingLore[line.get()]
                        existingLore[line.get()] = MiniMessage.miniMessage().deserialize(lore)
                        item.lore(existingLore)
                        Component.text("Replaced line [")
                            .append(replacedLine)
                            .append(Component.text("] with ["))
                            .append(MiniMessage.miniMessage().deserialize(lore))
                            .append(Component.text("] in "))
                            .append(Component.text("item").hoverEvent(item.asHoverEvent()))
                            .append(Component.text('.'))
                            .colorIfAbsent(NamedTextColor.AQUA) message player
                    }
                }
            }
        }
    }

    @Throws(InvalidCommandSenderException::class)
    private fun getTargetOrThrow(context: CommandContext<CommandSender>) = context.flags().getValue<Player>("player").orElseGet {
        context.sender as? Player ?: throw InvalidCommandSenderException(context.sender, Player::class.java, emptyList())
    }
}
