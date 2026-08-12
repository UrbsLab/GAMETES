from __future__ import annotations

import math
import os
import shutil
import subprocess
import sys
import threading
import queue
from pathlib import Path

import pytest

from py_gametes.cli import main, run_document
from py_gametes.document import DocDataset, DocModel, MixedModelDatasetType, SnpGenDocument
from py_gametes.gui import (
    JAVA_MODEL_COLUMNS,
    ModelSpec,
    _dataset_generation_worker,
    build_custom_table,
    common_selected_quantile_count,
    generate_and_save_model_spec,
    load_model_spec,
    model_can_be_selected,
    model_tables_path,
    normalize_model_output_prefix,
    normalized_model_weights,
    save_model_spec,
    selected_model_can_be_edited,
    standardize_selected_quantiles,
    _responsive_worker_entry,
)
from py_gametes.java_random import JavaRandom
from py_gametes.simulator import SnpGenSimulator


JAVA_JAR = Path(os.environ.get("GAMETES_JAR", Path(__file__).resolve().parent / "gametes_2.2_dev.jar"))


def _doc_from_args(args: list[str]) -> SnpGenDocument:
    doc = SnpGenDocument()
    show_gui = doc.parse_arguments(args)
    assert show_gui is False
    assert doc.run_document is True
    return doc


def _read_first_line(path: Path) -> str:
    with path.open("r", encoding="utf-8") as f:
        return f.readline().rstrip("\n")


def _class_conditional_genotype_frequencies(path: Path) -> dict[tuple[int, int], list[float]]:
    lines = path.read_text(encoding="utf-8").splitlines()
    header = lines[0].split("\t")
    predictive_columns = [index for index, name in enumerate(header) if name.startswith("M0")]
    counts: dict[tuple[int, int], list[int]] = {}
    class_totals: dict[int, int] = {}
    for line in lines[1:]:
        values = [int(value) for value in line.split("\t")]
        class_value = values[-1]
        class_totals[class_value] = class_totals.get(class_value, 0) + 1
        for predictor_index, column in enumerate(predictive_columns):
            key = (class_value, predictor_index)
            counts.setdefault(key, [0, 0, 0])[values[column]] += 1
    return {
        key: [count / class_totals[key[0]] for count in genotype_counts]
        for key, genotype_counts in counts.items()
    }


def test_cli_main_no_args_launches_gui_like_the_jar(monkeypatch) -> None:
    import py_gametes.gui

    launched = []
    monkeypatch.setattr(py_gametes.gui, "launch_gui", lambda: launched.append(True))
    code = main([])
    assert code == 0
    assert launched == [True]


def test_java_random_matches_java_util_random_reference_sequence() -> None:
    rng = JavaRandom(123)

    assert rng.next_int() == -1188957731
    assert rng.next_int(10) == 0
    assert rng.next_double().hex() == "0x1.fb5719a699f85p-1"
    assert rng.next_long() == 4672433029010564658
    assert rng.next_gaussian().hex() == "0x1.c30f509bf541fp-2"
    assert rng.next_gaussian().hex() == "0x1.3d03dec9adba0p+0"


def test_cli_gui_flag_rejects_extra_args(capsys) -> None:
    code = main(["--gui", "-h"])
    err = capsys.readouterr().err
    assert code == 2
    assert "cannot be combined" in err


def test_parse_arguments_supports_nested_model_dataset_strings(tmp_path: Path) -> None:
    model_prefix = tmp_path / "basicModel"
    data_prefix = tmp_path / "myData"

    args = [
        "-M",
        f"-h 0.2 -p 0.3 -a 0.3 -a 0.2 -o {model_prefix}",
        "-q",
        "1",
        "-p",
        "30",
        "-t",
        "3000",
        "-D",
        f"-n 0.01 -x 0.5 -a 20 -s 20 -w 20 -r 1 -o {data_prefix}",
        "-r",
        "123",
    ]

    doc = _doc_from_args(args)

    assert len(doc.model_list) == 1
    assert len(doc.dataset_list) == 1
    assert doc.ras_quantile_count == 1
    assert doc.ras_population_count == 30
    assert doc.ras_try_count == 3000
    assert doc.random_seed == 123
    assert doc.model_fractions == [1.0]

    model = doc.model_list[0]
    assert model.attribute_count == 2
    assert model.prevalence == 0.3

    dataset = doc.dataset_list[0]
    assert dataset.total_attribute_count == 20
    assert dataset.total_count == 40
    assert dataset.multiple_model_dataset_type == MixedModelDatasetType.hierarchical


