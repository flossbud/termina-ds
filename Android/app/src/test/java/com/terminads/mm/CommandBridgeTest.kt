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

    @Test
    fun opcodeValuesArePinnedToTheNativeHeader() {
        // Mirrors enum TdsCommandOp in mm/2s2h/TerminaDS/CommandMailbox.h.
        // Pinned to literals, not to each other: asserting OP_SET_MSAA ==
        // OP_SET_MSAA proves nothing, which was a Plan A review finding.
        assertEquals(1, CommandBridge.OP_PAUSE_SET)
        assertEquals(2, CommandBridge.OP_CVAR_SET_INT)
        assertEquals(3, CommandBridge.OP_CVAR_SAVE)
        assertEquals(4, CommandBridge.OP_SET_INTERNAL_RES)
        assertEquals(5, CommandBridge.OP_SET_MSAA)
        assertEquals(6, CommandBridge.OP_SET_TEXTURE_FILTER)
        assertEquals(7, CommandBridge.OP_SET_DISPLAY_HZ)
    }

    @Test
    fun semanticSettersSubmitTheirOpcodeAndValue() {
        val calls = mutableListOf<List<Any?>>()
        val bridge = CommandBridge { op, a, b, name ->
            calls += listOf(op, a, b, name)
            0
        }

        assertEquals(SubmitStatus.OK, bridge.setInternalResPercent(150))
        assertEquals(SubmitStatus.OK, bridge.setMsaa(4))
        assertEquals(SubmitStatus.OK, bridge.setTextureFilter(2))

        assertEquals(listOf(4, 150, 0, null), calls[0])
        assertEquals(listOf(5, 4, 0, null), calls[1])
        assertEquals(listOf(6, 2, 0, null), calls[2])
    }

    @Test
    fun semanticSettersSurfaceANonOkStatus() {
        val bridge = CommandBridge { _, _, _, _ -> 1 }
        assertEquals(SubmitStatus.FULL, bridge.setMsaa(4))
    }

    @Test
    fun displayRefreshSubmitsTheAbsoluteHz() {
        val rec = Recorder()

        assertEquals(SubmitStatus.OK, CommandBridge(rec::submit).setDisplayRefreshHz(120))
        assertEquals(CommandBridge.OP_SET_DISPLAY_HZ, rec.lastOp)
        assertEquals(120, rec.lastA)
        assertEquals(0, rec.lastB)
        assertEquals(null, rec.lastName)
    }
}
