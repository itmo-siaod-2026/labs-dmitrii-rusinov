#!/usr/bin/env python3
"""
Plot JMH benchmark results from JSON output.

Usage:
    python scripts/plot_results.py [path/to/results.json]

Default path: app/build/reports/jmh/results.json
Output:       benchmark-results/  (PNG files)
"""

import json
import os
import sys

import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
import numpy as np

RESULTS_FILE = os.path.join("app", "build", "reports", "jmh", "results.json")
OUTPUT_DIR = "benchmark-results"

COLORS = {
    "Custom":            "#2196F3",
    "HashMap":           "#FF9800",
    "ConcurrentHashMap": "#4CAF50",
}
MAP_TYPES = list(COLORS.keys())

SCENARIOS = {
    "Single-threaded Put":   "singlePut",
    "Single-threaded Get":   "singleGet",
    "Concurrent Put (8t)":   "concPut",
    "Concurrent Get (8t)":   "concGet",
    "Concurrent Mixed (8t)": "concMixed",
}


def load(path):
    with open(path) as f:
        return json.load(f)


SCALING_OPS = {
    "scalingGet":   "Get",
    "scalingPut":   "Put",
    "scalingMerge": "Merge",
}

OP_COLORS = {
    "Get":   "#2196F3",
    "Put":   "#FF9800",
    "Merge": "#4CAF50",
}


def parse(results):
    data = {}
    scaling = {}
    for entry in results:
        short = entry["benchmark"].rsplit(".", 1)[-1]
        score = entry["primaryMetric"]["score"]
        confidence = entry["primaryMetric"].get("scoreConfidence", [score, score])
        record = {
            "score":  score,
            "ci_lo":  score - confidence[0],
            "ci_hi":  confidence[1] - score,
        }
        params = entry.get("params")
        if params and "tableSize" in params:
            op_label = SCALING_OPS.get(short)
            if op_label:
                size = int(params["tableSize"])
                scaling.setdefault(op_label, {})[size] = record
        else:
            try:
                scenario, map_type = short.rsplit("_", 1)
                data[(scenario, map_type)] = record
            except ValueError:
                pass
    return data, scaling


def grouped_bar(ax, title, scenario_key, data):
    x = np.arange(len(MAP_TYPES))
    width = 0.55 / len(MAP_TYPES)

    for idx, map_type in enumerate(MAP_TYPES):
        key = (scenario_key, map_type)
        entry = data.get(key)
        if entry is None:
            continue
        offset = (idx - len(MAP_TYPES) / 2 + 0.5) * width * 1.1
        yerr = np.array([[entry["ci_lo"]], [entry["ci_hi"]]])
        ax.bar(
            x + offset,
            [entry["score"]],
            width,
            label=map_type,
            color=COLORS[map_type],
            yerr=yerr,
            capsize=5,
            error_kw={"elinewidth": 1.5, "ecolor": "black"},
            zorder=3,
        )

    ax.set_title(title, fontsize=11, fontweight="bold")
    ax.set_ylabel("avg time (µs/op)", fontsize=9)
    ax.set_xticks([])
    ax.grid(axis="y", linestyle="--", alpha=0.4, zorder=0)
    ax.set_axisbelow(True)

    patches = [mpatches.Patch(color=COLORS[m], label=m) for m in MAP_TYPES]
    ax.legend(handles=patches, fontsize=8)


