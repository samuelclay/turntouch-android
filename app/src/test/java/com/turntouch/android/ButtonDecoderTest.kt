package com.turntouch.android
import org.junit.Assert.*
import org.junit.Test

class ButtonDecoderTest {
    private val events=mutableListOf<Pair<Direction,Gesture>>()
    private val decoder=ButtonDecoder({d,g->events.add(d to g)})
    @Test fun everyPhysicalBitMapsToItsDirection() { for(i in 0..3) {decoder.read(byteArrayOf((255 xor (1 shl i)).toByte(),0),i*1000L);decoder.read(byteArrayOf(-1,0),i*1000L+50);decoder.flush(i*1000L+350)};assertEquals(Direction.entries.map {it to Gesture.Single},events) }
    @Test fun hardwareDoubleReplacesPendingSingle() {decoder.read(byteArrayOf(-2,0),0);decoder.read(byteArrayOf(-1,0),30);decoder.read(byteArrayOf(-2,0),90);decoder.read(byteArrayOf(0xef.toByte(),0),120);decoder.flush(1000);assertEquals(listOf(Direction.North to Gesture.Double),events)}
    @Test fun softwareDoubleAndIndependentButtons() {decoder.read(byteArrayOf(-3,0),0);decoder.read(byteArrayOf(-1,0),20);decoder.read(byteArrayOf(-3,0),80);decoder.read(byteArrayOf(-1,0),100);decoder.flush(500);assertEquals(listOf(Direction.East to Gesture.Double),events)}
    @Test fun holdNeverFiresOnReleaseOrRepeats() {decoder.read(byteArrayOf(-9,0),0);decoder.read(byteArrayOf(-9,-1),800);decoder.read(byteArrayOf(-9,-1),900);decoder.read(byteArrayOf(-1,0),1000);decoder.flush(2000);assertEquals(listOf(Direction.South to Gesture.Hold),events)}
    @Test fun chordSuppressesAllActionsUntilAllReleased() {decoder.read(byteArrayOf(-2,0),0);decoder.read(byteArrayOf(-4,0),20);decoder.read(byteArrayOf(-3,0),100);decoder.read(byteArrayOf(-1,0),150);decoder.flush(1000);assertTrue(events.isEmpty())}
    @Test fun malformedPacketsAndDisconnectDoNotFire() {decoder.read(byteArrayOf(),0);decoder.read(byteArrayOf(-2),0);decoder.read(byteArrayOf(-2,0),10);decoder.reset();decoder.read(byteArrayOf(-1,0),50);decoder.flush(500);assertTrue(events.isEmpty())}
    @Test fun fastDifferentButtonsBothRun() {decoder.read(byteArrayOf(-2,0),0);decoder.read(byteArrayOf(-1,0),20);decoder.read(byteArrayOf(-5,0),40);decoder.read(byteArrayOf(-1,0),60);decoder.flush(500);assertEquals(listOf(Direction.North to Gesture.Single,Direction.West to Gesture.Single),events)}
}
