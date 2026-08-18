from __future__ import annotations

import contextlib
import copy
import json
import math
import queue
import shlex
import sys
import threading
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable, Dict, List, Optional, Sequence, Union

from .cli import run_document
from .document import DocDataset, DocModel, MixedModelDatasetType, SnpGenDocument
from .penetrance_table import PenetranceTable
from .simulator import SnpGenSimulator


try:
    import tkinter as tk
    from tkinter import filedialog, messagebox, ttk
except Exception:  # pragma: no cover - handled by launch_gui
    tk = None
    filedialog = None
    messagebox = None
    ttk = None


if tk is not None:
    _TkRootBase = tk.Tk
    _TkTopLevelBase = tk.Toplevel
    _TkFrameBase = ttk.Frame
else:
    class _TkRootBase:  # pragma: no cover
        pass

    class _TkTopLevelBase:  # pragma: no cover
        pass

    class _TkFrameBase:  # pragma: no cover
        pass


BACKGROUND = "#ececec"
FIELD = "#ffffff"
TEXT = "#171717"
MUTED = "#5f5f5f"
INCOMPATIBLE = "#dedede"
GRID = "#8a8a8a"
FOCUS = "#2675d8"
HIGH_ORDER_WARNING_THRESHOLD = 8
ATTRIBUTE_COUNT_SPINBOX_MAX = 2_147_483_647

JAVA_MODEL_COLUMNS = (
    "Model",
    "# Attributes",
    "Heritability",
    "SNPs",
    "Minor allele freq",
    "Heterogeneity proportion",
    "# Quantiles",
    "Selected",
)


def _fmt(value: Optional[float], digits: int = 4) -> str:
    if value is None or math.isnan(value):
        return "--"
    if math.isinf(value):
        return "infinity"
    return f"{value:.{digits}g}"


def _fmt_property(value: Optional[float]) -> str:
    if value is None or math.isnan(value):
        return "-"
    if math.isinf(value):
        return "∞"
    return f"{value:.4g}"


def penetrance_cell_count(attribute_count: int) -> int:
    """Return the number of genotype cells in an n-attribute SNP model."""
    if attribute_count < 1:
        raise ValueError("Attribute count must be positive")
    return 3**attribute_count


def parse_model_heritability(value: str) -> float:
    try:
        parsed = float(value)
    except ValueError:
        raise ValueError("Heritability must be a decimal greater than 0 and at most 1 (for example, 0.2)") from None
    if not math.isfinite(parsed) or not 0.0 < parsed <= 1.0:
        raise ValueError("Heritability must be greater than 0 and at most 1 (for example, 0.2)")
    return parsed


def parse_model_prevalence(value: str) -> float:
    try:
        parsed = float(value)
    except ValueError:
        raise ValueError("Prevalence must be a decimal greater than 0 and less than 1 (for example, 0.5)") from None
    if not math.isfinite(parsed) or not 0.0 < parsed < 1.0:
        raise ValueError("Prevalence must be greater than 0 and less than 1; values of 0 and 1 are not valid")
    return parsed


def normalized_model_weights(models: Sequence["ModelSpec"]) -> List[float]:
    selected_weights = [model.weight if model.selected else 0.0 for model in models]
    total = sum(selected_weights)
    if total <= 0.0:
        return [0.0 for _ in models]
    return [weight / total for weight in selected_weights]


def common_selected_quantile_count(models: Sequence["ModelSpec"]) -> Optional[int]:
    counts = {model.quantile_count for model in models if model.selected}
    return next(iter(counts)) if len(counts) == 1 else None


def model_can_be_selected(models: Sequence["ModelSpec"], index: int) -> bool:
    model = models[index]
    if model.selected:
        return True
    selected_counts = {other.quantile_count for other in models if other.selected}
    return not selected_counts or selected_counts == {model.quantile_count}


def selected_model_can_be_edited(models: Sequence["ModelSpec"]) -> bool:
    selected = [model for model in models if model.selected]
    if len(selected) != 1:
        return False
    model = selected[0]
    return bool(model.tables) and model.order in (2, 3) and model.quantile_count == 1


def standardize_selected_quantiles(models: Sequence["ModelSpec"]) -> Optional[int]:
    """Keep the first selected quantile group and deselect incompatible models."""
    target: Optional[int] = None
    for model in models:
        if not model.selected:
            continue
        if target is None:
            target = model.quantile_count
        elif model.quantile_count != target:
            model.selected = False
    return target


def normalize_model_output_prefix(path: Union[str, Path]) -> Path:
    output = Path(path)
    name = output.name
    if name.lower().endswith(".txt"):
        name = name[:-4]
    if name.lower().endswith("_models"):
        name = name[:-7]
    if not name:
        raise ValueError("Model output prefix is required")
    return output.with_name(name)


def model_tables_path(path: Union[str, Path]) -> Path:
    prefix = normalize_model_output_prefix(path)
    return prefix.with_name(f"{prefix.name}_Models.txt")


def clean_model_name(path: Union[str, Path]) -> str:
    return normalize_model_output_prefix(path).name


def build_custom_table(
    order: int,
    mafs: Sequence[float],
    values: Sequence[float],
    attribute_names: Optional[Sequence[str]] = None,
) -> PenetranceTable:
    if order not in (2, 3):
        raise ValueError("Custom models must be 2-locus or 3-locus")
    if len(mafs) != order:
        raise ValueError(f"A {order}-locus model requires {order} minor allele frequencies")
    if any(maf < 0.0 or maf > 0.5 for maf in mafs):
        raise ValueError("Minor allele frequencies must be between 0 and 0.5")
    if len(values) != 3**order:
        raise ValueError(f"A {order}-locus model requires {3**order} penetrance values")
    if any(not math.isfinite(value) or value < 0.0 or value > 1.0 for value in values):
        raise ValueError("Penetrance values must be between 0 and 1")

    names = list(attribute_names) if attribute_names is not None else [f"P{i + 1}" for i in range(order)]
    if len(names) != order or any(not name.strip() for name in names) or len(set(names)) != order:
        raise ValueError("Feature names must be non-empty and unique")

    table = PenetranceTable(3, order)
    table.set_attribute_names(names)
    table.set_minor_allele_frequencies(list(mafs))
    for cell, value in zip(table.cells, values):
        cell.value = float(value)
        cell.is_set = True

    # Java's direct editor saves user-entered tables as unnormalized models.
    table.normalized = False
    table.calc_and_set_prevalence()
    table.calc_and_set_heritability()
    table.calc_and_set_edm()
    table.calc_and_set_odds_ratio()
    table.check_row_sums()
    return table


def _focus_editable_widget(event: object) -> None:
    try:
        event.widget.focus_force()
    except Exception:
        pass


def _make_text_entry(parent: object, textvariable: object, width: int, justify: str = "left") -> object:
    entry = tk.Entry(
        parent,
        textvariable=textvariable,
        width=width,
        justify=justify,
        background=FIELD,
        foreground=TEXT,
        disabledbackground="#e1e1e1",
        disabledforeground=MUTED,
        insertbackground=FOCUS,
        insertwidth=2,
        insertontime=600,
        insertofftime=300,
        selectbackground=FOCUS,
        selectforeground="#ffffff",
        relief="solid",
        borderwidth=1,
        highlightthickness=2,
        highlightbackground=GRID,
        highlightcolor=FOCUS,
        takefocus=True,
    )
    entry.bind("<Button-1>", _focus_editable_widget, add="+")
    return entry


def _activate_window(window: object, focus_widget: Optional[object] = None) -> None:
    try:
        window.update_idletasks()
        window.lift()
        window.focus_force()
        if focus_widget is not None:
            focus_widget.focus_force()
            focus_widget.icursor("end")
    except Exception:
        pass


@dataclass
class ModelSpec:
    name: str
    source: str
    heritability: Optional[float]
    prevalence: Optional[float]
    mafs: List[float]
    feature_names: List[str] = field(default_factory=list)
    output_prefix: str = ""
    use_odds_ratio: bool = False
    weight: float = 1.0
    selected: bool = False
    tables: List[PenetranceTable] = field(default_factory=list)
    population_scores: List[float] = field(default_factory=list)
    input_path: str = ""
    requested_quantiles: int = 1
    population_count: int = 1000
    try_count: int = 100000

    def __post_init__(self) -> None:
        if not self.feature_names:
            if self.tables:
                self.feature_names = self.tables[0].get_attribute_names()
            else:
                self.feature_names = [f"P{i + 1}" for i in range(len(self.mafs))]

    @property
    def order(self) -> int:
        return len(self.mafs)

    @property
    def quantile_count(self) -> int:
        return len(self.tables) if self.tables else self.requested_quantiles

    @property
    def attribute_names(self) -> List[str]:
        return list(self.feature_names)

    def to_blob(self) -> str:
        tokens = ["-h", str(self.heritability)]
        if self.prevalence is not None:
            tokens += ["-p", str(self.prevalence)]
        if self.use_odds_ratio:
            tokens.append("-d")
        for maf in self.mafs:
            tokens += ["-a", str(maf)]
        tokens += ["-o", self.output_prefix]
        return shlex.join(tokens)

    def to_doc_model(self) -> DocModel:
        model = DocModel(attribute_count=self.order, model_id=self.name)
        model.attribute_name_array = self.attribute_names
        model.attribute_allele_frequency_array = list(self.mafs)
        model.heritability = self.heritability
        model.prevalence = self.prevalence
        model.fraction = self.weight
        model.use_odds_ratio = self.use_odds_ratio
        model.file = None if self.source == "loaded" else Path(self.output_prefix)
        if self.tables:
            model.set_penetrance_tables(copy.deepcopy(self.tables))
        return model