def test_end_to_end_generates_model_and_dataset_files(tmp_path: Path) -> None:
    model_prefix = tmp_path / "basicModel"
    data_prefix = tmp_path / "myData"

    args = [
        "-M",
        f"-h 0.2 -p 0.3 -a 0.3 -a 0.2 -o {model_prefix}",
        "-q",
        "1",
        "-p",
        "30",
        "-t",
        "3000",
        "-D",
        f"-n 0.01 -x 0.5 -a 20 -s 20 -w 20 -r 1 -o {data_prefix}",
        "-r",
        "123",
    ]

    doc = _doc_from_args(args)
    run_document(doc)

    model_file = tmp_path / "basicModel_Models.txt"
    score_file = tmp_path / "basicModel_EDM_Scores.txt"
    dataset_file = tmp_path / "myData_EDM-1" / "myData_EDM-1_1.txt"

    assert model_file.exists()
    assert score_file.exists()
    assert dataset_file.exists()

    dataset_header = _read_first_line(dataset_file)
    assert dataset_header.endswith("\tClass")
    assert len(dataset_header.split("\t")) == 21


def test_can_generate_dataset_from_loaded_model_file(tmp_path: Path) -> None:
    seed_model_prefix = tmp_path / "seedModel"

    seed_args = [
        "-M",
        f"-h 0.2 -p 0.3 -a 0.3 -a 0.2 -o {seed_model_prefix}",
        "-q",
        "1",
        "-p",
        "20",
        "-t",
        "2500",
        "-r",
        "7",
    ]
    seed_doc = _doc_from_args(seed_args)
    run_document(seed_doc)

    model_input_file = tmp_path / "seedModel_Models.txt"
    assert model_input_file.exists()

    loaded_data_prefix = tmp_path / "fromLoadedModel"
    loaded_args = [
        "-i",
        str(model_input_file),
        "-q",
        "1",
        "-D",
        f"-h hierarchical -n 0.01 -x 0.5 -a 12 -s 10 -w 10 -r 1 -o {loaded_data_prefix}",
        "-r",
        "5",
    ]

    loaded_doc = _doc_from_args(loaded_args)
    run_document(loaded_doc)

    dataset_file = tmp_path / "fromLoadedModel_EDM-1" / "fromLoadedModel_EDM-1_1.txt"
    assert dataset_file.exists()
    assert "Class" in _read_first_line(dataset_file)


def test_heterogeneous_output_creates_model_label_file(tmp_path: Path) -> None:
    m1 = tmp_path / "m1"
    m2 = tmp_path / "m2"
    out = tmp_path / "hetData"

    args = [
        "-M",
        f"-h 0.1 -p 0.5 -a 0.3 -o {m1}",
        "-w",
        "75",
        "-M",
        f"-h 0.03 -p 0.5 -a 0.4 -o {m2}",
        "-w",
        "25",
        "-q",
        "1",
        "-p",
        "20",
        "-t",
        "2500",
        "-D",
        f"-h heterogeneous -b -n 0.01 -x 0.5 -a 10 -s 10 -w 10 -r 1 -o {out}",
        "-r",
        "11",
    ]

    doc = _doc_from_args(args)
    run_document(doc)

    label_file = tmp_path / "hetData_EDM-1" / "hetData_EDM-1_1_hetLabel.txt"
    assert label_file.exists()
    assert _read_first_line(label_file).startswith("Model\t")


