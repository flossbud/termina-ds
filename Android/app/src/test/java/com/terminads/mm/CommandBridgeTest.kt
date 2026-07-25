package com.terminads.mm

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandBridgeTest {

    private class Recorder(var result: Int = 0) {
        var lastOp = -1; var lastA = -1; var lastB = -1; var lastName: String? = "unset"
        fun submit(op: Int, a: Int, b: Int, name: String?): Int {
            lastOp = op; lastA = a; lastB = b; lastName = name
            return result
        }
    }

    @Test
    fun pauseSetSubmitsAbsoluteValues() {
        assertEquals(1, CommandBridge.OP_PAUSE_SET)
        val rec = Recorder()
        val bridge = CommandBridge(rec::submit)
        assertEquals(SubmitStatus.OK, bridge.setPaused(true))
        assertEquals(CommandBridge.OP_PAUSE_SET, rec.lastOp)
        assertEquals(1, rec.lastA)
        assertEquals(0, rec.lastB)
        assertEquals(null, rec.lastName)
        bridge.setPaused(false)
        assertEquals(0, rec.lastA)
    }

    @Test
    fun cvarSetCarriesNameAndValue() {
        assertEquals(2, CommandBridge.OP_CVAR_SET_INT)
        val rec = Recorder()
        CommandBridge(rec::submit).setCVarInt("gInterpolationFPS", 60)
        assertEquals(CommandBridge.OP_CVAR_SET_INT, rec.lastOp)
        assertEquals(60, rec.lastA)
        assertEquals(0, rec.lastB)
        assertEquals("gInterpolationFPS", rec.lastName)
    }

    @Test
    fun statusesDecodeAndUnknownIsPermanent() {
        assertEquals(3, CommandBridge.OP_CVAR_SAVE)
        val rec = Recorder(result = 1)
        assertEquals(SubmitStatus.FULL, CommandBridge(rec::submit).saveCVars())
        assertEquals(CommandBridge.OP_CVAR_SAVE, rec.lastOp)
        assertEquals(0, rec.lastA)
        assertEquals(0, rec.lastB)
        assertEquals(null, rec.lastName)
        rec.result = 2
        assertEquals(SubmitStatus.INVALID, CommandBridge(rec::submit).saveCVars())
        rec.result = 99
        assertEquals(SubmitStatus.UNKNOWN, CommandBridge(rec::submit).saveCVars())
    }
}
