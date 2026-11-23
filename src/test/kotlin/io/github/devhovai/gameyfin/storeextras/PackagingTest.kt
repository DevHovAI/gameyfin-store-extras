package io.github.devhovai.gameyfin.storeextras

import org.gameyfin.pluginapi.gamemetadata.GameMetadataProvider
import org.pf4j.Extension
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Without META-INF/extensions.idx in the JAR Gameyfin lists the plugin as running but never calls it (1.0.1 shipped like that). */
class PackagingTest {
    @Test fun `extension index names the metadata provider`() {
        val index = assertNotNull(javaClass.getResource("/META-INF/extensions.idx")).readText()
        val classes = index.lines().filter { it.isNotBlank() && !it.startsWith("#") }
        assertEquals(listOf(StoreExtrasPlugin.StoreExtrasMetadataProvider::class.java.name), classes)
        val type = Class.forName(classes.single())
        assertTrue(GameMetadataProvider::class.java.isAssignableFrom(type))
        assertNotNull(type.getAnnotation(Extension::class.java))
    }
}
