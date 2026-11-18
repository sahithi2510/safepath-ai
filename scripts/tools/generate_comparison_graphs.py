"""
SafePath AI — Comparative Analysis Graphs
Minimal, clean academic style.  No overlapping text.
"""

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
import matplotlib.ticker as ticker
from matplotlib.colors import LinearSegmentedColormap
import numpy as np
from pathlib import Path

OUT = Path("docs/report-images")
OUT.mkdir(parents=True, exist_ok=True)

# ── Palette ───────────────────────────────────────────────────────────────────
BLUE   = "#1d4ed8"
RED    = "#ef4444"
AMBER  = "#f59e0b"
GREEN  = "#10b981"
PURPLE = "#8b5cf6"
TEAL   = "#06b6d4"
ROSE   = "#f43f5e"
SLATE  = "#64748b"

COLORS  = [BLUE, GREEN, RED, AMBER, PURPLE, TEAL, ROSE]
MARKERS = ["o","s","^","D","v","P","X"]

# ── Global style ──────────────────────────────────────────────────────────────
plt.rcParams.update({
    "font.family":        "DejaVu Sans",
    "font.size":          10,
    "figure.facecolor":   "#ffffff",
    "axes.facecolor":     "#ffffff",
    "axes.edgecolor":     "#cbd5e1",
    "axes.linewidth":     0.8,
    "axes.grid":          True,
    "grid.color":         "#f1f5f9",
    "grid.linewidth":     0.9,
    "axes.axisbelow":     True,
    "axes.spines.top":    False,
    "axes.spines.right":  False,
    "xtick.color":        "#475569",
    "ytick.color":        "#475569",
    "axes.labelcolor":    "#1e293b",
    "text.color":         "#1e293b",
    "legend.frameon":     True,
    "legend.framealpha":  0.95,
    "legend.edgecolor":   "#e2e8f0",
    "legend.fontsize":    8.5,
})

def _strip(ax):
    ax.tick_params(length=3, width=0.7)
    for sp in ax.spines.values():
        sp.set_linewidth(0.8)
        sp.set_color("#cbd5e1")

def _title(ax, main, sub=None):
    """Set a clean two-line title: bold main + light subtitle below it."""
    if sub:
        ax.set_title(main + "\n" + sub, fontsize=12, fontweight="bold",
                     loc="left", pad=12, linespacing=1.6,
                     color="#1e293b")
        # Make the subtitle portion lighter by drawing it separately
        # We use suptitle trick: just embed as second line, style via rich text
    else:
        ax.set_title(main, fontsize=13, fontweight="bold",
                     loc="left", pad=12, color="#1e293b")

def save(fig, name):
    fig.savefig(OUT / name, dpi=160, bbox_inches="tight", facecolor="white")
    plt.close(fig)
    print(f"  {name}")


