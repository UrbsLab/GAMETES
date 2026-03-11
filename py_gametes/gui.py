from __future__ import annotations

import contextlib
import queue
import shlex
import threading
from dataclasses import dataclass
from pathlib import Path
from typing import List, Optional

from .cli import run_document
from .document import SnpGenDocument


try:
    import tkinter as tk
    from tkinter import filedialog, messagebox, ttk
except Exception:  # pragma: no cover - handled by caller
    tk = None
    filedialog = None
    messagebox = None
    ttk = None


if tk is not None:
    _TkRootBase = tk.Tk
    _TkTopLevelBase = tk.Toplevel
else:
    class _TkRootBase:  # pragma: no cover - used only when Tkinter is absent
        pass

    class _TkTopLevelBase:  # pragma: no cover - used only when Tkinter is absent
        pass


@dataclass
class ModelSpec:
    heritability: float
    case_proportion: Optional[float]
    mafs: List[float]
    output_prefix: str
    use_odds_ratio: bool = False
    weight: float = 1.0

    def to_blob(self) -> str:
        tokens = ["-h", str(self.heritability)]
        if self.case_proportion is not None:
            tokens += ["-p", str(self.case_proportion)]
        if self.use_odds_ratio:
            tokens.append("-d")
        for maf in self.mafs:
            tokens += ["-a", str(maf)]
        tokens += ["-o", self.output_prefix]
        return shlex.join(tokens)


@dataclass
class InputModelSpec:
    path: str
    weight: float = 1.0


class _QueueWriter:
    def __init__(self, q: "queue.Queue[str]") -> None:
        self._q = q

    def write(self, s: str) -> int:
        if s:
            self._q.put(s)
        return len(s)

    def flush(self) -> None:
        return


class ModelDialog(_TkTopLevelBase):
    def __init__(self, parent: tk.Tk, model: Optional[ModelSpec] = None) -> None:
        super().__init__(parent)
        self.title("Model")
        self.resizable(False, False)
        self.transient(parent)
        self.grab_set()

        self.result: Optional[ModelSpec] = None

        initial = model or ModelSpec(
            heritability=0.2,
            case_proportion=0.5,
            mafs=[0.3, 0.2],
            output_prefix="model",
            use_odds_ratio=False,
            weight=1.0,
        )

        frm = ttk.Frame(self, padding=12)
        frm.grid(row=0, column=0, sticky="nsew")

        self.herit_var = tk.StringVar(value=str(initial.heritability))
        self.case_prop_var = tk.StringVar(value="" if initial.case_proportion is None else str(initial.case_proportion))
        self.maf_var = tk.StringVar(value=",".join(str(x) for x in initial.mafs))
        self.output_var = tk.StringVar(value=initial.output_prefix)
        self.weight_var = tk.StringVar(value=str(initial.weight))
        self.odds_var = tk.BooleanVar(value=initial.use_odds_ratio)

        ttk.Label(frm, text="Heritability").grid(row=0, column=0, sticky="w")
        ttk.Entry(frm, textvariable=self.herit_var, width=28).grid(row=0, column=1, sticky="ew")

        ttk.Label(frm, text="Case Proportion (optional)").grid(row=1, column=0, sticky="w")
        ttk.Entry(frm, textvariable=self.case_prop_var, width=28).grid(row=1, column=1, sticky="ew")

        ttk.Label(frm, text="MAFs (comma-separated)").grid(row=2, column=0, sticky="w")
        ttk.Entry(frm, textvariable=self.maf_var, width=28).grid(row=2, column=1, sticky="ew")

        ttk.Label(frm, text="Output Prefix").grid(row=3, column=0, sticky="w")
        output_entry = ttk.Entry(frm, textvariable=self.output_var, width=28)
        output_entry.grid(row=3, column=1, sticky="ew")

        def browse_output() -> None:
            assert filedialog is not None
            p = filedialog.asksaveasfilename(title="Model output prefix")
            if p:
                self.output_var.set(p)

        ttk.Button(frm, text="Browse", command=browse_output).grid(row=3, column=2, padx=(6, 0))

        ttk.Label(frm, text="Weight").grid(row=4, column=0, sticky="w")
        ttk.Entry(frm, textvariable=self.weight_var, width=28).grid(row=4, column=1, sticky="ew")

        ttk.Checkbutton(frm, text="Use Odds Ratio", variable=self.odds_var).grid(row=5, column=0, columnspan=2, sticky="w")

        btns = ttk.Frame(frm)
        btns.grid(row=6, column=0, columnspan=3, pady=(10, 0), sticky="e")
        ttk.Button(btns, text="Cancel", command=self._cancel).grid(row=0, column=0, padx=(0, 6))
        ttk.Button(btns, text="Save", command=self._save).grid(row=0, column=1)

        frm.columnconfigure(1, weight=1)
        self.bind("<Escape>", lambda _e: self._cancel())
        self.bind("<Return>", lambda _e: self._save())

    def _cancel(self) -> None:
        self.result = None
        self.destroy()

    def _save(self) -> None:
        assert messagebox is not None
        try:
            heritability = float(self.herit_var.get().strip())
            case_raw = self.case_prop_var.get().strip()
            case_prop = None if case_raw == "" else float(case_raw)
            mafs = [float(x.strip()) for x in self.maf_var.get().split(",") if x.strip()]
            if not mafs:
                raise ValueError("At least one MAF is required")
            output_prefix = self.output_var.get().strip()
            if not output_prefix:
                raise ValueError("Output prefix is required")
            weight = float(self.weight_var.get().strip())
            if weight == 0:
                raise ValueError("Weight must be non-zero")
        except Exception as exc:
            messagebox.showerror("Invalid model", str(exc), parent=self)
            return

        self.result = ModelSpec(
            heritability=heritability,
            case_proportion=case_prop,
            mafs=mafs,
            output_prefix=output_prefix,
            use_odds_ratio=bool(self.odds_var.get()),
            weight=weight,
        )
        self.destroy()


