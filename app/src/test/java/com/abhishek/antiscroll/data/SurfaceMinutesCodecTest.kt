package com.abhishek.antiscroll.data

import com.abhishek.antiscroll.rules.profile.Surface
import org.junit.Assert.assertEquals
import org.junit.Test

class SurfaceMinutesCodecTest {

    @Test
    fun `round trip`() {
        val minutes = mapOf(Surface.REELS to 30, Surface.FEED to 20, Surface.EXPLORE to 0)
        assertEquals(minutes, SurfaceMinutesCodec.decode(SurfaceMinutesCodec.encode(minutes)))
    }

    @Test
    fun `encoded in a stable order`() =
        assertEquals("REELS=30,FEED=20", SurfaceMinutesCodec.encode(mapOf(Surface.FEED to 20, Surface.REELS to 30)))

    @Test
    fun `no limits at all`() {
        assertEquals("", SurfaceMinutesCodec.encode(emptyMap()))
        assertEquals(emptyMap<Surface, Int>(), SurfaceMinutesCodec.decode(""))
    }

    @Test
    fun `unknown surfaces and broken entries are dropped, the rest kept`() =
        assertEquals(mapOf(Surface.REELS to 30), SurfaceMinutesCodec.decode("REELS=30,SHORTS=10,FEED=abc,junk"))

    @Test
    fun `spaces are tolerated, as typed in an adb command`() =
        assertEquals(mapOf(Surface.REELS to 46, Surface.FEED to 20), SurfaceMinutesCodec.decode("REELS = 46, FEED=20"))
}