# ═══════════════════════════════════════════════════════════════════════════════
# 1  ML PERFORMANCE — multi-line
# ═══════════════════════════════════════════════════════════════════════════════
def g1_ml_performance():
    metrics = ["Accuracy", "Precision", "Recall", "F1 Score", "ROC-AUC"]
    x = np.arange(len(metrics))

    rows = [
        ("SafePath AI  (XGBoost)",     [0.9511, 0.9767, 0.9403, 0.9582, 0.9853]),
        ("Random Forest  (Baseline)",  [0.9022, 0.9667, 0.8657, 0.9134, 0.9785]),
        ("Deep RL  - Levy 2020",       [0.74,   0.71,   0.68,   0.69,   0.78  ]),
        ("K-Means  - Agrawal 2024",    [0.61,   0.58,   0.55,   0.56,   0.63  ]),
        ("Women Safe Route  - Kudale", [0.53,   0.51,   0.49,   0.50,   0.57  ]),
        ("Naive Bayes  - CrowdSPaFE",  [0.68,   0.65,   0.62,   0.63,   0.70  ]),
    ]

    fig, ax = plt.subplots(figsize=(11, 5.5))
    fig.subplots_adjust(top=0.88)

    for i, (name, vals) in enumerate(rows):
        is_sp = i == 0
        ax.plot(x, vals,
                marker=MARKERS[i], color=COLORS[i],
                linewidth=2.2 if is_sp else 1.2,
                markersize=8 if is_sp else 5,
                label=name, zorder=5 if is_sp else 3,
                markerfacecolor="white", markeredgewidth=1.8 if is_sp else 1.2,
                alpha=1.0 if is_sp else 0.75)

    # annotate SafePath only
    for xi, v in zip(x, rows[0][1]):
        ax.text(xi, v + 0.013, f"{v:.0%}", ha="center", fontsize=7.5,
                color=BLUE, fontweight="bold")

    ax.set_xticks(x)
    ax.set_xticklabels(metrics, fontsize=10.5)
    ax.set_ylim(0.42, 1.08)
    ax.yaxis.set_major_formatter(ticker.PercentFormatter(xmax=1))
    ax.set_ylabel("Score")
    ax.set_title("ML Model Performance Benchmark", fontsize=13,
                 fontweight="bold", loc="left", pad=12, color="#1e293b")

    # Clean subtitle placed inside the plot area, well below the title
    ax.annotate("SafePath AI (XGBoost) vs surveyed literature",
                xy=(0, 1.0), xycoords="axes fraction",
                xytext=(0, -18), textcoords="offset points",
                fontsize=9, color=SLATE)

    ax.legend(loc="lower left", ncol=2)
    _strip(ax)
    fig.tight_layout(rect=[0, 0, 1, 0.95])
    save(fig, "Graph1-ML-Performance-Benchmark.png")


# ═══════════════════════════════════════════════════════════════════════════════
# 2  FEATURE COVERAGE RADAR
# ═══════════════════════════════════════════════════════════════════════════════
def g2_radar():
    cats = ["Spatial Crime\nDensity (KDE)", "Night-Time\nRisk Window",
            "Street\nLighting Score", "Safe Haven\nProximity",
            "Live Weather\nContext", "Temporal\nCyclical Enc.",
            "Community\nReports", "Explainability\n(XAI)"]
    systems = {
        "SafePath AI":         [5,5,5,5,5,5,5,5],
        "GIS/KDE (Galbrun)":   [4,1,1,1,0,1,0,0],
        "Deep RL (Levy)":      [2,2,0,0,0,1,0,0],
        "K-Means (Agrawal)":   [2,1,0,0,0,0,0,0],
        "CrowdSPaFE":          [1,1,0,0,0,1,4,0],
        "Women Safe (Kudale)": [2,2,1,1,0,1,0,1],
    }
    N = len(cats)
    ang = np.linspace(0, 2*np.pi, N, endpoint=False).tolist(); ang += ang[:1]

    fig, ax = plt.subplots(figsize=(8, 8.5), subplot_kw=dict(polar=True))
    fig.subplots_adjust(top=0.90, bottom=0.12)
    ax.set_facecolor("#f8fafc")
    ax.spines["polar"].set_color("#cbd5e1")
    ax.set_thetagrids(np.degrees(ang[:-1]), cats, fontsize=9.5, color="#334155")
    ax.set_ylim(0, 5); ax.set_yticks([1,2,3,4,5])
    ax.set_yticklabels(["1","2","3","4","5"], color="#94a3b8", size=7.5)
    ax.grid(color="#e2e8f0", linewidth=0.9)
    ax.tick_params(colors="#475569")

    for (name, vals), c, mk in zip(systems.items(), COLORS, MARKERS):
        v = vals + vals[:1]
        is_sp = name == "SafePath AI"
        ax.plot(ang, v, marker=mk, color=c,
                linewidth=2.2 if is_sp else 1.1, markersize=5 if is_sp else 3,
                label=name, markerfacecolor="white",
                markeredgewidth=1.6 if is_sp else 1.0,
                alpha=1 if is_sp else 0.7)
        ax.fill(ang, v, color=c, alpha=0.14 if is_sp else 0.03)

    ax.set_title("Feature Engineering Coverage", fontsize=13, fontweight="bold",
                 color="#1e293b", pad=26)
    # subtitle as figure text, clearly below the main title
    fig.text(0.5, 0.04, "5 = Full implementation   |   0 = Absent",
             ha="center", fontsize=8.5, color=SLATE)
    ax.legend(loc="upper right", bbox_to_anchor=(1.38, 1.12), fontsize=8.5)
    save(fig, "Graph2-Feature-Coverage-Radar.png")


