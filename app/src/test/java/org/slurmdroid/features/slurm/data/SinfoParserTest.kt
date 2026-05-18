package org.slurmdroid.features.slurm.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.slurmdroid.core.Result

class SinfoParserTest {

    private val parser = SinfoParser()

    // ── happy path ─────────────────────────────────────────────────────────────

    @Test
    fun `single partition single state`() {
        val output = "gpu|up|4|idle|0/32/0/32"
        val result = parser.parse(output)
        assertTrue(result is Result.Success)
        val partitions = (result as Result.Success).data
        assertEquals(1, partitions.size)
        with(partitions[0]) {
            assertEquals("gpu", name)
            assertFalse(isDefault)
            assertEquals("up", state)
            assertEquals(4, nodesTotal)
            assertEquals(4, nodesAvailable)
            assertEquals(0, cpusAllocated)
            assertEquals(32, cpusIdle)
            assertEquals(32, cpusTotal)
        }
    }

    @Test
    fun `default partition marked with asterisk`() {
        val output = "debug*|up|2|idle|0/4/0/4"
        val partitions = (parser.parse(output) as Result.Success).data
        assertTrue(partitions[0].isDefault)
        assertEquals("debug", partitions[0].name)
    }

    @Test
    fun `partition aggregates multiple node-state rows`() {
        // 2 idle + 1 allocated nodes; CPUs are summed across rows
        val output = """
            debug*|up|2|idle|0/4/0/4
            debug*|up|1|allocated|2/0/0/2
        """.trimIndent()
        val partitions = (parser.parse(output) as Result.Success).data
        assertEquals(1, partitions.size)
        with(partitions[0]) {
            assertEquals(3, nodesTotal)
            assertEquals(2, nodesAvailable)    // only idle nodes count
            assertEquals(2, cpusAllocated)
            assertEquals(4, cpusIdle)
            assertEquals(6, cpusTotal)         // 4 + 2
        }
    }

    @Test
    fun `mix nodes count as available`() {
        val output = "gpu|up|3|mix|4/4/0/8"
        val partitions = (parser.parse(output) as Result.Success).data
        assertEquals(3, partitions[0].nodesAvailable)
    }

    @Test
    fun `down nodes do not count as available`() {
        val output = """
            gpu|up|2|idle|0/16/0/16
            gpu|up|1|down|0/0/0/8
        """.trimIndent()
        val partitions = (parser.parse(output) as Result.Success).data
        assertEquals(3, partitions[0].nodesTotal)
        assertEquals(2, partitions[0].nodesAvailable)
    }

    @Test
    fun `multiple distinct partitions`() {
        val output = """
            debug*|up|2|idle|0/4/0/4
            gpu|up|4|idle|0/32/0/32
            gpu|up|1|allocated|8/0/0/8
        """.trimIndent()
        val partitions = (parser.parse(output) as Result.Success).data
        assertEquals(2, partitions.size)
        val gpu = partitions.first { it.name == "gpu" }
        assertEquals(5, gpu.nodesTotal)
        assertEquals(40, gpu.cpusTotal)
    }

    @Test
    fun `drained partition state propagates`() {
        val output = "compute|drain|8|drain|0/0/0/128"
        val partitions = (parser.parse(output) as Result.Success).data
        assertEquals("drain", partitions[0].state)
        assertFalse(partitions[0].isUp)
        assertEquals(0, partitions[0].nodesAvailable)
    }

    @Test
    fun `cpuUsageFraction calculated correctly`() {
        val output = "gpu|up|1|mix|6/2/0/8"
        val p = (parser.parse(output) as Result.Success).data[0]
        assertEquals(6f / 8f, p.cpuUsageFraction, 0.001f)
    }

    // ── edge cases ─────────────────────────────────────────────────────────────

    @Test
    fun `empty output returns empty list`() {
        val result = parser.parse("")
        assertEquals(Result.Success(emptyList<Any>()), result)
    }

    @Test
    fun `blank lines are ignored`() {
        val output = "\n  \ngpu|up|2|idle|0/16/0/16\n\n"
        val partitions = (parser.parse(output) as Result.Success).data
        assertEquals(1, partitions.size)
    }

    @Test
    fun `malformed line is skipped`() {
        val output = """
            gpu|up|4|idle|0/32/0/32
            this is garbage
            debug*|up|2|idle|0/4/0/4
        """.trimIndent()
        val partitions = (parser.parse(output) as Result.Success).data
        assertEquals(2, partitions.size)
    }
}
