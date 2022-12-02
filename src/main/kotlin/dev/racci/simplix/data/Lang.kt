package dev.racci.simplix.data

import dev.racci.minix.api.annotations.MappedConfig
import dev.racci.minix.api.data.LangConfig
import dev.racci.minix.api.utils.adventure.PartialComponent
import dev.racci.simplix.Simplix
import org.spongepowered.configurate.objectmapping.ConfigSerializable

@ConfigSerializable
@MappedConfig(Simplix::class, "Lang.conf")
public class Lang : LangConfig<Simplix>() {

    override val prefixes: Map<String, String> = mapOf(
        "server" to "<gradient:#ED13D9:#12d3ff>Elixir</gradient> <white>»</white> <aqua>",
        "terix" to "<gradient:#ED13D9:#12d3ff>Terix</gradient> <white>»</white> <aqua>",
        "origins" to "<gold>Origins</gold> » <aqua>"
    )

    public var generic: Generic = Generic()

    @ConfigSerializable
    public class Generic : InnerLang() {

        public var error: PartialComponent = PartialComponent.of("<dark_red>Error <white>» <red><message>")

        public var reloadLang: PartialComponent = PartialComponent.of("<prefix:terix>Reloaded plugin in <time>ms. (Does nothing currently)")
    }
}