class InputModelDialog(_TkTopLevelBase):
    def __init__(self, parent: tk.Tk, model: Optional[InputModelSpec] = None) -> None:
        super().__init__(parent)
        self.title("Loaded Model File")
        self.resizable(False, False)
        self.transient(parent)
        self.grab_set()

        self.result: Optional[InputModelSpec] = None

        initial = model or InputModelSpec(path="", weight=1.0)

        frm = ttk.Frame(self, padding=12)
        frm.grid(row=0, column=0, sticky="nsew")

        self.path_var = tk.StringVar(value=initial.path)
        self.weight_var = tk.StringVar(value=str(initial.weight))

        ttk.Label(frm, text="Model File").grid(row=0, column=0, sticky="w")
        ttk.Entry(frm, textvariable=self.path_var, width=42).grid(row=0, column=1, sticky="ew")

        def browse() -> None:
            assert filedialog is not None
            p = filedialog.askopenfilename(title="Select model file", filetypes=[("Text", "*.txt"), ("All", "*")])
            if p:
                self.path_var.set(p)

        ttk.Button(frm, text="Browse", command=browse).grid(row=0, column=2, padx=(6, 0))

        ttk.Label(frm, text="Weight").grid(row=1, column=0, sticky="w")
        ttk.Entry(frm, textvariable=self.weight_var, width=16).grid(row=1, column=1, sticky="w")

        btns = ttk.Frame(frm)
        btns.grid(row=2, column=0, columnspan=3, pady=(10, 0), sticky="e")
        ttk.Button(btns, text="Cancel", command=self._cancel).grid(row=0, column=0, padx=(0, 6))
        ttk.Button(btns, text="Save", command=self._save).grid(row=0, column=1)

        frm.columnconfigure(1, weight=1)
        self.bind("<Escape>", lambda _e: self._cancel())
        self.bind("<Return>", lambda _e: self._save())

    def _cancel(self) -> None:
        self.result = None
        self.destroy()

    def _save(self) -> None:
        assert messagebox is not None
        try:
            path = self.path_var.get().strip()
            if not path:
                raise ValueError("Model file path is required")
            weight = float(self.weight_var.get().strip())
            if weight == 0:
                raise ValueError("Weight must be non-zero")
        except Exception as exc:
            messagebox.showerror("Invalid model file", str(exc), parent=self)
            return

        self.result = InputModelSpec(path=path, weight=weight)
        self.destroy()


