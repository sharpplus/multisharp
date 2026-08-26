package org.multisharp.bench

import decomp.SquareDecomp
import experiments.CircuitTests
import experiments.CircuitWorkloads
import experiments.CombinedTables
import experiments.MsmComparison
import experiments.MultiSharpTests
import experiments.TimingProbe

object Bench {

    class Harness(
        val label: String,
        val defaultArgs: String,
        val note: String,
        val entryPoint: (Array<String>) -> Unit,
    )

    val harnesses = listOf(
        Harness(
            "CircuitWorkloads",
            "measure 256 10",
            "Table: circuit workloads. Minutes. Start here \u2014 these are the rows " +
                "the use-case latency claims rest on.",
        ) { CircuitWorkloads.main(it) },
        Harness(
            "CombinedTables",
            "10 1024 256 128,256,512,1024 11",
            "Comparison + scalability tables. ~17 min on the desktop, so expect " +
                "a few hours here. Run it plugged in.",
        ) { CombinedTables.main(it) },
        Harness(
            "MsmComparison",
            "10 1024 128,256,512,1024",
            "Bucket method against naive. ~27 min on the desktop.",
        ) { MsmComparison.main(it) },
        Harness(
            "TimingProbe",
            "solo 256 256 10 3",
            "Run this FIRST. It reports three consecutive timed blocks; if they " +
                "do not agree, retune the bench.* settings below before trusting " +
                "any measurement.",
        ) { TimingProbe.main(it) },
        Harness(
            "MultiSharpTests",
            "",
            "Correctness. Seconds. Confirms the protocol behaves on this device " +
                "before anything is timed.",
        ) { MultiSharpTests.main(it) },
        Harness(
            "CircuitTests",
            "",
            "Gadget and end-to-end circuit correctness. Seconds.",
        ) { CircuitTests.main(it) },
    )

    var burnInMs = 15_000L
    var minBlockMs = 5_000L
    var discardBlocks = 1
    var passes = 3

    fun applyTimingPolicy() {
        System.setProperty("bench.burnInMs", burnInMs.toString())
        System.setProperty("bench.minBlockMs", minBlockMs.toString())
        System.setProperty("bench.discardBlocks", discardBlocks.toString())
        System.setProperty("bench.passes", passes.toString())
    }

    fun installPrecompSource(assets: android.content.res.AssetManager) {
        SquareDecomp.setPrecompSource { name -> assets.open("precomp/$name") }
        applyTimingPolicy()
    }

    fun describeEnvironment(): String = buildString {
        appendLine("device:      ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        appendLine("soc:         ${android.os.Build.SOC_MANUFACTURER} ${android.os.Build.SOC_MODEL}")
        appendLine("android:     ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        appendLine("abi:         ${android.os.Build.SUPPORTED_ABIS.joinToString()}")
        val rt = Runtime.getRuntime()
        appendLine("max heap:    ${rt.maxMemory() / (1024 * 1024)} MB")
        appendLine("cores:       ${rt.availableProcessors()}")
        appendLine("policy:      burnInMs=$burnInMs minBlockMs=$minBlockMs " +
            "discardBlocks=$discardBlocks passes=$passes")
    }

    fun run(harness: Harness, argLine: String) {
        applyTimingPolicy()
        val args = argLine.trim()
            .split(' ')
            .filter { it.isNotEmpty() }
            .toTypedArray()
        harness.entryPoint(args)
    }
}