def overview_bar(ax, data):
    label_names = list(SCENARIOS.keys())
    scenario_keys = list(SCENARIOS.values())
    n_scenarios = len(scenario_keys)
    n_maps = len(MAP_TYPES)

    group_positions = np.arange(n_scenarios)
    total_width = 0.8
    bar_width = total_width / n_maps

    for mi, map_type in enumerate(MAP_TYPES):
        scores = []
        ci_lo = []
        ci_hi = []
        for sk in scenario_keys:
            entry = data.get((sk, map_type))
            if entry:
                scores.append(entry["score"])
                ci_lo.append(entry["ci_lo"])
                ci_hi.append(entry["ci_hi"])
            else:
                scores.append(0)
                ci_lo.append(0)
                ci_hi.append(0)

        offset = (mi - n_maps / 2 + 0.5) * bar_width
        ax.bar(
            group_positions + offset,
            scores,
            bar_width,
            label=map_type,
            color=COLORS[map_type],
            yerr=[ci_lo, ci_hi],
            capsize=4,
            error_kw={"elinewidth": 1.5, "ecolor": "black"},
            zorder=3,
        )

    ax.set_xticks(group_positions)
    ax.set_xticklabels(label_names, rotation=20, ha="right", fontsize=9)
    ax.set_ylabel("avg time (µs/op)", fontsize=9)
    ax.set_title("HashMap Benchmark Overview  —  error bars = 99.9% confidence interval",
                 fontsize=11, fontweight="bold")
    ax.legend(fontsize=9)
    ax.grid(axis="y", linestyle="--", alpha=0.4, zorder=0)
    ax.set_axisbelow(True)


def scaling_line(ax, op_label, size_data):
    sizes = sorted(size_data)
    scores = [size_data[s]["score"] for s in sizes]
    ci_lo  = [size_data[s]["ci_lo"]  for s in sizes]
    ci_hi  = [size_data[s]["ci_hi"]  for s in sizes]

    color = OP_COLORS.get(op_label, "#9C27B0")
    ax.errorbar(
        sizes, scores,
        yerr=[ci_lo, ci_hi],
        fmt="o-",
        color=color,
        capsize=5,
        linewidth=2,
        markersize=6,
        label=op_label,
    )
    ax.set_xlabel("table size (entries)", fontsize=9)
    ax.set_ylabel("avg time (µs/op)", fontsize=9)
    ax.set_title(f"{op_label}  —  O(1) if flat", fontsize=11, fontweight="bold")
    ax.set_xticks(sizes)
    ax.set_xticklabels([str(s) for s in sizes], rotation=20, ha="right", fontsize=8)
    ax.grid(axis="y", linestyle="--", alpha=0.4)
    ax.set_axisbelow(True)
    ymin = max(0, min(scores) * 0.5)
    ymax = max(scores) * 1.5
    ax.set_ylim(ymin, ymax)


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else RESULTS_FILE
    if not os.path.exists(path):
        print(f"Results file not found: {path}")
        print("Run:  ./gradlew jmh")
        sys.exit(1)

    results = load(path)
    data, scaling = parse(results)
    os.makedirs(OUTPUT_DIR, exist_ok=True)

    if data:
        fig, axes = plt.subplots(1, len(SCENARIOS), figsize=(4 * len(SCENARIOS), 5))
        for ax, (title, key) in zip(axes, SCENARIOS.items()):
            grouped_bar(ax, title, key, data)
        plt.suptitle("Per-scenario breakdown  —  lower is better", fontsize=12, y=1.01)
        plt.tight_layout()
        out = os.path.join(OUTPUT_DIR, "per_scenario.png")
        plt.savefig(out, dpi=150, bbox_inches="tight")
        print(f"Saved {out}")

        fig2, ax2 = plt.subplots(figsize=(14, 6))
        overview_bar(ax2, data)
        plt.tight_layout()
        out2 = os.path.join(OUTPUT_DIR, "overview.png")
        plt.savefig(out2, dpi=150, bbox_inches="tight")
        print(f"Saved {out2}")

    if scaling:
        ops = [op for op in SCALING_OPS.values() if op in scaling]
        fig3, axes3 = plt.subplots(1, len(ops), figsize=(5 * len(ops), 5))
        if len(ops) == 1:
            axes3 = [axes3]
        for ax, op in zip(axes3, ops):
            scaling_line(ax, op, scaling[op])
        plt.suptitle(
            "O(1) scaling check  —  flat line = constant time regardless of table size",
            fontsize=12, y=1.01,
        )
        plt.tight_layout()
        out3 = os.path.join(OUTPUT_DIR, "scaling.png")
        plt.savefig(out3, dpi=150, bbox_inches="tight")
        print(f"Saved {out3}")

    plt.show()


if __name__ == "__main__":
    main()