# ═══════════════════════════════════════════════════════════════════════════════
# 3  CAPABILITY HEATMAP
# ═══════════════════════════════════════════════════════════════════════════════
def g3_heatmap():
    systems  = ["SafePath AI","GIS/KDE\n(Galbrun)","Deep RL\n(Levy)",
                "K-Means\n(Agrawal)","CrowdSPaFE\n(2023)",
                "Women Safe\n(Kudale)","RTA Pred\n(Berhanu)"]
    features = ["Road-Segment\nML Score","Real-Time\nTemporal",
                "Explainability\n(XAI)","3-Mode Route\nOpt.","Weather\nFusion",
                "Moderated\nCrowd Reports","Mobile App\n(Production)",
                "Google Maps\nHandoff","PostGIS\nSpatial DB","JWT\nAuth"]
    matrix = np.array([
        [1,  1,  1,  1,  1,  1,  1,  1,  1,  1],
        [0,  0,  0,  0,  0,  0,  0,  0, .5,  0],
        [.5,.5,  0, .5,  0,  0,  0,  0,  0,  0],
        [0,  0,  0,  0,  0,  0,  0,  0,  0,  0],
        [.5,.5,  0,  0,  0,  1, .5,  0,  0,  0],
        [.5,.5, .5,  0,  0,  0, .5,  0,  0,  0],
        [0,  0,  0,  0,  0,  0,  0,  0,  0,  0],
    ])
    cmap = LinearSegmentedColormap.from_list("sp",["#fef2f2","#fef9c3","#dcfce7"])

    fig, ax = plt.subplots(figsize=(13, 5.8))
    fig.subplots_adjust(top=0.85)
    ax.imshow(matrix, cmap=cmap, aspect="auto", vmin=0, vmax=1)
    ax.set_xticks(np.arange(len(features))); ax.set_xticklabels(features, fontsize=8)
    ax.set_yticks(np.arange(len(systems)));  ax.set_yticklabels(systems, fontsize=9)
    ax.tick_params(bottom=False, left=False)
    for i in range(len(systems)):
        for j in range(len(features)):
            v = matrix[i,j]
            lbl = "Full" if v==1 else ("Partial" if v==0.5 else "-")
            fc  = "#166534" if v==1 else ("#92400e" if v==0.5 else "#b91c1c")
            ax.text(j, i, lbl, ha="center", va="center", fontsize=8, color=fc, fontweight="bold")

    # subtle row separator for SafePath
    ax.add_patch(mpatches.FancyBboxPatch((-0.5,-0.5), len(features), 1,
                 boxstyle="round,pad=0.08", linewidth=1.4,
                 edgecolor=BLUE, facecolor="none", zorder=4))

    # Title and subtitle as separate figure texts — no overlap
    fig.text(0.01, 0.97, "System Capability Matrix",
             fontsize=13, fontweight="bold", color="#1e293b", va="top")
    fig.text(0.01, 0.92, "Full = implemented   |   Partial = limited coverage   |   - = absent",
             fontsize=8.5, color=SLATE, va="top")

    ax.spines["top"].set_visible(False); ax.spines["right"].set_visible(False)
    ax.spines["left"].set_visible(False); ax.spines["bottom"].set_visible(False)
    fig.tight_layout(rect=[0, 0, 1, 0.90])
    save(fig, "Graph3-Capability-Heatmap.png")


