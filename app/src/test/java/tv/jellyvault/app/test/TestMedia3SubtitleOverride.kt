package tv.jellyvault.app.test

import org.junit.Assert
import org.junit.Test
import tv.jellyvault.app.util.Media3SubtitleOverride

class TestMedia3SubtitleOverride {
    @Test
    fun test() {
        // This tests whether the class and field names exist
        Media3SubtitleOverride(2f)
        Assert.assertTrue(Media3SubtitleOverride.Companion.initialized)
    }
}