def load_model_spec(path: Union[str, Path]) -> ModelSpec:
    input_path = Path(path)
    tables = SnpGenSimulator().fetch_tables(input_path)
    if not tables:
        raise ValueError("The model file contains no penetrance tables")
    first = tables[0]
    return ModelSpec(
        name=clean_model_name(input_path),
        source="loaded",
        heritability=first.actual_heritability,
        prevalence=first.prevalence,
        mafs=first.get_minor_allele_frequencies(),
        feature_names=first.get_attribute_names(),
        output_prefix=str(normalize_model_output_prefix(input_path)),
        input_path=str(input_path),
        tables=tables,
        requested_quantiles=len(tables),
    )


def save_model_spec(model: ModelSpec, output_prefix: Optional[Union[str, Path]] = None) -> Path:
    if not model.tables:
        raise ValueError("Generate the model before saving it")
    prefix = normalize_model_output_prefix(output_prefix or model.output_prefix)
    doc_model = model.to_doc_model()
    doc_model.file = prefix
    simulator = SnpGenSimulator()
    if model.population_scores:
        simulator.write_tables_and_scores_to_file(
            [doc_model],
            [list(model.population_scores)],
            model.quantile_count,
        )
    else:
        simulator.write_model_tables(
            doc_model,
            model_tables_path(prefix),
            header=None,
            save_unnormalized=True,
        )
    model.output_prefix = str(prefix)
    model.input_path = str(model_tables_path(prefix))
    return model_tables_path(prefix)


def generate_and_save_model_spec(model: ModelSpec, random_seed: Optional[int] = None) -> ModelSpec:
    if model.source != "generated":
        raise ValueError("Only generated model specifications can be materialized")
    generated = copy.deepcopy(model)
    generated.output_prefix = str(normalize_model_output_prefix(generated.output_prefix))
    document = SnpGenDocument(
        ras_quantile_count=generated.requested_quantiles,
        ras_population_count=generated.population_count,
        ras_try_count=generated.try_count,
        random_seed=random_seed,
    )
    doc_model = generated.to_doc_model()
    document.model_list = [doc_model]
    simulator = SnpGenSimulator()
    simulator.set_document(document)
    scores = simulator.generate_tables_for_one_model(
        model=doc_model,
        desired_quantile_count=generated.requested_quantiles,
        desired_population_count=generated.population_count,
        try_count=generated.try_count,
    )
    generated.tables = copy.deepcopy(doc_model.penetrance_tables)
    generated.population_scores = list(scores)
    generated.heritability = doc_model.heritability
    generated.prevalence = doc_model.prevalence
    save_model_spec(generated)
    return generated


class _WorkerQueueWriter:
    def __init__(self, output_queue: object) -> None:
        self._queue = output_queue

    def write(self, text: str) -> int:
        if text:
            self._queue.put(("log", text))
        return len(text)

    def flush(self) -> None:
        return


def _model_generation_worker(output_queue: object, specification: ModelSpec, random_seed: Optional[int]) -> None:
    writer = _WorkerQueueWriter(output_queue)
    try:
        with contextlib.redirect_stdout(writer), contextlib.redirect_stderr(writer):
            result = generate_and_save_model_spec(specification, random_seed)
        output_queue.put(("model", result))
    except Exception as exc:
        output_queue.put(("error", str(exc)))
    finally:
        output_queue.put(("done", None))


def _dataset_generation_worker(output_queue: object, document: SnpGenDocument) -> None:
    writer = _WorkerQueueWriter(output_queue)
    try:
        with contextlib.redirect_stdout(writer), contextlib.redirect_stderr(writer):
            simulator = SnpGenSimulator()
            simulator.set_document(document)
            simulator.combine_model_tables_into_quantiles(document.model_list, document.model_input_files)
            simulator.generate_datasets()
    except Exception as exc:
        output_queue.put(("error", str(exc)))
    finally:
        output_queue.put(("done", None))


def _responsive_worker_entry(target: object, output_queue: object, *arguments: object) -> None:
    prior_interval = sys.getswitchinterval()
    sys.setswitchinterval(0.001)
    try:
        target(output_queue, *arguments)
    finally:
        sys.setswitchinterval(prior_interval)


class GenerateModelDialog(_TkTopLevelBase):
    def __init__(self, parent: object, first_attribute_number: int = 1) -> None:
        super().__init__(parent)
        self.title("Generate Model")
        self.configure(background=BACKGROUND)
        self.transient(parent)
        self.result: Optional[ModelSpec] = None
        self.first_attribute_number = first_attribute_number
        self._syncing_rows = False

        self.attribute_count_var = tk.StringVar(value="2")
        self.heritability_var = tk.StringVar(value="0.2")
        self.prevalence_enabled_var = tk.BooleanVar(value=False)
        self.prevalence_var = tk.StringVar(value="")
        self.metric_var = tk.StringVar(value="edm")
        self.quantile_count_var = tk.StringVar(value="2")
        self.population_count_var = tk.StringVar(value="1000")
        self.name_vars: List[tk.StringVar] = []
        self.maf_vars: List[tk.StringVar] = []

        body = ttk.Frame(self, padding=7)
        body.pack(fill="both", expand=True)
        parameters = ttk.Frame(body)
        parameters.pack(fill="x")

        top = ttk.Frame(parameters)
        top.pack(fill="x", pady=(0, 5))
        ttk.Label(top, text="Number of attributes").pack(side="left")
        self.attribute_count_entry = ttk.Spinbox(
            top,
            from_=1,
            to=ATTRIBUTE_COUNT_SPINBOX_MAX,
            increment=1,
            width=5,
            textvariable=self.attribute_count_var,
            takefocus=True,
        )
        self.attribute_count_entry.pack(side="left", padx=(6, 24))
        self.attribute_count_entry.bind("<Button-1>", _focus_editable_widget, add="+")
        ttk.Label(top, text="Heritability (0 < h² ≤ 1)").pack(side="left")
        self.heritability_entry = _make_text_entry(top, self.heritability_var, 8)
        self.heritability_entry.pack(side="left", padx=(6, 24))
        ttk.Checkbutton(
            top,
            variable=self.prevalence_enabled_var,
            command=self._toggle_prevalence,
        ).pack(side="left")
        ttk.Label(top, text="Prevalence (0 < K < 1)").pack(side="left", padx=(4, 6))
        self.prevalence_entry = _make_text_entry(top, self.prevalence_var, 8)
        self.prevalence_entry.pack(side="left")

        quantiles = ttk.Frame(parameters)
        quantiles.pack(fill="x", pady=(0, 5))
        ttk.Label(quantiles, text="Quantiles:").pack(side="left")
        ttk.Radiobutton(quantiles, text="EDM", value="edm", variable=self.metric_var).pack(side="left", padx=(6, 4))
        ttk.Radiobutton(quantiles, text="Odds ratio", value="odds", variable=self.metric_var).pack(side="left", padx=(0, 16))
        ttk.Label(quantiles, text="Quantile count").pack(side="left")
        self.quantile_count_entry = _make_text_entry(quantiles, self.quantile_count_var, 10)
        self.quantile_count_entry.pack(side="left", padx=(6, 20))
        ttk.Label(quantiles, text="Quantile population size").pack(side="left")
        self.population_count_entry = _make_text_entry(quantiles, self.population_count_var, 10)
        self.population_count_entry.pack(side="left", padx=(6, 0))

        table_outer = tk.Frame(body, background=GRID, padx=1, pady=1)
        table_outer.pack(fill="both", expand=True)
        self.attribute_canvas = tk.Canvas(
            table_outer,
            background=FIELD,
            highlightthickness=0,
            height=250,
        )
        self.attribute_v_scroll = ttk.Scrollbar(
            table_outer,
            orient="vertical",
            command=self.attribute_canvas.yview,
        )
        self.attribute_h_scroll = ttk.Scrollbar(
            table_outer,
            orient="horizontal",
            command=self.attribute_canvas.xview,
        )
        self.attribute_canvas.configure(
            yscrollcommand=self.attribute_v_scroll.set,
            xscrollcommand=self.attribute_h_scroll.set,
        )
        self.attribute_canvas.grid(row=0, column=0, sticky="nsew")
        self.attribute_v_scroll.grid(row=0, column=1, sticky="ns")
        self.attribute_h_scroll.grid(row=1, column=0, sticky="ew")
        table_outer.columnconfigure(0, weight=1)
        table_outer.rowconfigure(0, weight=1)
        self.attribute_table = tk.Frame(self.attribute_canvas, background=FIELD)
        self.attribute_table_window = self.attribute_canvas.create_window(
            (0, 0),
            window=self.attribute_table,
            anchor="nw",
        )
        self.attribute_table.bind("<Configure>", self._attribute_table_configured)
        self.attribute_canvas.bind("<Configure>", self._attribute_canvas_configured)
        self._sync_attribute_rows(2)

        buttons = ttk.Frame(body)
        buttons.pack(pady=(8, 0))
        ttk.Button(buttons, text="Save", command=self._save).pack(side="left", padx=(0, 5))
        ttk.Button(buttons, text="Cancel", command=self._cancel).pack(side="left")

        self.attribute_count_var.trace_add("write", self._attribute_count_changed)
        self._toggle_prevalence()
        self.bind("<Escape>", lambda _event: self._cancel())
        self.protocol("WM_DELETE_WINDOW", self._cancel)
        self.grab_set()
        self.after_idle(lambda: _activate_window(self, self.attribute_count_entry))

    def _attribute_count_changed(self, *_args: object) -> None:
        try:
            count = int(self.attribute_count_var.get())
        except ValueError:
            return
        if count >= 1:
            self._sync_attribute_rows(count)

    def _attribute_table_configured(self, _event: object) -> None:
        self.attribute_canvas.configure(scrollregion=self.attribute_canvas.bbox("all"))

    def _attribute_canvas_configured(self, event: object) -> None:
        minimum_width = 560
        if event.width > minimum_width:
            self.attribute_canvas.itemconfigure(self.attribute_table_window, width=event.width)

    def _sync_attribute_rows(self, count: int) -> None:
        if self._syncing_rows:
            return
        self._syncing_rows = True
        try:
            old_names = [variable.get() for variable in self.name_vars]
            old_mafs = [variable.get() for variable in self.maf_vars]
            for child in self.attribute_table.winfo_children():
                child.destroy()
            self.name_vars = []
            self.maf_vars = []

            headers = (("SNP", 0), ("Minor allele frequency", 1))
            for text, column in headers:
                tk.Label(
                    self.attribute_table,
                    text=text,
                    background="#e3e3e3",
                    foreground=TEXT,
                    relief="solid",
                    borderwidth=1,
                ).grid(
                    row=0, column=column, sticky="nsew"
                )
            self.attribute_table.columnconfigure(0, weight=1, minsize=280)
            self.attribute_table.columnconfigure(1, weight=1, minsize=280)
            for index in range(count):
                name = old_names[index] if index < len(old_names) else f"P{self.first_attribute_number + index}"
                maf = old_mafs[index] if index < len(old_mafs) else "0.2"
                name_var = tk.StringVar(value=name)
                maf_var = tk.StringVar(value=maf)
                self.name_vars.append(name_var)
                self.maf_vars.append(maf_var)
                _make_text_entry(self.attribute_table, name_var, 20).grid(row=index + 1, column=0, sticky="nsew")
                _make_text_entry(self.attribute_table, maf_var, 20).grid(row=index + 1, column=1, sticky="nsew")
        finally:
            self._syncing_rows = False

    def _toggle_prevalence(self) -> None:
        self.prevalence_entry.configure(state="normal" if self.prevalence_enabled_var.get() else "disabled")
        if self.prevalence_enabled_var.get():
            self.prevalence_entry.focus_force()
            self.prevalence_entry.icursor("end")

    def _save(self) -> None:
        assert messagebox is not None
        try:
            count = int(self.attribute_count_var.get())
            if count != len(self.name_vars):
                raise ValueError("Number of attributes is invalid")
            heritability = parse_model_heritability(self.heritability_var.get())
            prevalence = None
            if self.prevalence_enabled_var.get():
                prevalence = parse_model_prevalence(self.prevalence_var.get())
            names = [variable.get().strip() for variable in self.name_vars]
            if any(not name for name in names) or len(set(names)) != len(names):
                raise ValueError("SNP names must be non-empty and unique")
            mafs = [float(variable.get()) for variable in self.maf_vars]
            if any(not 0.0 < maf <= 0.5 for maf in mafs):
                raise ValueError("Minor allele frequencies must be greater than 0 and at most 0.5")
            quantiles = int(self.quantile_count_var.get())
            population = int(self.population_count_var.get())
            if quantiles <= 0 or population < quantiles:
                raise ValueError("Quantile count must be positive and no larger than the population size")
        except Exception as exc:
            messagebox.showerror("Invalid model", str(exc), parent=self)
            return

        if count >= HIGH_ORDER_WARNING_THRESHOLD:
            cells = penetrance_cell_count(count)
            proceed = messagebox.askyesno(
                "High-order model",
                f"A {count}-attribute model contains {cells:,} penetrance cells per table.\n\n"
                "Generation time and memory use grow exponentially, especially with a large "
                "quantile population. Continue?",
                parent=self,
            )
            if not proceed:
                return

        self.result = ModelSpec(
            name="Model",
            source="generated",
            heritability=heritability,
            prevalence=prevalence,
            mafs=mafs,
            feature_names=names,
            use_odds_ratio=self.metric_var.get() == "odds",
            requested_quantiles=quantiles,
            population_count=population,
            try_count=min(max(population * 100, 100000), 2_147_483_647),
        )
        self.destroy()

    def _cancel(self) -> None:
        self.result = None
        self.destroy()