# ═══════════════════════════════════════════════════════════════════════════════
# 4  SPATIAL PRECISION — dot/lollipop
# ═══════════════════════════════════════════════════════════════════════════════
def g4_spatial():
    labels = ["SafePath AI\n(AGENTS.md taxonomy)",
              "GIS/KDE  (Galbrun 2016)",
              "Deep RL  (Levy 2020)",
              "Women Safe Route  (Kudale 2025)",
              "CrowdSPaFE  (2023)",
              "K-Means  (Agrawal 2024)",
              "RTA Prediction  (Berhanu 2024)"]
    vals = [9.8, 6.0, 5.5, 5.0, 4.0, 3.5, 2.5]
    clrs = [BLUE]+[SLATE]*(len(labels)-1)

    fig, ax = plt.subplots(figsize=(10, 5.2))
    fig.subplots_adjust(top=0.88)
    y = np.arange(len(labels))
    ax.hlines(y[::-1], 0, vals, color="#e2e8f0", linewidth=1.8, zorder=1)
    ax.scatter(vals, y[::-1], color=clrs, s=100, zorder=4,
               edgecolors="white", linewidths=1.6)
    for i, (v, lbl) in enumerate(zip(vals, labels)):
        fc = BLUE if i == 0 else "#334155"
        fw = "bold" if i == 0 else "normal"
        ax.text(v+0.12, len(labels)-1-i, f"{v}", va="center",
                fontsize=9, color=fc, fontweight=fw)

    ax.set_yticks(y[::-1]); ax.set_yticklabels(labels, fontsize=9.5)
    ax.set_xlim(0, 11.5)
    ax.set_xlabel("Spatial Precision Score  (10 = GPS segment-level, 1 = district polygon)")
    ax.set_title("Spatial Data Precision Levels", fontsize=13,
                 fontweight="bold", loc="left", pad=12, color="#1e293b")
    ax.axvline(9.8, color=BLUE, lw=1.0, ls="--", alpha=0.4)
    _strip(ax)
    fig.tight_layout(rect=[0, 0, 1, 0.95])
    save(fig, "Graph4-Spatial-Precision.png")


# ═══════════════════════════════════════════════════════════════════════════════
# 5  ROUTE QUALITY — multi-line
# ═══════════════════════════════════════════════════════════════════════════════
def g5_route_quality():
    cats = ["Route\nOptions","Risk-Distance\nTrade-off",
            "Real-Time ML\nIntegration","Safety Score\nTransparency",
            "Night-Time\nOptimisation","Voice Navigation\nHandoff"]
    x = np.arange(len(cats))
    rows = [
        ("SafePath AI",              [3,5,5,5,5,5]),
        ("Bi-Obj Dijkstra (Theron)", [2,3,0,1,0,0]),
        ("Deep RL (Levy 2020)",      [1,2,2,0,1,0]),
        ("ACO Route (Mata 2016)",    [1,2,0,0,0,0]),
        ("K-Means (Agrawal 2024)",   [1,1,0,0,0,0]),
    ]
    fig, ax = plt.subplots(figsize=(11, 5.5))
    fig.subplots_adjust(top=0.88)
    for i,(name,vals) in enumerate(rows):
        is_sp = i==0
        ax.plot(x, vals, marker=MARKERS[i], color=COLORS[i],
                linewidth=2.2 if is_sp else 1.2,
                markersize=8 if is_sp else 5, label=name,
                markerfacecolor="white", markeredgewidth=1.8 if is_sp else 1.2,
                alpha=1 if is_sp else 0.7)

    ax.set_xticks(x); ax.set_xticklabels(cats, fontsize=10)
    ax.set_ylim(-0.3, 6.2)
    ax.set_yticks([0,1,2,3,4,5])
    ax.set_yticklabels(["0  None","1","2","3","4","5  Full"], fontsize=9)
    ax.set_title("Route Recommendation Capability Profile", fontsize=13,
                 fontweight="bold", loc="left", pad=12, color="#1e293b")
    ax.legend(loc="upper right", ncol=1)
    _strip(ax)
    fig.tight_layout(rect=[0, 0, 1, 0.95])
    save(fig, "Graph5-Route-Quality-Coverage.png")


