package com.hikemvp.planning

import kotlin.math.max
import kotlin.math.min

/**
 * Smoothing utils — conserves existing API (List<Double?>) and adds fast DoubleArray overloads.
 * No breaking changes.
 */
object Smoothing {

    /** Median filter with odd window; nulls are skipped and produce null if insufficient neighbors. */
    fun medianFilter(values: List<Double?>, window: Int = 5): List<Double?> {
        val w = if (window % 2 == 1) window else window + 1
        val r = w / 2
        val out = ArrayList<Double?>(values.size)
        for (i in values.indices) {
            val slice = ArrayList<Double>()
            val from = max(0, i - r)
            val to = min(values.lastIndex, i + r)
            for (j in from..to) {
                val v = values[j]; if (v != null) slice.add(v)
            }
            if (slice.size < r + 1) { out.add(null); continue }
            slice.sort()
            out.add(slice[slice.size / 2])
        }
        return out
    }

    /**
     * Savitzky–Golay smoothing, fixed coefficients for window 7, order 2:
     * coeffs = [-2, 3, 6, 7, 6, 3, -2] / 21
     * Nulls are ignored; if the effective weight is 0 the output is null.
     */
    fun savitzkyGolay(values: List<Double?>, window: Int = 7): List<Double?> {
        val coeff = doubleArrayOf(-2.0, 3.0, 6.0, 7.0, 6.0, 3.0, -2.0).map { it / 21.0 }
        val r = coeff.size / 2
        val out = ArrayList<Double?>(values.size)
        for (i in values.indices) {
            var acc = 0.0
            var weight = 0.0
            for (k in -r..r) {
                val idx = i + k
                if (idx < 0 || idx > values.lastIndex) continue
                val v = values[idx] ?: continue
                val c = coeff[k + r]
                acc += v * c
                weight += c
            }
            out.add(if (weight == 0.0) null else acc) // coeffs sum to 1 for full window
        }
        return out
    }

    // ----------------- New overloads (non-breaking) -----------------

    /** Median filter overload for DoubleArray (faster, no boxing). */
    fun medianFilter(values: DoubleArray, window: Int = 5): DoubleArray {
        val n = values.size
        if (n == 0) return DoubleArray(0)
        val w = if (window % 2 == 1) window else window + 1
        val r = w / 2
        val out = DoubleArray(n)
        val buf = DoubleArray(w)
        for (i in 0 until n) {
            var idx = 0
            val from = max(0, i - r)
            val to = min(n - 1, i + r)
            for (j in from..to) buf[idx++] = values[j]
            java.util.Arrays.sort(buf, 0, idx)
            out[i] = buf[idx / 2]
        }
        return out
    }

    /** Savitzky–Golay overload for DoubleArray (window 7, order 2). */
    fun savitzkyGolay(values: DoubleArray, window: Int = 7): DoubleArray {
        if (window != 7 || values.size < 7) return values.copyOf()
        val out = DoubleArray(values.size)
        // copy borders
        for (i in 0..2) out[i] = values[i]
        for (i in values.size - 3 until values.size) out[i] = values[i]
        // core
        for (i in 3 until values.size - 3) {
            val v = (-2 * values[i - 3] + 3 * values[i - 2] + 6 * values[i - 1] +
                    7 * values[i] + 6 * values[i + 1] + 3 * values[i + 2] - 2 * values[i + 3]) / 21.0
            out[i] = v
        }
        return out
    }

    /** Convenience: median then Savitzky–Golay in one step on List<Double?>. */
    fun medianThenSavitzky(values: List<Double?>, medianWindow: Int = 5, sgWindow: Int = 7): List<Double?> {
        val med = medianFilter(values, medianWindow)
        return savitzkyGolay(med, sgWindow)
    }
}