class CustomModelDialog(_TkTopLevelBase):
    GENOTYPES = (("AA", "Aa", "aa"), ("BB", "Bb", "bb"), ("CC", "Cc", "cc"))

    def __init__(self, parent: object, model: ModelSpec, creating: bool = False) -> None:
        super().__init__(parent)
        self.title("Create Model" if creating else "Edit Model")
        self.configure(background=BACKGROUND)
        self.transient(parent)
        self.result: Optional[ModelSpec] = None
        self.original = copy.deepcopy(model)
        self.order_var = tk.IntVar(value=model.order)
        self.feature_names = list(model.attribute_names)
        while len(self.feature_names) < 3:
            self.feature_names.append(f"P{len(self.feature_names) + 1}")
        self.maf_vars = [tk.StringVar(value=str(value)) for value in model.mafs]
        while len(self.maf_vars) < 3:
            self.maf_vars.append(tk.StringVar(value="0.2"))
        initial_values = [cell.value for cell in model.tables[0].cells] if model.tables else [0.0] * (3**model.order)
        self._preserved_values = list(initial_values)
        self.cell_vars: List[tk.StringVar] = []
        self._stats_refresh_job: Optional[str] = None
        self.property_vars: Dict[str, tk.StringVar] = {
            "heritability": tk.StringVar(value="-"),
            "prevalence": tk.StringVar(value="-"),
            "edm": tk.StringVar(value="-"),
            "odds": tk.StringVar(value="-"),
        }
        self.marginal_var = tk.StringVar(value="")
        for variable in self.maf_vars:
            variable.trace_add("write", self._schedule_stats_refresh)

        body = ttk.Frame(self, padding=7)
        body.pack(fill="both", expand=True)
        header = ttk.Frame(body)
        header.pack()
        ttk.Label(header, text="Model Order:").pack(side="left")
        ttk.Radiobutton(header, text="2-locus", value=2, variable=self.order_var, command=self._change_order).pack(side="left", padx=(5, 0))
        ttk.Radiobutton(header, text="3-locus", value=3, variable=self.order_var, command=self._change_order).pack(side="left", padx=(5, 0))
        ttk.Label(
            body,
            text="Enter decimal penetrances from 0 to 1. Calculated properties update as you type.",
            foreground=MUTED,
        ).pack(pady=(5, 0))

        content = ttk.Frame(body)
        content.pack(fill="both", expand=True, pady=(7, 0))
        self.table_host = ttk.Frame(content)
        self.table_host.grid(row=0, column=0, sticky="nsew", padx=(0, 15))
        side = ttk.Frame(content)
        side.grid(row=0, column=1, sticky="nsew")
        self.maf_host = ttk.Frame(side, padding=10, relief="solid")
        self.maf_host.pack(fill="x")
        ttk.Label(self.maf_host, text="Minor-Allele Frequencies:", font=("TkDefaultFont", 10, "bold")).grid(
            row=0, column=0, columnspan=2, pady=(0, 7)
        )
        self.maf_rows = ttk.Frame(self.maf_host)
        self.maf_rows.grid(row=1, column=0, columnspan=2)

        properties = ttk.Frame(side, padding=10, relief="solid")
        properties.pack(fill="x", pady=(15, 0))
        for row, (key, label) in enumerate(
            (("heritability", "Heritability (calculated):"), ("prevalence", "Prevalence (calculated):"), ("edm", "EDM:"), ("odds", "COR:"))
        ):
            ttk.Label(properties, text=label).grid(row=row, column=0, sticky="e", pady=3)
            ttk.Label(properties, textvariable=self.property_vars[key], font=("TkDefaultFont", 10, "bold")).grid(
                row=row, column=1, sticky="w", padx=(5, 0), pady=3
            )

        marginals = ttk.Frame(side, padding=10, relief="solid")
        marginals.pack(fill="both", expand=True, pady=(15, 0))
        ttk.Label(marginals, text="Marginal Penetrances:", font=("TkDefaultFont", 10, "bold")).pack(pady=(0, 7))
        ttk.Label(marginals, textvariable=self.marginal_var, justify="left").pack(anchor="w")

        buttons = ttk.Frame(body)
        buttons.pack(pady=(8, 0))
        ttk.Button(buttons, text="Save", command=self._save).pack(side="left")
        ttk.Button(buttons, text="Clear", command=self._clear).pack(side="left", padx=5)
        ttk.Button(buttons, text="Cancel", command=self._cancel).pack(side="left")

        content.columnconfigure(0, weight=3)
        content.columnconfigure(1, weight=2)
        content.rowconfigure(0, weight=1)
        self._build_editor(initial_values)
        self.bind("<Escape>", lambda _event: self._cancel())
        self.protocol("WM_DELETE_WINDOW", self._cancel)
        self.grab_set()
        self.after_idle(lambda: _activate_window(self, self.first_cell_entry))

    def _change_order(self) -> None:
        self._preserved_values = self._current_values(fallback=True)
        self._build_editor(self._preserved_values)

    def _build_editor(self, values: Sequence[float]) -> None:
        for child in self.table_host.winfo_children():
            child.destroy()
        for child in self.maf_rows.winfo_children():
            child.destroy()

        order = self.order_var.get()
        self.cell_vars = []
        self.first_cell_entry = None
        panels = 3 if order == 3 else 1
        for panel_index in range(panels):
            panel = ttk.Frame(self.table_host)
            panel.grid(row=panel_index, column=0, sticky="w", pady=(0, 14 if panel_index < panels - 1 else 0))
            if order == 3:
                ttk.Label(panel, text=f"{self.feature_names[2]}  {self.GENOTYPES[2][panel_index]}", font=("TkDefaultFont", 10, "bold")).grid(
                    row=0, column=0, rowspan=2, padx=(0, 8)
                )
            ttk.Label(panel, text=self.feature_names[0], font=("TkDefaultFont", 10, "bold")).grid(row=0, column=2, columnspan=3)
            for column, genotype in enumerate(self.GENOTYPES[0]):
                ttk.Label(panel, text=genotype).grid(row=1, column=column + 2, padx=4)
            ttk.Label(panel, text=self.feature_names[1], font=("TkDefaultFont", 10, "bold")).grid(row=2, column=0, rowspan=3, padx=(0, 6))
            for row, genotype in enumerate(self.GENOTYPES[1]):
                ttk.Label(panel, text=genotype).grid(row=row + 2, column=1, sticky="e", padx=(0, 4))
                for column in range(3):
                    index = column + (3 * row) + (9 * panel_index)
                    value = values[index] if index < len(values) else 0.0
                    variable = tk.StringVar(value=f"{float(value):.7g}")
                    self.cell_vars.append(variable)
                    variable.trace_add("write", self._schedule_stats_refresh)
                    entry = _make_text_entry(panel, variable, 7, justify="center")
                    entry.grid(row=row + 2, column=column + 2, padx=2, pady=2)
                    entry.bind("<Return>", self._refresh_stats)
                    if self.first_cell_entry is None:
                        self.first_cell_entry = entry

        for index in range(order):
            ttk.Label(self.maf_rows, text=f"MAF {self.feature_names[index]}:").grid(row=index, column=0, sticky="e", pady=3)
            entry = _make_text_entry(self.maf_rows, self.maf_vars[index], 8)
            entry.grid(row=index, column=1, sticky="w", padx=(7, 0), pady=3)
            entry.bind("<Return>", self._refresh_stats)
        self._refresh_stats()

    def _schedule_stats_refresh(self, *_args: object) -> None:
        if self._stats_refresh_job is not None:
            try:
                self.after_cancel(self._stats_refresh_job)
            except Exception:
                pass
        self._stats_refresh_job = self.after(60, self._run_scheduled_stats_refresh)

    def _run_scheduled_stats_refresh(self) -> None:
        self._stats_refresh_job = None
        self._refresh_stats()

    def _current_values(self, fallback: bool = False) -> List[float]:
        values: List[float] = []
        for variable in self.cell_vars:
            try:
                values.append(float(variable.get()))
            except ValueError:
                values.append(0.0 if fallback else float("nan"))
        return values

    def _table_from_ui(self) -> PenetranceTable:
        order = self.order_var.get()
        mafs = [float(self.maf_vars[index].get()) for index in range(order)]
        return build_custom_table(order, mafs, self._current_values(), self.feature_names[:order])

    def _refresh_stats(self, _event: object = None) -> None:
        try:
            table = self._table_from_ui()
        except Exception:
            for variable in self.property_vars.values():
                variable.set("-")
            self.marginal_var.set("")
            return
        self.property_vars["heritability"].set(_fmt_property(table.actual_heritability))
        self.property_vars["prevalence"].set(_fmt_property(table.prevalence))
        self.property_vars["edm"].set(_fmt_property(table.edm))
        self.property_vars["odds"].set(_fmt_property(table.odds_ratio))
        lines = []
        for index, values in enumerate(table.calc_marginal_prevalences()):
            rendered = "   ".join(f"{name} {_fmt_property(value)}" for name, value in zip(self.GENOTYPES[index], values))
            lines.append(f"{self.feature_names[index]}  {rendered}")
        self.marginal_var.set("\n\n".join(lines))

    def _clear(self) -> None:
        for variable in self.cell_vars:
            variable.set("0")
        for index in range(self.order_var.get()):
            self.maf_vars[index].set("0")
        self._refresh_stats()

    def _save(self) -> None:
        assert messagebox is not None
        try:
            table = self._table_from_ui()
        except Exception as exc:
            messagebox.showerror("Invalid model", str(exc), parent=self)
            return
        order = self.order_var.get()
        self.result = ModelSpec(
            name=self.original.name,
            source="custom",
            heritability=table.actual_heritability,
            prevalence=table.prevalence,
            mafs=[float(self.maf_vars[index].get()) for index in range(order)],
            feature_names=self.feature_names[:order],
            output_prefix=self.original.output_prefix,
            weight=self.original.weight,
            selected=self.original.selected,
            tables=[table],
            requested_quantiles=1,
            population_count=1,
            try_count=1,
        )
        self.destroy()

    def _cancel(self) -> None:
        self.result = None
        self.destroy()


