from __future__ import annotations

from pathlib import Path

from py_gametes.cli import main, run_document
from py_gametes.document import MixedModelDatasetType, SnpGenDocument


def _doc_from_args(args: list[str]) -> SnpGenDocument:
    doc = SnpGenDocument()
    show_gui = doc.parse_arguments(args)
    assert show_gui is False
    assert doc.run_document is True
    return doc


def _read_first_line(path: Path) -> str:
    with path.open("r", encoding="utf-8") as f:
        return f.readline().rstrip("\n")


def test_cli_main_no_args_prints_helpful_message(capsys) -> None:
    code = main([])
    out = capsys.readouterr().out
    assert code == 0
    assert "--gui" in out


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