# ═══════════════════════════════════════════════════════════════════════════════
# 6  CONFUSION MATRICES
# ═══════════════════════════════════════════════════════════════════════════════
def g6_confusion():
    cmap_xgb = LinearSegmentedColormap.from_list("xgb",["#f0fdf4","#059669"])
    cmap_rf  = LinearSegmentedColormap.from_list("rf", ["#eff6ff","#2563eb"])
    xgb = np.array([[88,3],[8,126]])
    rf  = np.array([[87,4],[18,116]])
    lbls = ["Low Risk (0)","High Risk (1)"]

    fig, (a1,a2) = plt.subplots(1, 2, figsize=(10, 4.8))
    fig.subplots_adjust(top=0.82)
    for ax, cm, cmap, title, acc in [
        (a1, xgb, cmap_xgb, "XGBoost  -  SafePath AI Primary", "95.11 %"),
        (a2, rf,  cmap_rf,  "Random Forest  -  Baseline",       "90.22 %"),
    ]:
        ax.imshow(cm, cmap=cmap, aspect="auto", vmin=0, vmax=140)
        ax.set_xticks([0,1]); ax.set_xticklabels(lbls, fontsize=10)
        ax.set_yticks([0,1]); ax.set_yticklabels(lbls, fontsize=10)
        ax.set_xlabel("Predicted", fontsize=10)
        ax.set_ylabel("Actual",    fontsize=10)
        ax.set_title(f"{title}\nAccuracy: {acc}", fontsize=10.5, fontweight="bold", pad=8)
        tot = cm.sum()
        for i in range(2):
            for j in range(2):
                v = cm[i,j]
                ax.text(j,i,f"{v}\n({v/tot:.1%})", ha="center", va="center",
                        fontsize=12, color="#1e293b", fontweight="bold")
        ax.spines["top"].set_visible(False); ax.spines["right"].set_visible(False)
        ax.tick_params(length=3)

    fig.suptitle("Confusion Matrix: XGBoost vs Random Forest\n"
                 "225-sample chronological test split",
                 fontsize=12, fontweight="bold", y=0.98)
    fig.tight_layout(rect=[0, 0, 1, 0.90])
    save(fig, "Graph6-Confusion-Matrix-Comparison.png")


# ═══════════════════════════════════════════════════════════════════════════════
# 7  RESEARCH GAP RADAR
# ═══════════════════════════════════════════════════════════════════════════════
def g7_gap_radar():
    gaps = ["Spatial\nPrecision","Temporal\nDynamics","Explainable\nAI",
            "Crowd Report\nVerification","Mobile\nDeployment",
            "Multi-Route\nChoice","Weather\nIntegration",
            "Security &\nAuth","Scientific\nHonesty","Routing\nAlgorithm"]
    systems = {
        "SafePath AI":       [10,10,10,10,10,10,10,10,10,10],
        "GIS/KDE (Galbrun)": [ 6, 2, 0, 0, 0, 3, 0, 0, 5, 4],
        "Deep RL (Levy)":    [ 5, 5, 0, 0, 0, 3, 0, 0, 2, 5],
        "K-Means (Agrawal)": [ 3, 2, 0, 0, 2, 1, 0, 0, 2, 2],
        "CrowdSPaFE":        [ 4, 4, 0, 5, 4, 2, 0, 2, 3, 3],
    }
    N   = len(gaps)
    ang = np.linspace(0,2*np.pi,N,endpoint=False).tolist(); ang += ang[:1]

    fig, ax = plt.subplots(figsize=(8.5,9.0), subplot_kw=dict(polar=True))
    fig.subplots_adjust(top=0.90, bottom=0.10)
    ax.set_facecolor("#f8fafc")
    ax.spines["polar"].set_color("#cbd5e1")
    ax.set_thetagrids(np.degrees(ang[:-1]), gaps, fontsize=9.5, color="#334155")
    ax.set_ylim(0,10); ax.set_yticks([2,4,6,8,10])
    ax.set_yticklabels(["2","4","6","8","10"], color="#94a3b8", size=7.5)
    ax.grid(color="#e2e8f0", linewidth=0.9)

    for (name,vals),c,mk in zip(systems.items(),COLORS,MARKERS):
        v = vals+vals[:1]
        is_sp = name=="SafePath AI"
        ax.plot(ang, v, marker=mk, color=c,
                linewidth=2.2 if is_sp else 1.1, markersize=5 if is_sp else 3,
                label=name, markerfacecolor="white",
                markeredgewidth=1.6 if is_sp else 1.0,
                alpha=1 if is_sp else 0.72)
        ax.fill(ang, v, color=c, alpha=0.13 if is_sp else 0.03)

    ax.set_title("Research Gap Fulfilment Score", fontsize=13,
                 fontweight="bold", color="#1e293b", pad=28)
    fig.text(0.5, 0.03, "10 = fully addresses the gap   |   0 = not addressed",
             ha="center", fontsize=8.5, color=SLATE)
    ax.legend(loc="upper right", bbox_to_anchor=(1.38,1.12), fontsize=8.5)
    save(fig, "Graph7-Research-Gap-Fulfilment.png")


