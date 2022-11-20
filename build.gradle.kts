import net.minecrell.pluginyml.bukkit.BukkitPluginDescription.Permission.Default
import net.minecrell.pluginyml.bukkit.BukkitPluginDescription.PluginLoadOrder

plugins {
    alias(libs.plugins.minix.kotlin)
    alias(libs.plugins.minix.copyjar)
    alias(libs.plugins.minix.purpurmc)
    alias(libs.plugins.minecraft.pluginYML)
}

bukkit {
    this.name = rootProject.name
    this.author = "Racci"
    this.version = project.version.toString()
    this.apiVersion = "1.19"
    this.depend = listOf("Minix")
    this.main = "dev.racci.simplix.Simplix"
    this.load = PluginLoadOrder.STARTUP

    this.permissions {
        this.register("simplix.rename") {
            this.description = "Allows the user to rename items"
            this.default = Default.OP
        }
        this.register("simplix.lore") {
            this.description = "Allows the user to edit the lore of items"
            this.default = Default.OP
        }
    }
}

tasks {
    val quickBuild by creating {
        this.group = "build"
        dependsOn(compileKotlin)
        dependsOn(shadowJar)
        findByName("copyJar")?.let { dependsOn(it) }
    }
}

dependencies {
    compileOnly(libs.minecraft.minix)
}