class GametesGui(_TkRootBase):
    def __init__(self) -> None:
        super().__init__()
        self.title("GAMETES Python GUI")
        self.geometry("1200x850")

        self.models: List[ModelSpec] = []
        self.input_models: List[InputModelSpec] = []

        self.log_queue: "queue.Queue[str]" = queue.Queue()
        self._running = False

        self._build_ui()
        self.after(100, self._poll_logs)

    def _build_ui(self) -> None:
        root = ttk.Frame(self, padding=10)
        root.pack(fill="both", expand=True)

        top = ttk.Panedwindow(root, orient="horizontal")
        top.pack(fill="both", expand=True)

        left = ttk.Frame(top)
        right = ttk.Frame(top)
        top.add(left, weight=2)
        top.add(right, weight=3)

        self._build_model_panel(left)
        self._build_dataset_panel(right)
        self._build_run_panel(root)

    def _build_model_panel(self, parent: ttk.Frame) -> None:
        model_frame = ttk.LabelFrame(parent, text="Model Construction", padding=8)
        model_frame.pack(fill="both", expand=True)

        cols = ("heri", "prev", "maf", "weight", "output", "odds")
        self.model_tree = ttk.Treeview(model_frame, columns=cols, show="headings", height=10)
        self.model_tree.heading("heri", text="Heritability")
        self.model_tree.heading("prev", text="Case Proportion")
        self.model_tree.heading("maf", text="MAFs")
        self.model_tree.heading("weight", text="Weight")
        self.model_tree.heading("output", text="Output Prefix")
        self.model_tree.heading("odds", text="OddsRatio")
        self.model_tree.column("heri", width=90, anchor="center")
        self.model_tree.column("prev", width=110, anchor="center")
        self.model_tree.column("maf", width=150)
        self.model_tree.column("weight", width=70, anchor="center")
        self.model_tree.column("output", width=210)
        self.model_tree.column("odds", width=70, anchor="center")
        self.model_tree.pack(fill="x", expand=False)

        btn_row = ttk.Frame(model_frame)
        btn_row.pack(fill="x", pady=(6, 8))
        ttk.Button(btn_row, text="Add Model", command=self._add_model).pack(side="left")
        ttk.Button(btn_row, text="Edit Model", command=self._edit_model).pack(side="left", padx=(6, 0))
        ttk.Button(btn_row, text="Remove Model", command=self._remove_model).pack(side="left", padx=(6, 0))

        load_frame = ttk.LabelFrame(model_frame, text="Loaded Model Files (-i)", padding=8)
        load_frame.pack(fill="both", expand=True)

        self.input_model_tree = ttk.Treeview(load_frame, columns=("path", "weight"), show="headings", height=7)
        self.input_model_tree.heading("path", text="Path")
        self.input_model_tree.heading("weight", text="Weight")
        self.input_model_tree.column("path", width=450)
        self.input_model_tree.column("weight", width=80, anchor="center")
        self.input_model_tree.pack(fill="x", expand=False)

        i_btn = ttk.Frame(load_frame)
        i_btn.pack(fill="x", pady=(6, 0))
        ttk.Button(i_btn, text="Add File", command=self._add_input_model).pack(side="left")
        ttk.Button(i_btn, text="Edit File", command=self._edit_input_model).pack(side="left", padx=(6, 0))
        ttk.Button(i_btn, text="Remove File", command=self._remove_input_model).pack(side="left", padx=(6, 0))

        ras_frame = ttk.LabelFrame(model_frame, text="Model Generation Settings", padding=8)
        ras_frame.pack(fill="x", expand=False, pady=(8, 0))

        self.quantiles_var = tk.StringVar(value="1")
        self.population_var = tk.StringVar(value="100")
        self.try_count_var = tk.StringVar(value="5000")
        self.seed_var = tk.StringVar(value="")

        ttk.Label(ras_frame, text="Quantiles (-q)").grid(row=0, column=0, sticky="w")
        ttk.Entry(ras_frame, textvariable=self.quantiles_var, width=10).grid(row=0, column=1, sticky="w", padx=(6, 18))
        ttk.Label(ras_frame, text="Population (-p)").grid(row=0, column=2, sticky="w")
        ttk.Entry(ras_frame, textvariable=self.population_var, width=10).grid(row=0, column=3, sticky="w", padx=(6, 18))
        ttk.Label(ras_frame, text="Try Count (-t)").grid(row=0, column=4, sticky="w")
        ttk.Entry(ras_frame, textvariable=self.try_count_var, width=12).grid(row=0, column=5, sticky="w", padx=(6, 18))
        ttk.Label(ras_frame, text="Random Seed (-r)").grid(row=0, column=6, sticky="w")
        ttk.Entry(ras_frame, textvariable=self.seed_var, width=12).grid(row=0, column=7, sticky="w", padx=(6, 0))

    def _build_dataset_panel(self, parent: ttk.Frame) -> None:
        dataset = ttk.LabelFrame(parent, text="Dataset Construction", padding=8)
        dataset.pack(fill="both", expand=True)

        file_frame = ttk.LabelFrame(dataset, text="Input Files", padding=8)
        file_frame.pack(fill="x", expand=False)

        self.predictive_var = tk.StringVar(value="")
        self.noise_var = tk.StringVar(value="")

        ttk.Label(file_frame, text="Predictive File (-v)").grid(row=0, column=0, sticky="w")
        ttk.Entry(file_frame, textvariable=self.predictive_var, width=55).grid(row=0, column=1, sticky="ew", padx=(6, 6))
        ttk.Button(file_frame, text="Browse", command=self._browse_predictive).grid(row=0, column=2)

        ttk.Label(file_frame, text="Noise File (-z)").grid(row=1, column=0, sticky="w", pady=(6, 0))
        ttk.Entry(file_frame, textvariable=self.noise_var, width=55).grid(row=1, column=1, sticky="ew", padx=(6, 6), pady=(6, 0))
        ttk.Button(file_frame, text="Browse", command=self._browse_noise).grid(row=1, column=2, pady=(6, 0))
        file_frame.columnconfigure(1, weight=1)

        noise_gen = ttk.LabelFrame(dataset, text="Non-Predictive Attributes", padding=8)
        noise_gen.pack(fill="x", expand=False, pady=(8, 0))

        self.attr_count_var = tk.StringVar(value="100")
        self.af_min_var = tk.StringVar(value="0.01")
        self.af_max_var = tk.StringVar(value="0.5")

        ttk.Label(noise_gen, text="Total Attributes (-a)").grid(row=0, column=0, sticky="w")
        ttk.Entry(noise_gen, textvariable=self.attr_count_var, width=12).grid(row=0, column=1, sticky="w", padx=(6, 20))
        ttk.Label(noise_gen, text="Allele Freq Min (-n)").grid(row=0, column=2, sticky="w")
        ttk.Entry(noise_gen, textvariable=self.af_min_var, width=10).grid(row=0, column=3, sticky="w", padx=(6, 20))
        ttk.Label(noise_gen, text="Allele Freq Max (-x)").grid(row=0, column=4, sticky="w")
        ttk.Entry(noise_gen, textvariable=self.af_max_var, width=10).grid(row=0, column=5, sticky="w", padx=(6, 0))

        props = ttk.LabelFrame(dataset, text="Dataset Properties", padding=8)
        props.pack(fill="x", expand=False, pady=(8, 0))

        self.endpoint_var = tk.StringVar(value="binary")
        self.mixed_var = tk.StringVar(value="hierarchical")
        self.hetero_label_var = tk.BooleanVar(value=False)

        ttk.Label(props, text="Endpoint Type").grid(row=0, column=0, sticky="w")
        ttk.Radiobutton(props, text="Binary Class", value="binary", variable=self.endpoint_var, command=self._update_endpoint_ui).grid(
            row=0, column=1, sticky="w"
        )
        ttk.Radiobutton(props, text="Quantitative Trait", value="continuous", variable=self.endpoint_var, command=self._update_endpoint_ui).grid(
            row=0, column=2, sticky="w", padx=(8, 0)
        )

        ttk.Label(props, text="Mixed Model Type (-h)").grid(row=1, column=0, sticky="w", pady=(8, 0))
        ttk.Radiobutton(props, text="Hierarchical (Additive)", value="hierarchical", variable=self.mixed_var, command=self._update_mixed_ui).grid(
            row=1, column=1, sticky="w", pady=(8, 0)
        )
        ttk.Radiobutton(props, text="Heterogeneous", value="heterogeneous", variable=self.mixed_var, command=self._update_mixed_ui).grid(
            row=1, column=2, sticky="w", padx=(8, 0), pady=(8, 0)
        )
        self.hetero_label_cb = ttk.Checkbutton(props, text="Add heterogeneous labels (-b)", variable=self.hetero_label_var)
        self.hetero_label_cb.grid(row=1, column=3, sticky="w", padx=(12, 0), pady=(8, 0))

        self.case_var = tk.StringVar(value="400")
        self.control_var = tk.StringVar(value="400")
        self.total_var = tk.StringVar(value="800")
        self.std_var = tk.StringVar(value="0.2")

        self.binary_frame = ttk.Frame(props)
        self.binary_frame.grid(row=2, column=0, columnspan=4, sticky="w", pady=(8, 0))
        ttk.Label(self.binary_frame, text="Case Count (-s)").grid(row=0, column=0, sticky="w")
        ttk.Entry(self.binary_frame, textvariable=self.case_var, width=12).grid(row=0, column=1, sticky="w", padx=(6, 16))
        ttk.Label(self.binary_frame, text="Control Count (-w)").grid(row=0, column=2, sticky="w")
        ttk.Entry(self.binary_frame, textvariable=self.control_var, width=12).grid(row=0, column=3, sticky="w", padx=(6, 0))

        self.cont_frame = ttk.Frame(props)
        self.cont_frame.grid(row=3, column=0, columnspan=4, sticky="w", pady=(8, 0))
        ttk.Label(self.cont_frame, text="Total Count (-t)").grid(row=0, column=0, sticky="w")
        ttk.Entry(self.cont_frame, textvariable=self.total_var, width=12).grid(row=0, column=1, sticky="w", padx=(6, 16))
        ttk.Label(self.cont_frame, text="Std Dev (-d)").grid(row=0, column=2, sticky="w")
        ttk.Entry(self.cont_frame, textvariable=self.std_var, width=12).grid(row=0, column=3, sticky="w", padx=(6, 0))

        out = ttk.LabelFrame(dataset, text="Output", padding=8)
        out.pack(fill="x", expand=False, pady=(8, 0))

        self.repl_var = tk.StringVar(value="1")
        self.dataset_out_var = tk.StringVar(value="dataset")

        ttk.Label(out, text="Replicates (-r)").grid(row=0, column=0, sticky="w")
        ttk.Entry(out, textvariable=self.repl_var, width=12).grid(row=0, column=1, sticky="w", padx=(6, 20))

        ttk.Label(out, text="Dataset Output Prefix (-o)").grid(row=1, column=0, sticky="w", pady=(8, 0))
        ttk.Entry(out, textvariable=self.dataset_out_var, width=55).grid(row=1, column=1, sticky="ew", padx=(6, 6), pady=(8, 0))
        ttk.Button(out, text="Browse", command=self._browse_dataset_out).grid(row=1, column=2, pady=(8, 0))
        out.columnconfigure(1, weight=1)

        self._update_endpoint_ui()
        self._update_mixed_ui()

    def _build_run_panel(self, parent: ttk.Frame) -> None:
        run_panel = ttk.LabelFrame(parent, text="Run", padding=8)
        run_panel.pack(fill="both", expand=True, pady=(8, 0))

        btns = ttk.Frame(run_panel)
        btns.pack(fill="x", expand=False)

        self.run_btn = ttk.Button(btns, text="Generate", command=self._on_generate)
        self.run_btn.pack(side="left")
        ttk.Button(btns, text="Clear Log", command=self._clear_log).pack(side="left", padx=(6, 0))

        self.log = tk.Text(run_panel, height=14, wrap="word")
        self.log.pack(fill="both", expand=True, pady=(8, 0))

    def _browse_predictive(self) -> None:
        assert filedialog is not None
        p = filedialog.askopenfilename(title="Select predictive input file")
        if p:
            self.predictive_var.set(p)

    def _browse_noise(self) -> None:
        assert filedialog is not None
        p = filedialog.askopenfilename(title="Select noise input file")
        if p:
            self.noise_var.set(p)

    def _browse_dataset_out(self) -> None:
        assert filedialog is not None
        p = filedialog.asksaveasfilename(title="Dataset output prefix")
        if p:
            self.dataset_out_var.set(p)

    def _add_model(self) -> None:
        dlg = ModelDialog(self)
        self.wait_window(dlg)
        if dlg.result is not None:
            self.models.append(dlg.result)
            self._refresh_models()

    def _edit_model(self) -> None:
        sel = self.model_tree.selection()
        if not sel:
            return
        idx = int(sel[0])
        dlg = ModelDialog(self, self.models[idx])
        self.wait_window(dlg)
        if dlg.result is not None:
            self.models[idx] = dlg.result
            self._refresh_models()
            self.model_tree.selection_set(str(idx))

    def _remove_model(self) -> None:
        sel = self.model_tree.selection()
        if not sel:
            return
        idx = int(sel[0])
        del self.models[idx]
        self._refresh_models()

    def _add_input_model(self) -> None:
        dlg = InputModelDialog(self)
        self.wait_window(dlg)
        if dlg.result is not None:
            self.input_models.append(dlg.result)
            self._refresh_input_models()

    def _edit_input_model(self) -> None:
        sel = self.input_model_tree.selection()
        if not sel:
            return
        idx = int(sel[0])
        dlg = InputModelDialog(self, self.input_models[idx])
        self.wait_window(dlg)
        if dlg.result is not None:
            self.input_models[idx] = dlg.result
            self._refresh_input_models()
            self.input_model_tree.selection_set(str(idx))

    def _remove_input_model(self) -> None:
        sel = self.input_model_tree.selection()
        if not sel:
            return
        idx = int(sel[0])
        del self.input_models[idx]
        self._refresh_input_models()

    def _refresh_models(self) -> None:
        for item in self.model_tree.get_children():
            self.model_tree.delete(item)
        for i, m in enumerate(self.models):
            self.model_tree.insert(
                "",
                "end",
                iid=str(i),
                values=(
                    m.heritability,
                    "" if m.case_proportion is None else m.case_proportion,
                    ",".join(str(x) for x in m.mafs),
                    m.weight,
                    m.output_prefix,
                    "yes" if m.use_odds_ratio else "no",
                ),
            )

    def _refresh_input_models(self) -> None:
        for item in self.input_model_tree.get_children():
            self.input_model_tree.delete(item)
        for i, m in enumerate(self.input_models):
            self.input_model_tree.insert("", "end", iid=str(i), values=(m.path, m.weight))

    def _update_endpoint_ui(self) -> None:
        is_binary = self.endpoint_var.get() == "binary"
        if is_binary:
            self.binary_frame.grid()
            self.cont_frame.grid_remove()
        else:
            self.binary_frame.grid_remove()
            self.cont_frame.grid()

    def _update_mixed_ui(self) -> None:
        if self.mixed_var.get() == "heterogeneous":
            self.hetero_label_cb.state(["!disabled"])
        else:
            self.hetero_label_var.set(False)
            self.hetero_label_cb.state(["disabled"])

    def _clear_log(self) -> None:
        self.log.delete("1.0", "end")

    def _append_log(self, text: str) -> None:
        self.log.insert("end", text)
        self.log.see("end")

    def _poll_logs(self) -> None:
        while True:
            try:
                msg = self.log_queue.get_nowait()
            except queue.Empty:
                break
            self._append_log(msg)
        self.after(100, self._poll_logs)

    def _build_args(self) -> List[str]:
        args: List[str] = []

        for m in self.models:
            args += ["-M", m.to_blob()]

        for im in self.input_models:
            args += ["-i", im.path]

        weights: List[float] = [m.weight for m in self.models] + [im.weight for im in self.input_models]
        for w in weights:
            args += ["-w", str(w)]

        q = self.quantiles_var.get().strip()
        p = self.population_var.get().strip()
        t = self.try_count_var.get().strip()
        s = self.seed_var.get().strip()

        if q:
            args += ["-q", q]
        if p:
            args += ["-p", p]
        if t:
            args += ["-t", t]
        if s:
            args += ["-r", s]

        pred = self.predictive_var.get().strip()
        noise = self.noise_var.get().strip()
        if pred:
            args += ["-v", pred]
        if noise:
            args += ["-z", noise]

        d_tokens: List[str] = [
            "-n",
            self.af_min_var.get().strip(),
            "-x",
            self.af_max_var.get().strip(),
            "-a",
            self.attr_count_var.get().strip(),
            "-r",
            self.repl_var.get().strip(),
            "-o",
            self.dataset_out_var.get().strip(),
            "-h",
            self.mixed_var.get().strip(),
        ]

        if self.endpoint_var.get() == "continuous":
            d_tokens += ["-c", "-d", self.std_var.get().strip(), "-t", self.total_var.get().strip()]
        else:
            d_tokens += ["-s", self.case_var.get().strip(), "-w", self.control_var.get().strip()]

        if self.mixed_var.get() == "heterogeneous" and bool(self.hetero_label_var.get()):
            d_tokens.append("-b")

        args += ["-D", shlex.join(d_tokens)]
        return args

    def _on_generate(self) -> None:
        assert messagebox is not None
        if self._running:
            return

        try:
            args = self._build_args()
            doc = SnpGenDocument()
            doc.parse_arguments(args)
        except Exception as exc:
            messagebox.showerror("Invalid configuration", str(exc), parent=self)
            return

        self._running = True
        self.run_btn.state(["disabled"])
        self._append_log("Starting generation...\n")

        def worker() -> None:
            writer = _QueueWriter(self.log_queue)
            try:
                with contextlib.redirect_stdout(writer), contextlib.redirect_stderr(writer):
                    run_document(doc)
                self.log_queue.put("\nGeneration complete.\n")
            except Exception as exc:
                self.log_queue.put(f"\nERROR: {exc}\n")
            finally:
                self.after(0, self._finish_run)

        threading.Thread(target=worker, daemon=True).start()

    def _finish_run(self) -> None:
        self._running = False
        self.run_btn.state(["!disabled"])


def launch_gui() -> None:
    if tk is None or ttk is None:
        raise RuntimeError("Tkinter is not available in this Python environment")
    app = GametesGui()
    app.mainloop()