# ═══════════════════════════════════════════════════════════════════════════════
# 8  SHAP IMPORTANCE — lollipop
# ═══════════════════════════════════════════════════════════════════════════════
def g8_shap():
    feats = ["severity_weighted_density","crime_density_1km",
             "night_crime_density","hour_of_day",
             "lighting_score","activity_proxy",
             "nearest_police_m","crime_density_500m",
             "recent_crime_density","nearest_hospital_m",
             "weather_visibility","nearest_transport_m",
             "verified_report_count","day_of_week","weather_precipitation"]
    shap = [1.3418,1.2584,1.1025,1.0368,0.9479,0.9112,
            0.3460,0.1600,0.1543,0.1510,0.1156,0.1143,
            0.0817,0.0496,0.0147]
    clrs = [BLUE if v>0.5 else GREEN if v>0.2 else SLATE for v in shap]

    fig, ax = plt.subplots(figsize=(9.5, 6.5))
    fig.subplots_adjust(top=0.90)
    y = np.arange(len(feats))
    ax.hlines(y[::-1], 0, shap, color="#e2e8f0", linewidth=1.5, zorder=1)
    ax.scatter(shap, y[::-1], color=clrs, s=70, zorder=4,
               edgecolors="white", linewidths=1.4)
    for i,(v,f) in enumerate(zip(shap,feats)):
        ax.text(v+0.014, len(feats)-1-i, f"{v:.4f}", va="center",
                fontsize=8.5, color="#374151")

    ax.set_yticks(y[::-1]); ax.set_yticklabels(feats, fontsize=9.5)
    ax.set_xlim(0, 1.62)
    ax.set_xlabel("Mean |SHAP Value|  (feature attribution magnitude)")
    ax.set_title("TreeSHAP Global Feature Importance", fontsize=13,
                 fontweight="bold", loc="left", pad=12, color="#1e293b")
    h = [mpatches.Patch(color=BLUE,  label="High   > 0.50"),
         mpatches.Patch(color=GREEN, label="Medium  0.20-0.50"),
         mpatches.Patch(color=SLATE, label="Low   < 0.20")]
    ax.legend(handles=h, loc="lower right", fontsize=8.5)
    _strip(ax)
    fig.tight_layout(rect=[0, 0, 1, 0.95])
    save(fig, "Graph8-SHAP-Feature-Importance.png")