def test_custom_penetrance_table_matches_java_reference_metrics() -> None:
    table = build_custom_table(
        order=2,
        mafs=[0.5, 0.5],
        values=[0, 1, 0, 1, 0, 1, 0, 1, 0],
    )

    assert table.prevalence == pytest.approx(0.5)
    assert table.actual_heritability == pytest.approx(1.0)
    assert table.edm == pytest.approx(0.28125)
    assert table.odds_ratio == float("inf")
    assert table.row_sums_valid is True
    for marginal in table.calc_marginal_prevalences():
        assert marginal == pytest.approx([0.5, 0.5, 0.5])


def test_model_weights_are_normalized_and_exclude_unselected_models() -> None:
    models = [
        ModelSpec("m1", "custom", 1.0, 0.5, [0.5, 0.5], weight=75, selected=True),
        ModelSpec("m2", "custom", 1.0, 0.5, [0.5, 0.5], weight=25, selected=True),
        ModelSpec("unused", "custom", 1.0, 0.5, [0.5, 0.5], weight=100, selected=False),
    ]

    assert normalized_model_weights(models) == pytest.approx([0.75, 0.25, 0.0])


def test_custom_model_generates_outputs_without_model_input_file(tmp_path: Path) -> None:
    table = build_custom_table(
        order=2,
        mafs=[0.5, 0.5],
        values=[0, 1, 0, 1, 0, 1, 0, 1, 0],
    )
    model = DocModel(attribute_count=2, model_id="customModel")
    model.attribute_name_array = ["P1", "P2"]
    model.attribute_allele_frequency_array = [0.5, 0.5]
    model.heritability = table.actual_heritability
    model.prevalence = table.prevalence
    model.file = tmp_path / "customModel"
    model.set_penetrance_tables([table])

    dataset = DocDataset(
        total_attribute_count=6,
        total_count=20,
        case_proportion=0.5,
        replicate_count=1,
        output_file=tmp_path / "customData",
    )
    document = SnpGenDocument(
        model_fractions=[1.0],
        ras_quantile_count=1,
        ras_population_count=1,
        ras_try_count=1,
        model_list=[model],
        dataset_list=[dataset],
        random_seed=17,
    )

    run_document(document)

    assert document.model_input_files == []
    assert (tmp_path / "customModel_Models.txt").exists()
    assert (tmp_path / "customModel_EDM_Scores.txt").exists()
    dataset_file = tmp_path / "customData_EDM-1" / "customData_EDM-1_1.txt"
    assert dataset_file.exists()
    assert _read_first_line(dataset_file).endswith("M0P1\tM0P2\tClass")


def test_model_selection_only_allows_equal_quantile_counts() -> None:
    models = [
        ModelSpec("q2-selected", "generated", 0.1, 0.5, [0.2, 0.3], requested_quantiles=2, selected=True),
        ModelSpec("q1-disabled", "generated", 0.1, 0.5, [0.2, 0.3], requested_quantiles=1),
        ModelSpec("q2-enabled", "generated", 0.1, 0.5, [0.2, 0.3], requested_quantiles=2),
    ]

    assert common_selected_quantile_count(models) == 2
    assert model_can_be_selected(models, 0) is True
    assert model_can_be_selected(models, 1) is False
    assert model_can_be_selected(models, 2) is True

    # Defensive behavior for configurations loaded or constructed outside the GUI.
    models[1].selected = True
    assert common_selected_quantile_count(models) is None


def test_unequal_quantile_selections_are_standardized_to_first_group() -> None:
    models = [
        ModelSpec("q2-first", "loaded", 0.1, 0.5, [0.2, 0.3], requested_quantiles=2, selected=True),
        ModelSpec("q1-rejected", "loaded", 0.1, 0.5, [0.2, 0.3], requested_quantiles=1, selected=True),
        ModelSpec("q2-kept", "loaded", 0.1, 0.5, [0.2, 0.3], requested_quantiles=2, selected=True),
    ]

    assert standardize_selected_quantiles(models) == 2
    assert [model.selected for model in models] == [True, False, True]
    assert common_selected_quantile_count(models) == 2


