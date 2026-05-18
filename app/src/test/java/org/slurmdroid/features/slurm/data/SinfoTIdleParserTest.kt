package org.slurmdroid.features.slurm.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.slurmdroid.core.Result

class SinfoTIdleParserTest {

    private val parser = SinfoTIdleParser()

    // Real output from KIT/SCC cluster (condensed)
    private val realOutput = """
        Partition dev_cpu                 :      2 nodes idle
        Partition cpu                     :     17 nodes idle
        Partition highmem                 :      0 nodes idle
        Partition dev_gpu_h100            :      0 nodes idle
        Partition gpu_h100_short          :      4 nodes idle
        Partition gpu_h100                :      0 nodes idle
        Partition gpu_mi300               :      0 nodes idle
        Partition dev_cpu_il              :      8 nodes idle
        Partition cpu_il                  :      0 nodes idle
        Partition dev_gpu_a100_il         :      1 nodes idle
        Partition gpu_a100_il             :      0 nodes idle
        Partition gpu_h100_il             :      0 nodes idle
        Partition gpu_a100_short          :     18 nodes idle
    """.trimIndent()

    @Test
    fun `parses all partitions from real cluster output`() {
        val result = parser.parse(realOutput)
        assertTrue(result is Result.Success)
        val partitions = (result as Result.Success).data
        assertEquals(13, partitions.size)
    }

    @Test
    fun `partition names are correct`() {
        val partitions = (parser.parse(realOutput) as Result.Success).data
        val names = partitions.map { it.name }.toSet()
        assertTrue("gpu_a100_il" in names)
        assertTrue("gpu_h100_short" in names)
        assertTrue("dev_cpu" in names)
    }

    @Test
    fun `idle node counts are parsed correctly`() {
        val partitions = (parser.parse(realOutput) as Result.Success).data.associateBy { it.name }
        assertEquals(2, partitions["dev_cpu"]!!.nodesAvailable)
        assertEquals(17, partitions["cpu"]!!.nodesAvailable)
        assertEquals(18, partitions["gpu_a100_short"]!!.nodesAvailable)
        assertEquals(0, partitions["gpu_a100_il"]!!.nodesAvailable)
    }

    @Test
    fun `nodesTotal is -1 (unknown) for all partitions`() {
        val partitions = (parser.parse(realOutput) as Result.Success).data
        assertTrue(partitions.all { it.nodesTotal == -1 })
        assertTrue(partitions.all { !it.isTotalKnown })
    }

    @Test
    fun `state is assumed up`() {
        val partitions = (parser.parse(realOutput) as Result.Success).data
        assertTrue(partitions.all { it.isUp })
    }

    @Test
    fun `isDefault is false (not available from this source)`() {
        val partitions = (parser.parse(realOutput) as Result.Success).data
        assertFalse(partitions.any { it.isDefault })
    }

    @Test
    fun `cpu fields are zero`() {
        val p = (parser.parse(realOutput) as Result.Success).data.first()
        assertEquals(0, p.cpusAllocated)
        assertEquals(0, p.cpusIdle)
        assertEquals(0, p.cpusTotal)
    }

    @Test
    fun `empty output returns empty list`() {
        val result = parser.parse("")
        assertEquals(Result.Success(emptyList<Any>()), result)
    }

    @Test
    fun `malformed lines are skipped`() {
        val output = """
            Partition gpu_a100_short          :     18 nodes idle
            this is garbage
            something else entirely
        """.trimIndent()
        val partitions = (parser.parse(output) as Result.Success).data
        assertEquals(1, partitions.size)
    }
}
