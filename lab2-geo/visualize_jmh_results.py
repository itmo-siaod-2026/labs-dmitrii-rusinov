import csv
import sys
from collections import defaultdict
from pathlib import Path

import matplotlib.pyplot as plt


ERROR_COL = "Score Error (99.9%)"


def load_jmh_csv(path: Path):
    with path.open(newline="") as f:
        reader = csv.DictReader(f)
        rows = list(reader)
    return rows


def detect_param_column(rows, param_name: str) -> str:
    param_name = param_name.strip()
    candidates = [param_name, f"Param:{param_name}", f"Param: {param_name}"]
    if not rows:
        raise ValueError("No rows in CSV.")
    columns = rows[0].keys()
    for c in candidates:
        if c in columns:
            return c
    raise KeyError(
        f"Could not find parameter column for '{param_name}'. "
        f"Available columns: {', '.join(columns)}"
    )


def _parse_error(row) -> float:
    raw = row.get(ERROR_COL, "").strip()
    if not raw or raw in ("NaN", "N/A", ""):
        return 0.0
    try:
        return float(raw)
    except ValueError:
        return 0.0


def plot_benchmark_vs_param(rows, benchmark_filter: str, param_name: str, out_dir: Path):
    filtered = [r for r in rows if benchmark_filter in r["Benchmark"]]
    if not filtered:
        print(f"No rows matched benchmark filter '{benchmark_filter}'.")
        return

    param_col = detect_param_column(filtered, param_name)

    grouped = defaultdict(list)
    for r in filtered:
        try:
            x = float(r[param_col].replace("_", ""))
            y = float(r["Score"])
            err = _parse_error(r)
        except (ValueError, KeyError):
            continue
        key = r["Benchmark"]
        grouped[key].append((x, y, err, r.get("Unit", "")))

    out_dir.mkdir(parents=True, exist_ok=True)

    for bench_name, points in grouped.items():
        if not points:
            continue
        points.sort(key=lambda t: t[0])
        xs = [p[0] for p in points]
        ys = [p[1] for p in points]
        errs = [p[2] for p in points]
        unit = points[0][3] if points[0][3] else "time"
        has_ci = any(e > 0 for e in errs)

        plt.figure()
        plt.plot(xs, ys, marker="o", linewidth=1.5, zorder=3)
        if has_ci:
            y_lo = [y - e for y, e in zip(ys, errs)]
            y_hi = [y + e for y, e in zip(ys, errs)]
            plt.fill_between(xs, y_lo, y_hi, alpha=0.25, label="99.9% CI")
            plt.legend()
        plt.xlabel(param_name)
        plt.ylabel(f"Score ({unit})")
        plt.title(bench_name)
        plt.grid(True)

        safe_name = bench_name.replace(".", "_").replace("/", "_")
        out_path = out_dir / f"{safe_name}_vs_{param_name}.png"
        plt.savefig(out_path, bbox_inches="tight")
        plt.close()
        print(f"Wrote {out_path}")


def auto_plot_all(rows, out_dir: Path) -> int:
    if not rows:
        print("No rows to plot.")
        return 1

    columns = list(rows[0].keys())
    param_cols = [c for c in columns if c.startswith("Param:")]

    if not param_cols:
        print("No parameter columns found in CSV.")
        return 1
    if len(param_cols) > 1:
        print(
            "Expected a single parameter column, "
            f"but found multiple: {', '.join(param_cols)}"
        )
        return 1

    param_col = param_cols[0]
    param_name = param_col.removeprefix("Param:").strip()

    by_benchmark = defaultdict(list)
    for r in rows:
        by_benchmark[r["Benchmark"]].append(r)

    for bench_name, bench_rows in by_benchmark.items():
        values = []
        for r in bench_rows:
            v = r.get(param_col, "")
            if not v:
                continue
            try:
                values.append(float(v.replace("_", "")))
            except ValueError:
                continue

        if len(set(values)) < 2:
            continue

        plot_benchmark_vs_param(rows, bench_name, param_name, out_dir)

    return 0


def collect_csv_files(paths: list[str]) -> list[Path]:
    """Resolve CLI args into a list of CSV files.

    Each arg can be a file or a directory (scanned for *.csv).
    """
    result: list[Path] = []
    for raw in paths:
        p = Path(raw)
        if p.is_file():
            result.append(p)
        elif p.is_dir():
            result.extend(sorted(p.rglob("*.csv")))
    # Keep stable order and remove duplicates.
    return sorted(set(result))


def find_default_csvs() -> list[Path]:
    candidates = [
        Path("app") / "build" / "jmh-results",
        Path(__file__).parent / "app" / "build" / "jmh-results",
        Path("bench"),
        Path(__file__).parent / "bench",
    ]
    for d in candidates:
        if d.is_dir():
            csvs = sorted(d.rglob("*.csv"))
            if csvs:
                return csvs

    single_candidates = [
        Path("app") / "build" / "jmh-results.csv",
        Path(__file__).parent / "app" / "build" / "jmh-results.csv",
        Path("jmh-results.csv"),
        Path("bench") / "geoloc" / "jmh-geoloc.csv",
        Path(__file__).parent / "bench" / "geoloc" / "jmh-geoloc.csv",
    ]
    for p in single_candidates:
        if p.is_file():
            return [p]
    return []


def main() -> int:
    if len(sys.argv) > 1:
        csv_files = collect_csv_files(sys.argv[1:])
    else:
        csv_files = find_default_csvs()

    if not csv_files:
        print("No CSV files found. Pass files/directories as arguments or "
              "place them in app/build/jmh-results/.")
        return 1

    all_rows: list[dict] = []
    for csv_path in csv_files:
        rows = load_jmh_csv(csv_path)
        print(f"Loaded {len(rows)} rows from {csv_path}")
        all_rows.extend(rows)

    if not all_rows:
        print("All CSV files were empty.")
        return 1

    out_dir = csv_files[0].parent / "jmh-plots"
    return auto_plot_all(all_rows, out_dir)


if __name__ == "__main__":
    raise SystemExit(main())