def test_java_model_table_layout_and_edit_button_rules() -> None:
    assert JAVA_MODEL_COLUMNS == (
        "Model",
        "# Attributes",
        "Heritability",
        "SNPs",
        "Minor allele freq",
        "Heterogeneity proportion",
        "# Quantiles",
        "Selected",
    )

    table = build_custom_table(2, [0.5, 0.5], [0, 1, 0, 1, 0, 1, 0, 1, 0])
    loaded = ModelSpec("loaded", "loaded", 1.0, 0.5, [0.5, 0.5], tables=[table], selected=True)
    assert selected_model_can_be_edited([loaded]) is True

    loaded.tables = [table, table]
    assert selected_model_can_be_edited([loaded]) is False
    loaded.tables = [table]
    assert selected_model_can_be_edited([loaded, ModelSpec("second", "loaded", 1.0, 0.5, [0.5, 0.5], tables=[table], selected=True)]) is False


def test_blank_editor_properties_match_java_undefined_values() -> None:
    table = build_custom_table(2, [0.2, 0.2], [0.0] * 9)

    assert table.prevalence == 0.0
    assert math.isnan(table.actual_heritability)
    assert math.isnan(table.edm)
    assert math.isnan(table.odds_ratio)
    assert table.calc_marginal_prevalences() == [[0.0, 0.0, 0.0], [0.0, 0.0, 0.0]]

    cleared = build_custom_table(2, [0.0, 0.0], [0.0] * 9)
    assert math.isnan(cleared.calc_marginal_prevalences()[0][1])
    assert math.isnan(cleared.calc_marginal_prevalences()[1][2])


def test_direct_model_save_and_load_round_trip(tmp_path: Path) -> None:
    values = [0, 1, 0, 1, 0, 1, 0, 1, 0]
    table = build_custom_table(2, [0.5, 0.5], values, ["RiskA", "RiskB"])
    model = ModelSpec(
        "direct",
        "custom",
        table.actual_heritability,
        table.prevalence,
        [0.5, 0.5],
        feature_names=["RiskA", "RiskB"],
        output_prefix=str(tmp_path / "direct"),
        tables=[table],
    )

    path = save_model_spec(model)
    saved_text = path.read_text(encoding="utf-8")
    assert "Table is NOT normalized." in saved_text
    assert "Table:" in saved_text

    loaded = load_model_spec(path)
    assert loaded.source == "loaded"
    assert loaded.feature_names == ["RiskA", "RiskB"]
    assert loaded.mafs == pytest.approx([0.5, 0.5])
    assert [cell.value for cell in loaded.tables[0].cells] == pytest.approx(values)
    assert loaded.heritability == pytest.approx(table.actual_heritability)
    assert loaded.prevalence == pytest.approx(table.prevalence)


def test_gui_dataset_generation_does_not_rewrite_saved_direct_model(tmp_path: Path) -> None:
    table = build_custom_table(2, [0.5, 0.5], [0, 1, 0, 1, 0, 1, 0, 1, 0])
    model = ModelSpec(
        "direct",
        "custom",
        table.actual_heritability,
        table.prevalence,
        [0.5, 0.5],
        output_prefix=str(tmp_path / "direct"),
        tables=[table],
        selected=True,
    )
    model_file = save_model_spec(model)
    model_contents = model_file.read_bytes()
    dataset = DocDataset(
        total_attribute_count=4,
        total_count=20,
        case_proportion=0.5,
        replicate_count=1,
        output_file=tmp_path / "directData",
    )
    document = SnpGenDocument(
        model_fractions=[1.0],
        ras_quantile_count=1,
        model_list=[model.to_doc_model()],
        dataset_list=[dataset],
        first_dataset=dataset,
        random_seed=13,
    )
    messages: "queue.Queue[tuple[str, object]]" = queue.Queue()

    _dataset_generation_worker(messages, document)

    assert model_file.read_bytes() == model_contents
    assert (tmp_path / "directData_EDM-1" / "directData_EDM-1_1.txt").exists()
    message_types = [message_type for message_type, _payload in list(messages.queue)]
    assert "error" not in message_types
    assert message_types[-1] == "done"


