package org.slurmdroid.features.slurm.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.slurmdroid.core.Result
import org.slurmdroid.features.slurm.data.SqueueParser.Companion.parseTimeToSeconds

class SqueueParserTest {

    private val parser = SqueueParser()

    // ── time parser ────────────────────────────────────────────────────────────

    @Test
    fun `parse MM_SS`() = assertEquals(83L, parseTimeToSeconds("1:23"))

    @Test
    fun `parse H_MM_SS`() = assertEquals(5025L, parseTimeToSeconds("1:23:45"))

    @Test
    fun `parse HH_MM_SS`() = assertEquals(86399L, parseTimeToSeconds("23:59:59"))

    @Test
    fun `parse D-HH_MM_SS`() {
        // 2 days + 3h + 15m + 30s
        assertEquals(2 * 86_400L + 3 * 3600L + 15 * 60L + 30L, parseTimeToSeconds("2-03:15:30"))
    }

    @Test
    fun `parse zero elapsed`() = assertEquals(0L, parseTimeToSeconds("0:00"))

    @Test
    fun `UNLIMITED returns null`() = assertNull(parseTimeToSeconds("UNLIMITED"))

    @Test
    fun `NOT_SET returns null`() = assertNull(parseTimeToSeconds("NOT_SET"))

    @Test
    fun `blank returns null`() = assertNull(parseTimeToSeconds(""))

    @Test
    fun `N_A returns null`() = assertNull(parseTimeToSeconds("N/A"))

    // ── job parsing ────────────────────────────────────────────────────────────

    @Test
    fun `running job parsed correctly`() {
        val output = "12345|my_training|RUNNING|1:23:45|24:00:00|(null)|gpu"
        val jobs = (parser.parse(output) as Result.Success).data
        assertEquals(1, jobs.size)
        with(jobs[0]) {
            assertEquals("12345", jobId)
            assertEquals("my_training", name)
            assertEquals("RUNNING", state)
            assertTrue(isRunning)
            assertFalse(isPending)
            assertEquals("1:23:45", timeUsed)
            assertEquals("24:00:00", timeLimit)
            assertEquals("", reason)             // (null) → ""
            assertEquals("gpu", partition)
            assertEquals(5025L, timeUsedSeconds)
            assertEquals(86_400L, timeLimitSeconds)
        }
    }

    @Test
    fun `pending job has reason`() {
        val output = "12346|preprocess|PENDING|0:00|4:00:00|Resources|debug"
        val job = (parser.parse(output) as Result.Success).data[0]
        assertTrue(job.isPending)
        assertEquals("Resources", job.reason)
        assertEquals(0L, job.timeUsedSeconds)
        assertEquals(14_400L, job.timeLimitSeconds)
    }

    @Test
    fun `multi-day job`() {
        val output = "12347|long_run|RUNNING|2-03:15:30|7-00:00:00|(null)|gpu"
        val job = (parser.parse(output) as Result.Success).data[0]
        assertEquals(2 * 86_400L + 3 * 3600L + 15 * 60L + 30L, job.timeUsedSeconds)
        assertEquals(7 * 86_400L, job.timeLimitSeconds)
    }

    @Test
    fun `unlimited time limit`() {
        val output = "99999|infinite|RUNNING|1:00:00|UNLIMITED|(null)|debug"
        val job = (parser.parse(output) as Result.Success).data[0]
        assertNull(job.timeLimitSeconds)
        assertNull(job.progressFraction)
    }

    @Test
    fun `progress fraction for running job`() {
        // 12h elapsed of 24h limit → 0.5
        val output = "1|job|RUNNING|12:00:00|24:00:00|(null)|gpu"
        val job = (parser.parse(output) as Result.Success).data[0]
        assertEquals(0.5f, job.progressFraction!!, 0.001f)
    }

    @Test
    fun `progress fraction capped at 1`() {
        // Elapsed > limit (clock skew / overtime)
        val output = "1|job|RUNNING|25:00:00|24:00:00|(null)|gpu"
        val job = (parser.parse(output) as Result.Success).data[0]
        assertEquals(1.0f, job.progressFraction!!, 0.001f)
    }

    @Test
    fun `multiple jobs`() {
        val output = """
            111|job_a|RUNNING|1:00:00|8:00:00|(null)|gpu
            222|job_b|PENDING|0:00|4:00:00|Priority|debug
            333|job_c|RUNNING|0:30:00|2:00:00|(null)|gpu
        """.trimIndent()
        val jobs = (parser.parse(output) as Result.Success).data
        assertEquals(3, jobs.size)
        assertEquals("111", jobs[0].jobId)
        assertEquals("222", jobs[1].jobId)
        assertEquals("333", jobs[2].jobId)
    }

    // ── edge cases ─────────────────────────────────────────────────────────────

    @Test
    fun `empty output returns empty list`() {
        val result = parser.parse("")
        assertEquals(Result.Success(emptyList<Any>()), result)
    }

    @Test
    fun `blank lines are skipped`() {
        val output = "\n  \n12345|job|RUNNING|1:00:00|8:00:00|(null)|gpu\n\n"
        assertEquals(1, (parser.parse(output) as Result.Success).data.size)
    }

    @Test
    fun `malformed line is skipped`() {
        val output = """
            12345|job|RUNNING|1:00:00|8:00:00|(null)|gpu
            not enough columns here
        """.trimIndent()
        assertEquals(1, (parser.parse(output) as Result.Success).data.size)
    }
}
