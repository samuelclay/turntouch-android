package com.turntouch.android

/** Pure decoder for nRF51 active-low packets. Each remote owns its own decoder. */
class ButtonDecoder(private val emit: (Direction, Gesture) -> Unit, private val highlight: (Direction?) -> Unit = {}) {
    private var previous=0
    private var held=0
    private var chord=false
    private var pending: Pair<Int,Long>?=null
    fun reset() { previous=0; held=0; chord=false; pending=null; highlight(null) }
    fun flush(now: Long) { pending?.let { (button,time) -> if(now-time>=290) { pending=null; emit(Direction.entries[button],Gesture.Single) } } }
    fun read(data: ByteArray, now: Long) {
        if(data.size<2) return
        flush(now)
        val bits=data[0].toInt().inv() and 15
        val doubleBits=(data[0].toInt().inv() ushr 4) and 15
        if(Integer.bitCount(bits)>1) { chord=true; pending=null }
        if(chord) { previous=bits; highlight(null); if(bits==0) { chord=false; held=0 }; return }
        val isHeld=(data[1].toInt() and 255)==255
        highlight(if(Integer.bitCount(bits)==1)Direction.entries[Integer.numberOfTrailingZeros(bits)] else null)
        if(isHeld && Integer.bitCount(bits)==1 && held and bits==0) { pending=null; held=held or bits; emit(Direction.entries[Integer.numberOfTrailingZeros(bits)],Gesture.Hold) }
        val lifted=previous and bits.inv() and 15
        for(i in 0..3) if(lifted and (1 shl i)!=0 && held and (1 shl i)==0) {
            val p=pending
            if((doubleBits!=0 && doubleBits!=15) || (p?.first==i && now-p.second<290)) { pending=null; emit(Direction.entries[i],Gesture.Double) }
            else { if(p!=null) { pending=null; emit(Direction.entries[p.first],Gesture.Single) }; pending=i to now }
        }
        held=held and bits
        previous=bits
    }
}