@pytest.mark.parametrize(
    ("provided", "expected"),
    (
        ("risk_model", "risk_model"),
        ("risk_model.txt", "risk_model"),
        ("risk_model_Models.txt", "risk_model"),
    ),
)
def test_model_output_name_normalization(tmp_path: Path, provided: str, expected: str) -> None:
    prefix = normalize_model_output_prefix(tmp_path / provided)
    assert prefix.name == expected
    assert model_tables_path(tmp_path / provided).name == f"{expected}_Models.txt"


def test_responsive_worker_reduces_and_restores_gil_timeslice() -> None:
    original_interval = sys.getswitchinterval()
    observed_intervals = []
    messages: "queue.Queue[tuple[str, object]]" = queue.Queue()

    def worker(output_queue: object) -> None:
        observed_intervals.append(sys.getswitchinterval())
        output_queue.put(("done", None))

    thread = threading.Thread(target=_responsive_worker_entry, args=(worker, messages))
    thread.start()
    thread.join(timeout=2)

    assert thread.is_alive() is False
    assert observed_intervals == pytest.approx([0.001])
    assert sys.getswitchinterval() == pytest.approx(original_interval)
    assert messages.get_nowait() == ("done", None)


def test_generate_model_saves_immediately_with_custom_feature_names(tmp_path: Path) -> None:
    specification = ModelSpec(
        name="namedModel",
        source="generated",
        heritability=0.1,
        prevalence=0.5,
        mafs=[0.2, 0.4],
        feature_names=["APOE", "TREM2"],
        output_prefix=str(tmp_path / "namedModel"),
        requested_quantiles=1,
        population_count=20,
        try_count=5000,
    )

    generated = generate_and_save_model_spec(specification, random_seed=19)

    assert generated.selected is False
    assert generated.input_path == str(tmp_path / "namedModel_Models.txt")
    assert (tmp_path / "namedModel_Models.txt").exists()
    assert (tmp_path / "namedModel_EDM_Scores.txt").exists()
    parsed = SnpGenSimulator().fetch_tables(Path(generated.input_path))
    assert len(parsed) == 1
    assert parsed[0].get_attribute_names() == ["APOE", "TREM2"]
    assert parsed[0].actual_heritability == pytest.approx(0.1)
    assert parsed[0].prevalence == pytest.approx(0.5)