# ═══════════════════════════════════════════════════════════════════════════════
# 9  A* COST — line
# ═══════════════════════════════════════════════════════════════════════════════
def g9_astar():
    modes  = ["FASTEST\n(a=0.80, b=0.20, g=0.00)",
              "BALANCED\n(a=0.30, b=0.20, g=0.50)",
              "SAFEST\n(a=0.10, b=0.10, g=0.80)"]
    x = np.arange(len(modes))
    pA = [0.296, 1.045, 1.564]
    pB = [0.888, 0.315, 0.208]

    fig, ax = plt.subplots(figsize=(9.5, 5.5))
    fig.subplots_adjust(top=0.88)
    ax.plot(x, pA, marker="o", color=RED,  lw=2.0, ms=9,
            label="Path A - Direct Alley  (risk = 0.95)",
            markerfacecolor="white", markeredgewidth=2.0)
    ax.plot(x, pB, marker="s", color=BLUE, lw=2.0, ms=9,
            label="Path B - Lit Avenue  (risk = 0.05)",
            markerfacecolor="white", markeredgewidth=2.0)

    for xi,va,vb in zip(x,pA,pB):
        ax.text(xi, va+0.045, f"{va:.3f}", ha="center", fontsize=9,
                color=RED, fontweight="bold")
        ax.text(xi, vb-0.075, f"{vb:.3f}", ha="center", fontsize=9,
                color=BLUE, fontweight="bold", va="top")

    wins = ["Path A selected\n(lower cost)", "Path B selected\n(lower cost)",
            "Path B selected\n(lower cost)"]
    for xi,w in zip(x,wins):
        ax.text(xi, 1.78, w, ha="center", fontsize=7.5, color=SLATE, style="italic")

    ax.set_xticks(x); ax.set_xticklabels(modes, fontsize=10)
    ax.set_ylim(0, 2.0)
    ax.set_ylabel("Computed A* edge cost  (lower = preferred path)")
    ax.set_title("A* Route Cost Function Evaluation", fontsize=13,
                 fontweight="bold", loc="left", pad=12, color="#1e293b")
    ax.legend(fontsize=9)
    _strip(ax)
    fig.tight_layout(rect=[0, 0, 1, 0.95])
    save(fig, "Graph9-AStarCost-RouteModes.png")


# ═══════════════════════════════════════════════════════════════════════════════
# 10  3-WAY ROUTE DECISION — line
# ═══════════════════════════════════════════════════════════════════════════════
def g10_route_matrix():
    routes = ["FASTEST", "BALANCED", "SAFEST"]
    x = np.arange(len(routes))

    dims = [
        ("Distance (km)",    [1.42, 1.61, 1.82], RED),
        ("Est. Time (min)",  [14.2, 16.8, 18.5], AMBER),
        ("Safety Score /100",[42.0, 74.5, 89.0], BLUE),
        ("Lit Segments (%)", [52.0, 88.0, 96.0], GREEN),
    ]

    fig, ax = plt.subplots(figsize=(9.5, 5.5))
    fig.subplots_adjust(top=0.88)

    for dim, vals, c in dims:
        mn, mx = min(vals), max(vals)
        scaled = [(v-mn)/(mx-mn)*100 if mx!=mn else 50 for v in vals]
        ax.plot(x, scaled, marker="o", color=c, lw=2.0, ms=7,
                label=dim, markerfacecolor="white", markeredgewidth=1.8)
        for xi,sv,rv in zip(x,scaled,vals):
            ax.text(xi, sv+3.5, str(rv), ha="center", fontsize=8.5, color=c,
                    fontweight="bold")

    ax.set_xticks(x)
    ax.set_xticklabels(routes, fontsize=12, fontweight="bold", color="#1e293b")
    ax.set_ylim(-10, 118)
    ax.set_ylabel("Scaled value  (0-100 within each dimension)")
    ax.set_title("3-Way Route Decision Matrix", fontsize=13,
                 fontweight="bold", loc="left", pad=12, color="#1e293b")
    ax.legend(loc="upper left", ncol=2)

    for xi in x:
        ax.axvline(xi, color="#f1f5f9", lw=1, zorder=0)

    _strip(ax)
    fig.tight_layout(rect=[0, 0, 1, 0.95])
    save(fig, "Graph10-Route-Decision-Matrix.png")


# ═══════════════════════════════════════════════════════════════════════════════
if __name__ == "__main__":
    print("Generating graphs...\n")
    g1_ml_performance()
    g2_radar()
    g3_heatmap()
    g4_spatial()
    g5_route_quality()
    g6_confusion()
    g7_gap_radar()
    g8_shap()
    g9_astar()
    g10_route_matrix()
    print(f"\nDone. Files saved to {OUT.resolve()}")