class ModelTableView(_TkFrameBase):
    WIDTHS = (125, 82, 88, 120, 130, 145, 88, 78)

    def __init__(
        self,
        parent: object,
        on_selection: Callable[[int, bool], None],
        on_weight: Callable[[int, float], None],
    ) -> None:
        super().__init__(parent)
        self.on_selection = on_selection
        self.on_weight = on_weight
        self.models: Sequence[ModelSpec] = []
        self.running = False
        self.row_widgets: List[List[object]] = []
        self.selected_vars: List[object] = []
        self.checkboxes: List[object] = []
        self.weight_entries: List[object] = []
        self.row_compatible: List[bool] = []
        self.row_running: List[bool] = []
        self.canvas = tk.Canvas(self, background=FIELD, highlightthickness=1, highlightbackground=GRID, height=260)
        self.v_scroll = ttk.Scrollbar(self, orient="vertical", command=self.canvas.yview)
        self.h_scroll = ttk.Scrollbar(self, orient="horizontal", command=self.canvas.xview)
        self.canvas.configure(yscrollcommand=self.v_scroll.set, xscrollcommand=self.h_scroll.set)
        self.canvas.grid(row=0, column=0, sticky="nsew")
        self.v_scroll.grid(row=0, column=1, sticky="ns")
        self.h_scroll.grid(row=1, column=0, sticky="ew")
        self.columnconfigure(0, weight=1)
        self.rowconfigure(0, weight=1)
        self.body = tk.Frame(self.canvas, background=FIELD)
        self.body_window = self.canvas.create_window((0, 0), window=self.body, anchor="nw")
        self.body.bind("<Configure>", self._body_configured)
        self.canvas.bind("<Configure>", self._canvas_configured)

    def _body_configured(self, _event: object) -> None:
        self.canvas.configure(scrollregion=self.canvas.bbox("all"))

    def _canvas_configured(self, event: object) -> None:
        required = sum(self.WIDTHS)
        if event.width > required:
            self.canvas.itemconfigure(self.body_window, width=event.width)

    def _cell(self, row: int, column: int, height: int, background: str) -> tk.Frame:
        cell = tk.Frame(
            self.body,
            width=self.WIDTHS[column],
            height=height,
            background=background,
            highlightbackground=GRID,
            highlightthickness=1,
        )
        cell.grid(row=row, column=column, sticky="nsew")
        cell.grid_propagate(False)
        return cell

    def rebuild(self, models: Sequence[ModelSpec], running: bool = False) -> None:
        self.models = models
        self.running = running
        self.row_widgets = []
        self.selected_vars = []
        self.checkboxes = []
        self.weight_entries = []
        self.row_compatible = []
        self.row_running = []
        for child in self.body.winfo_children():
            child.destroy()
        for column, heading in enumerate(JAVA_MODEL_COLUMNS):
            cell = self._cell(0, column, 26, "#e4e4e4")
            tk.Label(
                cell,
                text=heading,
                background="#e4e4e4",
                foreground=TEXT,
                anchor="center",
            ).pack(fill="both", expand=True)

        for index, model in enumerate(models):
            compatible = model_can_be_selected(models, index)
            background = FIELD if compatible else INCOMPATIBLE
            height = max(28, 23 * model.order)
            row_widgets: List[object] = []
            values = (model.name, str(model.order), _fmt(model.heritability))
            for column, value in enumerate(values):
                cell = self._cell(index + 1, column, height, background)
                label = tk.Label(
                    cell,
                    text=value,
                    background=background,
                    foreground=TEXT,
                    anchor="w",
                    padx=4,
                )
                label.pack(fill="both", expand=True)
                row_widgets.extend((cell, label))

            snp_cell = self._cell(index + 1, 3, height, background)
            snp_label = tk.Label(
                snp_cell,
                text="\n".join(model.attribute_names),
                background=background,
                foreground=TEXT,
                anchor="w",
                justify="left",
                padx=4,
            )
            snp_label.pack(
                fill="both", expand=True
            )
            maf_cell = self._cell(index + 1, 4, height, background)
            maf_label = tk.Label(
                maf_cell,
                text="\n".join(_fmt(maf) for maf in model.mafs),
                background=background,
                foreground=TEXT,
                anchor="w",
                justify="left",
                padx=4,
            )
            maf_label.pack(
                fill="both", expand=True
            )
            weight_cell = self._cell(index + 1, 5, height, background)
            weight_var = tk.StringVar(value=str(model.weight))
            weight_entry = tk.Entry(
                weight_cell,
                textvariable=weight_var,
                relief="flat",
                background=background,
                foreground=TEXT,
                disabledforeground=MUTED,
                insertbackground=TEXT,
                justify="center",
            )
            weight_entry.pack(fill="both", expand=True, padx=3, pady=max(2, (height - 24) // 2))
            if running:
                weight_entry.configure(state="disabled")
            weight_entry.bind("<Return>", lambda _event, i=index, v=weight_var: self._commit_weight(i, v))
            weight_entry.bind("<FocusOut>", lambda _event, i=index, v=weight_var: self._commit_weight(i, v))

            q_cell = self._cell(index + 1, 6, height, background)
            q_label = tk.Label(
                q_cell,
                text=str(model.quantile_count),
                background=background,
                foreground=TEXT,
            )
            q_label.pack(fill="both", expand=True)
            selected_cell = self._cell(index + 1, 7, height, background)
            selected_var = tk.BooleanVar(value=model.selected)
            checkbox = tk.Checkbutton(
                selected_cell,
                variable=selected_var,
                background=background,
                foreground=TEXT,
                activebackground=background,
                activeforeground=TEXT,
                selectcolor=background,
                command=lambda i=index, v=selected_var: self.on_selection(i, bool(v.get())),
            )
            checkbox.pack(expand=True)
            if running or not compatible:
                checkbox.configure(state="disabled", disabledforeground=MUTED)
            row_widgets.extend(
                (
                    snp_cell,
                    snp_label,
                    maf_cell,
                    maf_label,
                    weight_cell,
                    weight_entry,
                    q_cell,
                    q_label,
                    selected_cell,
                    checkbox,
                )
            )
            self.row_widgets.append(row_widgets)
            self.selected_vars.append(selected_var)
            self.checkboxes.append(checkbox)
            self.weight_entries.append(weight_entry)
            self.row_compatible.append(compatible)
            self.row_running.append(running)

        for column, width in enumerate(self.WIDTHS):
            self.body.grid_columnconfigure(column, minsize=width)

    def refresh_interaction_state(self, models: Sequence[ModelSpec], running: bool = False) -> None:
        """Update selection gating without rebuilding widgets under the pointer."""
        self.models = models
        self.running = running
        if len(models) != len(self.row_widgets):
            self.rebuild(models, running)
            return
        for index, model in enumerate(models):
            compatible = model_can_be_selected(models, index)
            background = FIELD if compatible else INCOMPATIBLE
            compatibility_changed = compatible != self.row_compatible[index]
            running_changed = running != self.row_running[index]
            if bool(self.selected_vars[index].get()) != model.selected:
                self.selected_vars[index].set(model.selected)
            if compatibility_changed:
                for widget in self.row_widgets[index]:
                    try:
                        widget.configure(background=background)
                    except Exception:
                        pass
                self.row_compatible[index] = compatible
            if running_changed:
                self.weight_entries[index].configure(state="disabled" if running else "normal")
                self.row_running[index] = running
            if compatibility_changed or running_changed:
                self.checkboxes[index].configure(
                    state="disabled" if running or not compatible else "normal",
                    disabledforeground=MUTED,
                    background=background,
                    activebackground=background,
                    selectcolor=background,
                )

    def _commit_weight(self, index: int, variable: object) -> None:
        try:
            value = float(variable.get())
            if value <= 0.0:
                raise ValueError
        except ValueError:
            variable.set(str(self.models[index].weight))
            return
        if value != self.models[index].weight:
            self.on_weight(index, value)


class GametesGui(_TkRootBase):
    def __init__(self) -> None:
        super().__init__()
        self.title("GAMETES 2.2 dev")
        self.configure(background=BACKGROUND)
        screen_width = self.winfo_screenwidth()
        screen_height = self.winfo_screenheight()
        width = min(720, max(680, screen_width - 60))
        height = min(900, max(720, screen_height - 80))
        self.geometry(f"{width}x{height}")
        self.minsize(680, 720)

        self.models: List[ModelSpec] = []
        self.next_model_number = 1
        self.configuration_file: Optional[Path] = None
        self.noise_file_path = ""
        self.noise_file_attributes = 0
        self.noise_file_instances = 0
        self._updating_counts = False
        self._running = False
        self._run_kind = "dataset"
        self._worker_done = False
        self._worker_error: Optional[str] = None
        self._worker_model_result: Optional[ModelSpec] = None
        self._worker_queue: "queue.Queue[object]" = queue.Queue()
        self._worker_thread: Optional[threading.Thread] = None
        self._progress_window: Optional[object] = None
        self._progress_bar: Optional[object] = None

        self._configure_style()
        self._build_menu()
        self._build_ui()
        self.protocol("WM_DELETE_WINDOW", self._close_application)
        self.after(50, self._poll_worker)
        self.after(80, lambda: _activate_window(self))

    def _configure_style(self) -> None:
        style = ttk.Style(self)
        try:
            style.theme_use("clam")
        except Exception:
            pass
        style.configure(".", background=BACKGROUND, foreground=TEXT)
        style.configure("TFrame", background=BACKGROUND)
        style.configure("TLabel", background=BACKGROUND, foreground=TEXT)
        style.configure("TLabelframe", background=BACKGROUND, foreground=TEXT)
        style.configure("TLabelframe.Label", background=BACKGROUND, foreground=TEXT)
        style.configure("TButton", padding=(9, 4))

    def _build_menu(self) -> None:
        menu_bar = tk.Menu(self)
        file_menu = tk.Menu(menu_bar, tearoff=False)
        file_menu.add_command(label="New", command=self._new_configuration, accelerator="Cmd+N")
        file_menu.add_command(label="Open", command=self._open_configuration, accelerator="Cmd+O")
        file_menu.add_command(label="Save", command=self._save_configuration, accelerator="Cmd+S")
        file_menu.add_command(label="SaveAs", command=self._save_configuration_as, accelerator="Shift+Cmd+S")
        file_menu.add_separator()
        file_menu.add_command(label="Exit", command=self._close_application)
        menu_bar.add_cascade(label="File", menu=file_menu)
        self.configure(menu=menu_bar)
        self.bind_all("<Command-n>", lambda _event: self._new_configuration())
        self.bind_all("<Command-o>", lambda _event: self._open_configuration())
        self.bind_all("<Command-s>", lambda _event: self._save_configuration())
        self.bind_all("<Command-Shift-S>", lambda _event: self._save_configuration_as())

    def _build_ui(self) -> None:
        shell = ttk.Frame(self, padding=6)
        shell.pack(fill="both", expand=True)
        self._build_model_panel(shell)
        self._build_dataset_panel(shell)
        command = ttk.Frame(shell)
        command.pack(fill="x", pady=(8, 0))
        self.generate_datasets_button = ttk.Button(command, text="Generate Datasets...", command=self._on_generate)
        self.generate_datasets_button.pack()
        self._update_action_states()

    def _build_model_panel(self, parent: object) -> None:
        panel = ttk.LabelFrame(parent, text="Model Construction", padding=6)
        panel.pack(fill="both", expand=True)
        controls = ttk.Frame(panel)
        controls.pack(pady=(0, 6))
        self.model_source_buttons = []
        for label, command in (
            ("Generate Model", self._generate_model),
            ("Create Model", self._create_model),
            ("Load Model", self._load_model),
        ):
            button = ttk.Button(controls, text=label, command=command)
            button.pack(side="left", padx=3)
            self.model_source_buttons.append(button)
        self.edit_button = ttk.Button(controls, text="Edit Model", command=self._edit_model)
        self.edit_button.pack(side="left", padx=3)
        self.delete_button = ttk.Button(controls, text="Delete Model", command=self._delete_model)
        self.delete_button.pack(side="left", padx=3)

        self.model_table = ModelTableView(panel, self._set_model_selected, self._set_model_weight)
        self.model_table.pack(fill="both", expand=True)
        self.model_table.rebuild(self.models)
        self.quantile_summary = tk.StringVar(value="Number of EDM Quantiles: 0")
        ttk.Label(panel, textvariable=self.quantile_summary, font=("TkDefaultFont", 10, "bold")).pack(pady=(6, 0))

    def _build_dataset_panel(self, parent: object) -> None:
        outer = ttk.LabelFrame(parent, text="Dataset Construction", padding=6)
        outer.pack(fill="x", pady=(7, 0))
        noise = ttk.LabelFrame(outer, text="Non-predictive Attributes", padding=6)
        noise.pack(fill="x")
        self.noise_mode_var = tk.StringVar(value="generate")
        modes = ttk.Frame(noise)
        modes.pack()
        ttk.Radiobutton(modes, text="Generate", value="generate", variable=self.noise_mode_var, command=self._update_noise_mode).pack(side="left")
        ttk.Radiobutton(modes, text="Read from file", value="file", variable=self.noise_mode_var, command=self._update_noise_mode).pack(
            side="left", padx=(12, 0)
        )

        self.generated_noise_frame = ttk.Frame(noise)
        self.attr_count_var = tk.StringVar(value="100")
        self.af_min_var = tk.StringVar(value="0.01")
        self.af_max_var = tk.StringVar(value="0.5")
        ttk.Label(self.generated_noise_frame, text="Total number of attributes").pack(side="left", padx=(20, 5))
        ttk.Entry(self.generated_noise_frame, textvariable=self.attr_count_var, width=8).pack(side="left")
        ttk.Label(self.generated_noise_frame, text="Minor-allele-frequency range").pack(side="left", padx=(45, 5))
        ttk.Entry(self.generated_noise_frame, textvariable=self.af_min_var, width=7).pack(side="left")
        ttk.Entry(self.generated_noise_frame, textvariable=self.af_max_var, width=7).pack(side="left", padx=(6, 0))

        self.file_noise_frame = ttk.Frame(noise)
        ttk.Button(self.file_noise_frame, text="Load SNP file", command=self._browse_noise_file).grid(row=0, column=0, rowspan=3, padx=(0, 25))
        self.noise_file_label = ttk.Label(self.file_noise_frame, text="File: (none)")
        self.noise_file_label.grid(row=0, column=1, sticky="w")
        self.noise_attribute_label = ttk.Label(self.file_noise_frame, text="Number of attributes: 0")
        self.noise_attribute_label.grid(row=1, column=1, sticky="w")
        self.noise_instance_label = ttk.Label(self.file_noise_frame, text="Total number of instances: 0")
        self.noise_instance_label.grid(row=2, column=1, sticky="w")

        properties = ttk.LabelFrame(outer, text="Dataset Properties", padding=6)
        properties.pack(fill="x", pady=(7, 0))
        self.mixed_var = tk.StringVar(value="hierarchical")
        self.endpoint_var = tk.StringVar(value="binary")
        self.hetero_label_var = tk.BooleanVar(value=False)
        mix_row = ttk.Frame(properties)
        mix_row.pack()
        self.additive_button = ttk.Radiobutton(
            mix_row, text="Additive Data", value="hierarchical", variable=self.mixed_var, command=self._update_mixed_ui
        )
        self.additive_button.pack(side="left")
        self.heterogeneous_button = ttk.Radiobutton(
            mix_row, text="Heterogenous Data", value="heterogeneous", variable=self.mixed_var, command=self._update_mixed_ui
        )
        self.heterogeneous_button.pack(side="left", padx=(12, 0))
        self.hetero_label_check = ttk.Checkbutton(
            mix_row, text="Add model labels for Heterogeneous Data", variable=self.hetero_label_var
        )
        self.hetero_label_check.pack(side="left", padx=(12, 0))

        endpoint_row = ttk.Frame(properties)
        endpoint_row.pack(pady=(5, 0))
        ttk.Radiobutton(
            endpoint_row, text="Binary Class", value="binary", variable=self.endpoint_var, command=self._update_endpoint_ui
        ).pack(side="left")
        ttk.Radiobutton(
            endpoint_row, text="Quantitative Trait", value="continuous", variable=self.endpoint_var, command=self._update_endpoint_ui
        ).pack(side="left", padx=(12, 0))

        self.endpoint_cards = ttk.Frame(properties)
        self.endpoint_cards.pack(fill="x", pady=(5, 0))
        self.case_var = tk.StringVar(value="400")
        self.control_var = tk.StringVar(value="400")
        self.balanced_var = tk.BooleanVar(value=False)
        self.binary_frame = ttk.Frame(self.endpoint_cards)
        ttk.Checkbutton(
            self.binary_frame, text="Balanced case/control ratio", variable=self.balanced_var, command=self._update_counts
        ).pack()
        case_row = ttk.Frame(self.binary_frame)
        case_row.pack(pady=(5, 0))
        ttk.Label(case_row, text="Number of cases").pack(side="left")
        ttk.Entry(case_row, textvariable=self.case_var, width=8).pack(side="left", padx=(5, 30))
        ttk.Label(case_row, text="Number of controls").pack(side="left")
        self.control_entry = ttk.Entry(case_row, textvariable=self.control_var, width=8)
        self.control_entry.pack(side="left", padx=(5, 0))
        self.sample_summary = tk.StringVar(value="Total sample size: 800    Case proportion: 0.5000")
        ttk.Label(self.binary_frame, textvariable=self.sample_summary, font=("TkDefaultFont", 10, "bold")).pack(pady=(5, 0))

        self.fixed_binary_frame = ttk.Frame(self.endpoint_cards)
        ttk.Label(self.fixed_binary_frame, text="Move slider to set case control counts.").pack()
        self.read_case_percent_var = tk.DoubleVar(value=50.0)
        ttk.Scale(
            self.fixed_binary_frame,
            from_=1,
            to=99,
            variable=self.read_case_percent_var,
            command=lambda _value: self._update_fixed_counts(),
        ).pack(fill="x", padx=90)
        self.fixed_summary = tk.StringVar(value="Case proportion: 0.5000    Number of cases: 0    Number of controls: 0")
        ttk.Label(self.fixed_binary_frame, textvariable=self.fixed_summary, font=("TkDefaultFont", 10, "bold")).pack(pady=(4, 0))

        self.total_var = tk.StringVar(value="800")
        self.std_var = tk.StringVar(value="0.2")
        self.quantitative_frame = ttk.Frame(self.endpoint_cards)
        ttk.Label(self.quantitative_frame, text="Total number of samples:").pack(side="left")
        self.total_entry = ttk.Entry(self.quantitative_frame, textvariable=self.total_var, width=8)
        self.total_entry.pack(side="left", padx=(5, 30))
        ttk.Label(self.quantitative_frame, text="Standard Deviation:").pack(side="left")
        ttk.Entry(self.quantitative_frame, textvariable=self.std_var, width=8).pack(side="left", padx=(5, 0))

        replicate_row = ttk.Frame(outer)
        replicate_row.pack(fill="x", pady=(7, 0))
        self.repl_var = tk.StringVar(value="100")
        ttk.Label(replicate_row, text="Number of replicates").pack(side="left", padx=(60, 5))
        ttk.Entry(replicate_row, textvariable=self.repl_var, width=8).pack(side="left")
        self.total_dataset_summary = tk.StringVar(value="Total number of datasets: --")
        ttk.Label(replicate_row, textvariable=self.total_dataset_summary, font=("TkDefaultFont", 10, "bold")).pack(side="right", padx=(0, 80))

        for variable in (self.case_var, self.control_var, self.repl_var):
            variable.trace_add("write", self._update_counts)
        self._update_noise_mode()
        self._update_endpoint_ui()
        self._update_mixed_ui()

    def _selected_indices(self) -> List[int]:
        return [index for index, model in enumerate(self.models) if model.selected]

    def _next_predictive_attribute_number(self) -> int:
        next_number = 1
        for model in self.models:
            for name in model.attribute_names:
                if name.lower().startswith("p"):
                    try:
                        next_number = max(next_number, int(name[1:]) + 1)
                    except ValueError:
                        pass
        return next_number

    def _set_model_selected(self, index: int, selected: bool) -> None:
        if self._running:
            return
        if selected and not model_can_be_selected(self.models, index):
            return
        self.models[index].selected = selected
        self.model_table.refresh_interaction_state(self.models, self._running)
        self._update_quantile_summary()
        self._update_total_dataset_count()
        self._update_action_states()

    def _set_model_weight(self, index: int, weight: float) -> None:
        self.models[index].weight = weight
        self._update_action_states()

    def _refresh_model_table(self) -> None:
        self.model_table.rebuild(self.models, self._running)
        self._update_quantile_summary()
        self._update_counts()

    def _update_quantile_summary(self) -> None:
        selected = self._selected_indices()
        if not selected:
            value = "0"
        else:
            common = common_selected_quantile_count(self.models)
            value = str(common) if common is not None else "--"
        self.quantile_summary.set(f"Number of EDM Quantiles: {value}")

    def _update_action_states(self) -> None:
        selected = self._selected_indices()
        editable = selected_model_can_be_edited(self.models)
        self.edit_button.state(["!disabled"] if editable and not self._running else ["disabled"])
        self.delete_button.state(["!disabled"] if selected and not self._running else ["disabled"])
        for button in self.model_source_buttons:
            button.state(["disabled"] if self._running else ["!disabled"])

        multi = len(selected) > 1 and not self._running
        self.additive_button.state(["!disabled"] if multi else ["disabled"])
        self.heterogeneous_button.state(["!disabled"] if multi else ["disabled"])
        labels_enabled = multi and self.mixed_var.get() == "heterogeneous"
        self.hetero_label_check.state(["!disabled"] if labels_enabled else ["disabled"])
        if not labels_enabled:
            self.hetero_label_var.set(False)

        common = common_selected_quantile_count(self.models)
        can_generate = bool(selected) and common is not None and self._dataset_sample_count() > 0 and not self._running
        self.generate_datasets_button.state(["!disabled"] if can_generate else ["disabled"])

    def _generate_model(self) -> None:
        if self._running:
            return
        model_number = self.next_model_number
        self.next_model_number += 1
        dialog = GenerateModelDialog(self, self._next_predictive_attribute_number())
        self.wait_window(dialog)
        if dialog.result is None:
            return
        path = filedialog.asksaveasfilename(
            title="Location for model files",
            initialfile=f"Model_{model_number}.txt",
            defaultextension=".txt",
            filetypes=[("GAMETES model", "*.txt"), ("All files", "*")],
        )
        if not path:
            return
        dialog.result.output_prefix = str(normalize_model_output_prefix(path))
        dialog.result.name = clean_model_name(path)
        self._start_model_generation(dialog.result)

    def _create_model(self) -> None:
        if self._running:
            return
        model_number = self.next_model_number
        self.next_model_number += 1
        first = self._next_predictive_attribute_number()
        initial = ModelSpec(
            name=f"Model {model_number}",
            source="custom",
            heritability=float("nan"),
            prevalence=0.0,
            mafs=[0.2, 0.2],
            feature_names=[f"P{first}", f"P{first + 1}"],
        )
        dialog = CustomModelDialog(self, initial, creating=True)
        self.wait_window(dialog)
        if dialog.result is None:
            return
        path = self._choose_model_output(dialog.result)
        if path is None:
            return
        try:
            save_model_spec(dialog.result, path)
        except Exception as exc:
            messagebox.showerror("Unable to save model", str(exc), parent=self)
            return
        dialog.result.name = clean_model_name(path)
        self.models.append(dialog.result)
        self._refresh_model_table()

    def _load_model(self) -> None:
        if self._running:
            return
        path = filedialog.askopenfilename(
            title="Load Model",
            filetypes=[("GAMETES model", "*.txt"), ("All files", "*")],
        )
        if not path:
            return
        try:
            self.models.append(load_model_spec(path))
        except Exception as exc:
            messagebox.showerror("Unable to load model", str(exc), parent=self)
            return
        self._refresh_model_table()

    def _edit_model(self) -> None:
        selected = self._selected_indices()
        if len(selected) != 1:
            return
        index = selected[0]
        model = self.models[index]
        if not model.tables or model.order not in (2, 3) or model.quantile_count != 1:
            return
        dialog = CustomModelDialog(self, model, creating=False)
        self.wait_window(dialog)
        if dialog.result is None:
            return
        path = self._choose_model_output(dialog.result)
        if path is None:
            return
        try:
            save_model_spec(dialog.result, path)
        except Exception as exc:
            messagebox.showerror("Unable to save model", str(exc), parent=self)
            return
        dialog.result.name = clean_model_name(path)
        dialog.result.selected = True
        self.models[index] = dialog.result
        self._refresh_model_table()

    def _choose_model_output(self, model: ModelSpec) -> Optional[Path]:
        initial = Path(model.input_path).name if model.input_path else f"{model.name}_Models.txt"
        path = filedialog.asksaveasfilename(
            title="Location for model files",
            initialfile=initial,
            defaultextension=".txt",
            filetypes=[("GAMETES model", "*.txt"), ("All files", "*")],
        )
        return Path(path) if path else None

    def _delete_model(self) -> None:
        if self._running:
            return
        self.models = [model for model in self.models if not model.selected]
        self._refresh_model_table()

    def _update_noise_mode(self) -> None:
        if self.noise_mode_var.get() == "generate":
            self.file_noise_frame.pack_forget()
            self.generated_noise_frame.pack(pady=(6, 0))
        else:
            self.generated_noise_frame.pack_forget()
            self.file_noise_frame.pack(pady=(6, 0))
        self._update_endpoint_ui()

    def _update_endpoint_ui(self) -> None:
        if not hasattr(self, "endpoint_cards"):
            return
        for frame in (self.binary_frame, self.fixed_binary_frame, self.quantitative_frame):
            frame.pack_forget()
        if self.endpoint_var.get() == "continuous":
            if self.noise_mode_var.get() == "file":
                self.total_var.set(str(self.noise_file_instances))
                self.total_entry.state(["disabled"])
            else:
                self.total_entry.state(["!disabled"])
            self.quantitative_frame.pack()
        elif self.noise_mode_var.get() == "file":
            self.fixed_binary_frame.pack(fill="x")
        else:
            self.binary_frame.pack()
        self._update_counts()

    def _update_mixed_ui(self) -> None:
        if hasattr(self, "generate_datasets_button"):
            self._update_action_states()

    def _update_counts(self, *_args: object) -> None:
        if self._updating_counts or not hasattr(self, "sample_summary"):
            return
        self._updating_counts = True
        try:
            if self.balanced_var.get():
                self.control_entry.state(["disabled"])
                if self.control_var.get() != self.case_var.get():
                    self.control_var.set(self.case_var.get())
            else:
                self.control_entry.state(["!disabled"])
            try:
                cases = int(self.case_var.get())
                controls = int(self.control_var.get())
                total = cases + controls
                proportion = cases / total if total > 0 else 0.0
                self.sample_summary.set(f"Total sample size: {total:,}    Case proportion: {proportion:.4f}")
            except ValueError:
                self.sample_summary.set("Total sample size: --    Case proportion: --")
            self._update_fixed_counts()
            self._update_total_dataset_count()
        finally:
            self._updating_counts = False
        if hasattr(self, "generate_datasets_button"):
            self._update_action_states()

    def _update_total_dataset_count(self) -> None:
        try:
            replicates = int(self.repl_var.get())
            common = common_selected_quantile_count(self.models)
            total_datasets = replicates * common if common is not None and self._selected_indices() else None
            self.total_dataset_summary.set(
                f"Total number of datasets: {total_datasets}" if total_datasets is not None else "Total number of datasets: --"
            )
        except ValueError:
            self.total_dataset_summary.set("Total number of datasets: --")

    def _update_fixed_counts(self) -> None:
        if not hasattr(self, "fixed_summary"):
            return
        proportion = round(float(self.read_case_percent_var.get())) / 100.0
        total = self.noise_file_instances
        cases = int(round(total * proportion))
        self.fixed_summary.set(
            f"Case proportion: {proportion:.4f}    Number of cases: {cases}    Number of controls: {total - cases}"
        )

    def _dataset_sample_count(self) -> int:
        try:
            if self.noise_mode_var.get() == "file":
                return self.noise_file_instances
            if self.endpoint_var.get() == "continuous":
                return int(self.total_var.get())
            return int(self.case_var.get()) + int(self.control_var.get())
        except ValueError:
            return 0

    def _browse_noise_file(self) -> None:
        path = filedialog.askopenfilename(
            title="Load SNP file",
            filetypes=[("Tab-delimited text", "*.txt"), ("All files", "*")],
        )
        if not path:
            return
        try:
            rows = SnpGenSimulator.parse_data_input_file(Path(path))
            if not rows:
                raise ValueError("The file contains no data rows")
            width = len(rows[0])
            if any(len(row) != width for row in rows):
                raise ValueError("Rows contain different numbers of attributes")
            if any(value not in (0, 1, 2) for row in rows for value in row):
                raise ValueError("SNP values must be encoded as 0, 1, or 2")
        except Exception as exc:
            messagebox.showerror("Invalid SNP file", str(exc), parent=self)
            return
        self.noise_file_path = path
        self.noise_file_attributes = width
        self.noise_file_instances = len(rows)
        self.noise_file_label.configure(text=f"File: {Path(path).name}")
        self.noise_attribute_label.configure(text=f"Number of attributes: {width}")
        self.noise_instance_label.configure(text=f"Total number of instances: {len(rows)}")
        self._update_endpoint_ui()

    def _build_document(self, output_file: Optional[Path] = None) -> SnpGenDocument:
        active = [model for model in self.models if model.selected]
        if not active:
            raise ValueError("Select at least one model")
        quantiles = {model.quantile_count for model in active}
        if len(quantiles) != 1:
            raise ValueError("All selected models must have the same number of quantiles")
        if any(model.weight <= 0.0 for model in active):
            raise ValueError("Heterogeneity proportions must be greater than zero")

        document = SnpGenDocument()
        document.model_list = [model.to_doc_model() for model in active]
        document.model_fractions = normalized_model_weights(active)
        document.ras_quantile_count = next(iter(quantiles))
        document.ras_population_count = 1
        document.ras_try_count = 1
        document.random_seed = None

        dataset = DocDataset()
        dataset.replicate_count = int(self.repl_var.get())
        if dataset.replicate_count <= 0:
            raise ValueError("Number of replicates must be greater than zero")
        dataset.output_file = output_file
        dataset.multiple_model_dataset_type = MixedModelDatasetType(self.mixed_var.get())
        dataset.heterogeneous_label_boolean = bool(self.hetero_label_var.get())
        dataset.create_continuous_endpoints = self.endpoint_var.get() == "continuous"

        predictive_count = sum(model.order for model in active)
        if self.noise_mode_var.get() == "file":
            if not getattr(self, "noise_file_path", "") or self.noise_file_instances <= 0:
                raise ValueError("Load a non-predictive SNP file")
            document.noise_input_file = Path(self.noise_file_path)
            dataset.total_count = self.noise_file_instances
            dataset.total_attribute_count = self.noise_file_attributes + predictive_count
            if dataset.create_continuous_endpoints:
                dataset.case_proportion = None
                dataset.continuous_endpoints_standard_deviation = float(self.std_var.get())
            else:
                dataset.case_proportion = round(float(self.read_case_percent_var.get())) / 100.0
        else:
            dataset.allele_frequency_min = float(self.af_min_var.get())
            dataset.allele_frequency_max = float(self.af_max_var.get())
            if not 0.0 <= dataset.allele_frequency_min <= dataset.allele_frequency_max <= 0.5:
                raise ValueError("Use a minor-allele-frequency range between 0 and 0.5")
            dataset.total_attribute_count = int(self.attr_count_var.get())
            if dataset.total_attribute_count < predictive_count:
                raise ValueError(f"Total number of attributes must be at least {predictive_count}")
            if dataset.create_continuous_endpoints:
                dataset.total_count = int(self.total_var.get())
                dataset.case_proportion = None
                dataset.continuous_endpoints_standard_deviation = float(self.std_var.get())
            else:
                cases = int(self.case_var.get())
                controls = int(self.control_var.get())
                if cases <= 0 or controls <= 0:
                    raise ValueError("Case and control counts must be greater than zero")
                dataset.total_count = cases + controls
                dataset.case_proportion = cases / dataset.total_count

        document.dataset_list = [dataset]
        document.first_dataset = dataset
        document.run_document = True
        error = document.verify_all_needed_parameters()
        if error is not None:
            raise error
        return document

    def _on_generate(self) -> None:
        if self._running:
            return
        try:
            document = self._build_document()
        except Exception as exc:
            messagebox.showerror("Input error", str(exc), parent=self)
            return
        path = filedialog.asksaveasfilename(title="Location for generated datasets")
        if not path:
            return
        document.first_dataset.output_file = Path(path)
        self._running = True
        self._run_kind = "dataset"
        self._worker_done = False
        self._worker_error = None
        self._worker_model_result = None
        self._show_progress("Saving datasets...")
        self._refresh_model_table()
        self._launch_worker(_dataset_generation_worker, document)

    def _start_model_generation(self, specification: ModelSpec) -> None:
        self._running = True
        self._run_kind = "model"
        self._worker_done = False
        self._worker_error = None
        self._worker_model_result = None
        self._show_progress("Generating models...")
        self._refresh_model_table()
        self._launch_worker(_model_generation_worker, specification, None)

    def _show_progress(self, label: str) -> None:
        window = tk.Toplevel(self)
        window.title("Progress")
        window.transient(self)
        window.resizable(False, False)
        ttk.Label(window, text=label).pack(padx=20, pady=(12, 5))
        bar = ttk.Progressbar(window, mode="indeterminate", length=260)
        bar.pack(padx=20, pady=(0, 12))
        bar.start(12)
        window.protocol("WM_DELETE_WINDOW", lambda: None)
        window.grab_set()
        self._progress_window = window
        self._progress_bar = bar
        window.after_idle(lambda: _activate_window(window))

    def _hide_progress(self) -> None:
        if self._progress_bar is not None:
            self._progress_bar.stop()
        if self._progress_window is not None:
            try:
                self._progress_window.grab_release()
                self._progress_window.destroy()
            except Exception:
                pass
        self._progress_bar = None
        self._progress_window = None

    def _launch_worker(self, target: object, *arguments: object) -> None:
        self._worker_queue = queue.Queue()
        self._worker_thread = threading.Thread(
            target=_responsive_worker_entry,
            args=(target, self._worker_queue, *arguments),
            daemon=True,
        )
        self._worker_thread.start()

    def _poll_worker(self) -> None:
        while True:
            try:
                message_type, payload = self._worker_queue.get_nowait()
            except queue.Empty:
                break
            if message_type == "model":
                self._worker_model_result = payload
            elif message_type == "error":
                self._worker_error = str(payload)
            elif message_type == "done":
                self._worker_done = True
        if self._worker_thread is not None and not self._worker_thread.is_alive() and not self._worker_done:
            self._worker_error = "Generation worker stopped unexpectedly"
            self._worker_done = True
        if self._running and self._worker_done:
            self._finish_run()
        self.after(50, self._poll_worker)

    def _finish_run(self) -> None:
        self._running = False
        self._worker_thread = None
        self._hide_progress()
        if self._worker_error:
            messagebox.showerror("Error in processing", self._worker_error, parent=self)
        elif self._run_kind == "model" and self._worker_model_result is not None:
            model = self._worker_model_result
            self.models.append(model)
            if len(model.population_scores) < model.population_count:
                messagebox.showwarning(
                    "Warning",
                    f"You asked for a population of {model.population_count} models, but only {len(model.population_scores)} were found.",
                    parent=self,
                )
        self._worker_done = False
        self._worker_model_result = None
        self._refresh_model_table()

    def _configuration_payload(self) -> Dict[str, object]:
        model_items = []
        for model in self.models:
            if not model.input_path:
                raise ValueError(f"Model {model.name} has not been saved")
            model_items.append(
                {
                    "file": model.input_path,
                    "weight": model.weight,
                    "selected": model.selected,
                }
            )
        return {
            "format": "GAMETES Python GUI configuration",
            "version": 1,
            "models": model_items,
            "dataset": {
                "noise_mode": self.noise_mode_var.get(),
                "noise_file": getattr(self, "noise_file_path", ""),
                "total_attributes": self.attr_count_var.get(),
                "maf_min": self.af_min_var.get(),
                "maf_max": self.af_max_var.get(),
                "endpoint": self.endpoint_var.get(),
                "mixed": self.mixed_var.get(),
                "heterogeneous_labels": self.hetero_label_var.get(),
                "cases": self.case_var.get(),
                "controls": self.control_var.get(),
                "balanced": self.balanced_var.get(),
                "file_case_percent": self.read_case_percent_var.get(),
                "total_samples": self.total_var.get(),
                "standard_deviation": self.std_var.get(),
                "replicates": self.repl_var.get(),
            },
        }

    def _save_configuration(self) -> None:
        if self.configuration_file is None:
            self._save_configuration_as()
            return
        self._write_configuration(self.configuration_file)

    def _save_configuration_as(self) -> None:
        path = filedialog.asksaveasfilename(
            title="Save GAMETES configuration",
            defaultextension=".json",
            filetypes=[("GAMETES configuration", "*.json"), ("All files", "*")],
        )
        if path:
            self.configuration_file = Path(path)
            self._write_configuration(self.configuration_file)

    def _write_configuration(self, path: Path) -> None:
        try:
            payload = self._configuration_payload()
            path.write_text(json.dumps(payload, indent=2), encoding="utf-8")
        except Exception as exc:
            messagebox.showerror("Unable to save configuration", str(exc), parent=self)

    def _open_configuration(self) -> None:
        path = filedialog.askopenfilename(
            title="Open GAMETES configuration",
            filetypes=[("GAMETES configuration", "*.json"), ("All files", "*")],
        )
        if not path:
            return
        try:
            config_path = Path(path)
            payload = json.loads(config_path.read_text(encoding="utf-8"))
            if payload.get("format") != "GAMETES Python GUI configuration":
                raise ValueError("This is not a GAMETES Python GUI configuration")
            models = []
            for item in payload.get("models", []):
                model_path = Path(item["file"])
                if not model_path.is_absolute():
                    model_path = config_path.parent / model_path
                model = load_model_spec(model_path)
                model.weight = float(item.get("weight", 1.0))
                model.selected = bool(item.get("selected", False))
                models.append(model)
            standardize_selected_quantiles(models)
            self.models = models
            dataset = payload.get("dataset", {})
            self.noise_mode_var.set(str(dataset.get("noise_mode", "generate")))
            self.attr_count_var.set(str(dataset.get("total_attributes", "100")))
            self.af_min_var.set(str(dataset.get("maf_min", "0.01")))
            self.af_max_var.set(str(dataset.get("maf_max", "0.5")))
            self.endpoint_var.set(str(dataset.get("endpoint", "binary")))
            self.mixed_var.set(str(dataset.get("mixed", "hierarchical")))
            self.hetero_label_var.set(bool(dataset.get("heterogeneous_labels", False)))
            self.case_var.set(str(dataset.get("cases", "400")))
            self.control_var.set(str(dataset.get("controls", "400")))
            self.balanced_var.set(bool(dataset.get("balanced", False)))
            self.read_case_percent_var.set(float(dataset.get("file_case_percent", 50.0)))
            self.total_var.set(str(dataset.get("total_samples", "800")))
            self.std_var.set(str(dataset.get("standard_deviation", "0.2")))
            self.repl_var.set(str(dataset.get("replicates", "100")))
            noise_file = str(dataset.get("noise_file", ""))
            if noise_file:
                noise_path = Path(noise_file)
                if not noise_path.is_absolute():
                    noise_path = config_path.parent / noise_path
                self._load_noise_path(noise_path)
            self.configuration_file = config_path
            self._update_noise_mode()
            self._update_endpoint_ui()
            self._refresh_model_table()
        except Exception as exc:
            messagebox.showerror("Unable to open configuration", str(exc), parent=self)

    def _load_noise_path(self, path: Path) -> None:
        rows = SnpGenSimulator.parse_data_input_file(path)
        if not rows:
            raise ValueError("Noise SNP file is empty")
        self.noise_file_path = str(path)
        self.noise_file_attributes = len(rows[0])
        self.noise_file_instances = len(rows)
        self.noise_file_label.configure(text=f"File: {path.name}")
        self.noise_attribute_label.configure(text=f"Number of attributes: {self.noise_file_attributes}")
        self.noise_instance_label.configure(text=f"Total number of instances: {self.noise_file_instances}")

    def _new_configuration(self) -> None:
        if self.models and not messagebox.askyesno("New", "Clear all models and start a new configuration?", parent=self):
            return
        self.models = []
        self.configuration_file = None
        self.next_model_number = 1
        self.noise_file_attributes = 0
        self.noise_file_instances = 0
        self.noise_file_path = ""
        self.noise_mode_var.set("generate")
        self.attr_count_var.set("100")
        self.af_min_var.set("0.01")
        self.af_max_var.set("0.5")
        self.endpoint_var.set("binary")
        self.mixed_var.set("hierarchical")
        self.hetero_label_var.set(False)
        self.case_var.set("400")
        self.control_var.set("400")
        self.balanced_var.set(False)
        self.read_case_percent_var.set(50.0)
        self.total_var.set("800")
        self.std_var.set("0.2")
        self.repl_var.set("100")
        self.noise_file_label.configure(text="File: (none)")
        self.noise_attribute_label.configure(text="Number of attributes: 0")
        self.noise_instance_label.configure(text="Total number of instances: 0")
        self._update_noise_mode()
        self._update_endpoint_ui()
        self._refresh_model_table()

    def _close_application(self) -> None:
        self.destroy()


def launch_gui() -> None:
    if tk is None or ttk is None:
        raise RuntimeError("Tkinter is not available in this Python environment")
    app = GametesGui()
    app.mainloop()