@pytest.mark.skipif(shutil.which("java") is None or not JAVA_JAR.exists(), reason="GAMETES Java JAR is unavailable")
def test_java_and_python_model_files_are_interchangeable(tmp_path: Path) -> None:
    python_table = build_custom_table(
        order=2,
        mafs=[0.5, 0.5],
        values=[0, 1, 0, 1, 0, 1, 0, 1, 0],
        attribute_names=["RiskA", "RiskB"],
    )
    python_model = ModelSpec(
        name="pythonModel",
        source="custom",
        heritability=python_table.actual_heritability,
        prevalence=python_table.prevalence,
        mafs=[0.5, 0.5],
        feature_names=["RiskA", "RiskB"],
        output_prefix=str(tmp_path / "pythonModel"),
        tables=[python_table],
    )
    python_model_file = save_model_spec(python_model)

    java_dataset_prefix = tmp_path / "javaFromPython"
    subprocess.run(
        [
            "java",
            "-jar",
            str(JAVA_JAR),
            "-i",
            str(python_model_file),
            "-q",
            "1",
            "-D",
            f"-h hierarchical -n 0.01 -x 0.5 -a 6 -s 400 -w 400 -r 1 -o {java_dataset_prefix}",
            "-r",
            "31",
        ],
        check=True,
        capture_output=True,
        text=True,
        timeout=60,
    )
    java_dataset = tmp_path / "javaFromPython_EDM-1" / "javaFromPython_EDM-1_1.txt"
    assert java_dataset.exists()
    assert _read_first_line(java_dataset).endswith("M0RiskA\tM0RiskB\tClass")

    python_same_model_prefix = tmp_path / "pythonFromPython"
    python_same_model_doc = _doc_from_args(
        [
            "-i",
            str(python_model_file),
            "-q",
            "1",
            "-D",
            f"-h hierarchical -n 0.01 -x 0.5 -a 6 -s 400 -w 400 -r 1 -o {python_same_model_prefix}",
            "-r",
            "31",
        ]
    )
    run_document(python_same_model_doc)
    python_same_model_dataset = tmp_path / "pythonFromPython_EDM-1" / "pythonFromPython_EDM-1_1.txt"
    assert python_same_model_dataset.exists()
    assert python_same_model_dataset.read_bytes() == java_dataset.read_bytes()

    java_model_prefix = tmp_path / "javaModel"
    subprocess.run(
        [
            "java",
            "-jar",
            str(JAVA_JAR),
            "-M",
            f"-h 0.1 -p 0.5 -a 0.2 -a 0.4 -o {java_model_prefix}",
            "-q",
            "1",
            "-p",
            "20",
            "-t",
            "5000",
            "-r",
            "19",
        ],
        check=True,
        capture_output=True,
        text=True,
        timeout=60,
    )
    java_model_file = tmp_path / "javaModel_Models.txt"
    java_tables = SnpGenSimulator().fetch_tables(java_model_file)
    assert len(java_tables) == 1
    java_properties = {}
    for line in java_model_file.read_text(encoding="utf-8").splitlines():
        if ":" in line:
            key, value = line.split(":", 1)
            if key in {"K", "Heritability", "Ease-of-detection metric", "Odds ratio"}:
                java_properties[key] = float(value.strip())
    assert java_tables[0].actual_heritability == pytest.approx(java_properties["Heritability"])
    assert java_tables[0].prevalence == pytest.approx(java_properties["K"])
    assert java_tables[0].edm == pytest.approx(java_properties["Ease-of-detection metric"])
    assert java_tables[0].odds_ratio == pytest.approx(java_properties["Odds ratio"])

    python_model_prefix = tmp_path / "pythonModelGenerated"
    python_model_doc = _doc_from_args(
        [
            "-M",
            f"-h 0.1 -p 0.5 -a 0.2 -a 0.4 -o {python_model_prefix}",
            "-q",
            "1",
            "-p",
            "20",
            "-t",
            "5000",
            "-r",
            "19",
        ]
    )
    run_document(python_model_doc)
    assert (tmp_path / "pythonModelGenerated_EDM_Scores.txt").read_bytes() == (
        tmp_path / "javaModel_EDM_Scores.txt"
    ).read_bytes()
    java_model_text = java_model_file.read_text(encoding="utf-8")
    python_model_text = (tmp_path / "pythonModelGenerated_Models.txt").read_text(encoding="utf-8")
    assert python_model_text.replace("pythonModelGenerated", "javaModel") == java_model_text

    python_dataset_prefix = tmp_path / "pythonFromJava"
    loaded_doc = _doc_from_args(
        [
            "-i",
            str(java_model_file),
            "-q",
            "1",
            "-D",
            f"-h hierarchical -n 0.01 -x 0.5 -a 6 -s 40 -w 40 -r 1 -o {python_dataset_prefix}",
            "-r",
            "31",
        ]
    )
    run_document(loaded_doc)
    python_dataset = tmp_path / "pythonFromJava_EDM-1" / "pythonFromJava_EDM-1_1.txt"
    assert python_dataset.exists()
    assert _read_first_line(python_dataset).endswith("M0P0\tM0P1\tClass")

    for dataset_file, expected_count in ((java_dataset, 800), (python_same_model_dataset, 800), (python_dataset, 80)):
        rows = dataset_file.read_text(encoding="utf-8").splitlines()
        assert len(rows) == expected_count + 1
        classes = [row.rsplit("\t", 1)[1] for row in rows[1:]]
        assert classes.count("1") == expected_count // 2
        assert classes.count("0") == expected_count // 2
        for row in rows[1:]:
            assert set(row.split("\t")[:-1]) <= {"0", "1", "2"}

    java_frequencies = _class_conditional_genotype_frequencies(java_dataset)
    python_frequencies = _class_conditional_genotype_frequencies(python_same_model_dataset)
    assert java_frequencies.keys() == python_frequencies.keys()
    for key in java_frequencies:
        assert python_frequencies[key] == pytest.approx(java_frequencies[key], abs=0.08)
