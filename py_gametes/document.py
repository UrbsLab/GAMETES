from __future__ import annotations

import argparse
import math
import shlex
from dataclasses import dataclass, field
from enum import Enum
from pathlib import Path
from typing import List, Optional


class InputException(Exception):
    pass


class MissingOptionException(Exception):
    pass


class MixedModelDatasetType(str, Enum):
    heterogeneous = "heterogeneous"
    hierarchical = "hierarchical"


@dataclass
class DocDataset:
    allele_frequency_min: float = 0.01
    allele_frequency_max: float = 0.5
    total_attribute_count: int = 100
    case_proportion: Optional[float] = 0.5
    replicate_count: int = 100
    output_file: Optional[Path] = None
    create_continuous_endpoints: bool = False
    continuous_endpoints_standard_deviation: Optional[float] = 0.2
    total_count: int = 800
    multiple_model_dataset_type: MixedModelDatasetType = MixedModelDatasetType.hierarchical
    heterogeneous_label_boolean: bool = False

    def get_case_count(self) -> int:
        return math.floor(((self.case_proportion or 0.0) * self.total_count) + 0.5)

    def get_control_count(self) -> int:
        return self.total_count - self.get_case_count()

    def verify_all_needed_parameters(self) -> Optional[Exception]:
        if self.total_count <= 0:
            return InputException("Dataset has no samples")
        if self.total_attribute_count <= 0:
            return InputException("Dataset has no attributes")
        if self.create_continuous_endpoints:
            if self.continuous_endpoints_standard_deviation is None:
                return InputException("No continuousEndpointsStandardDeviation specified")
        else:
            if self.case_proportion is None:
                return InputException("caseProportion (or case control count) not specified for binary class")
        return None


@dataclass
class DocModel:
    attribute_count: int
    model_id: str = ""
    heritability: Optional[float] = None
    prevalence: Optional[float] = None
    fraction: float = 1.0
    use_odds_ratio: bool = False
    file: Optional[Path] = None
    attribute_name_array: List[str] = field(default_factory=list)
    attribute_allele_frequency_array: List[float] = field(default_factory=list)
    penetrance_tables: List["PenetranceTable"] = field(default_factory=list)
    quantile_count_in_model: int = 0

    def __post_init__(self) -> None:
        if not self.attribute_name_array:
            self.attribute_name_array = [f"P{i}" for i in range(self.attribute_count)]
        if not self.attribute_allele_frequency_array:
            self.attribute_allele_frequency_array = [0.0 for _ in range(self.attribute_count)]

    def get_allele_frequencies(self) -> List[float]:
        return list(self.attribute_allele_frequency_array)

    def get_attribute_names(self) -> List[str]:
        return list(self.attribute_name_array)

    def set_penetrance_tables(self, penetrance_tables: List["PenetranceTable"]) -> None:
        self.penetrance_tables = penetrance_tables
        self.quantile_count_in_model = len(penetrance_tables)

    def get_use_odds_ratio(self) -> bool:
        return bool(self.use_odds_ratio)

    def verify_all_needed_parameters(self) -> Optional[Exception]:
        if self.model_id is None:
            return InputException("Missing a model ID")
        if self.attribute_count is None:
            return InputException("Missing an attribute-count")
        if self.heritability is None:
            return InputException("Missing a heritability")
        for name in self.attribute_name_array:
            if not name:
                return InputException("Missing an attribute name")
        for freq in self.attribute_allele_frequency_array:
            if freq is None:
                return InputException("Missing a minor-allele frequency")
        return None


@dataclass
class SnpGenDocument:
    model_fractions: Optional[List[float]] = None
    ras_quantile_count: int = 3
    ras_population_count: int = 1000
    ras_try_count: int = 100000
    model_list: List[DocModel] = field(default_factory=list)
    dataset_list: List[DocDataset] = field(default_factory=list)
    model_input_files: List[Path] = field(default_factory=list)
    predictive_input_file: Optional[Path] = None
    noise_input_file: Optional[Path] = None
    show_help: bool = False
    run_document: bool = False
    random_seed: Optional[int] = None
    first_dataset: Optional[DocDataset] = None

    def add_new_doc_dataset(self) -> DocDataset:
        dataset = DocDataset()
        self.dataset_list.append(dataset)
        return dataset

    def add_new_doc_model(
        self,
        attribute_count: int,
        model_id: str,
        attribute_names: Optional[List[str]] = None,
        attribute_allele_frequencies: Optional[List[float]] = None,
    ) -> DocModel:
        model = DocModel(attribute_count=attribute_count, model_id=model_id)
        if attribute_names is not None:
            model.attribute_name_array = list(attribute_names)
        if attribute_allele_frequencies is not None:
            model.attribute_allele_frequency_array = list(attribute_allele_frequencies)
        self.model_list.append(model)
        return model

    def parse_arguments(self, args: List[str]) -> bool:
        if len(args) == 0:
            self.run_document = False
            return True

        model_blobs: List[str] = []
        dataset_blobs: List[str] = []
        model_input_files: List[str] = []
        model_weights: List[float] = []
        predictive_input_file: Optional[str] = None
        noise_input_file: Optional[str] = None
        ras_quantile_count: Optional[int] = None
        ras_population_count: Optional[int] = None
        ras_try_count: Optional[int] = None
        random_seed: Optional[int] = None
        show_help = False

        i = 0
        while i < len(args):
            token = args[i]
            if token in ("-M", "--model"):
                if i + 1 >= len(args):
                    raise ValueError("argument -M/--model: expected one argument")
                model_blobs.append(args[i + 1])
                i += 2
                continue
            if token in ("-D", "--dataset"):
                if i + 1 >= len(args):
                    raise ValueError("argument -D/--dataset: expected one argument")
                dataset_blobs.append(args[i + 1])
                i += 2
                continue
            if token in ("-i", "--modelInputFile"):
                if i + 1 >= len(args):
                    raise ValueError("argument -i/--modelInputFile: expected one argument")
                model_input_files.append(args[i + 1])
                i += 2
                continue
            if token in ("-w", "--modelWeight"):
                if i + 1 >= len(args):
                    raise ValueError("argument -w/--modelWeight: expected one argument")
                model_weights.append(float(args[i + 1]))
                i += 2
                continue
            if token in ("-v", "--predictiveInputFile"):
                if i + 1 >= len(args):
                    raise ValueError("argument -v/--predictiveInputFile: expected one argument")
                predictive_input_file = args[i + 1]
                i += 2
                continue
            if token in ("-z", "--noiseInputFile"):
                if i + 1 >= len(args):
                    raise ValueError("argument -z/--noiseInputFile: expected one argument")
                noise_input_file = args[i + 1]
                i += 2
                continue
            if token in ("-q", "--rasQuantileCount"):
                if i + 1 >= len(args):
                    raise ValueError("argument -q/--rasQuantileCount: expected one argument")
                ras_quantile_count = int(args[i + 1])
                i += 2
                continue
            if token in ("-p", "--rasPopulationCount"):
                if i + 1 >= len(args):
                    raise ValueError("argument -p/--rasPopulationCount: expected one argument")
                ras_population_count = int(args[i + 1])
                i += 2
                continue
            if token in ("-t", "--rasTryCount"):
                if i + 1 >= len(args):
                    raise ValueError("argument -t/--rasTryCount: expected one argument")
                ras_try_count = int(args[i + 1])
                i += 2
                continue
            if token in ("-r", "--randomSeed"):
                if i + 1 >= len(args):
                    raise ValueError("argument -r/--randomSeed: expected one argument")
                random_seed = int(args[i + 1])
                i += 2
                continue
            if token in ("-h", "--help"):
                show_help = True
                i += 1
                continue
            raise ValueError(f"Unexpected top-level arguments: {[token]}")

        self.show_help = show_help
        self.model_input_files = [Path(x) for x in model_input_files]

        if model_weights:
            total = sum(model_weights)
            if total == 0:
                raise ValueError("Model weights sum to zero; provide at least one non-zero --modelWeight.")
            self.model_fractions = [w / total for w in model_weights]

        self.predictive_input_file = Path(predictive_input_file) if predictive_input_file else None
        self.noise_input_file = Path(noise_input_file) if noise_input_file else None
        self.ras_quantile_count = ras_quantile_count if ras_quantile_count is not None else 3
        self.ras_population_count = ras_population_count if ras_population_count is not None else 1000
        self.ras_try_count = ras_try_count if ras_try_count is not None else 100000
        self.random_seed = random_seed

        for dataset_blob in dataset_blobs:
            dataset = self._parse_dataset_blob(dataset_blob)
            self.dataset_list.append(dataset)
        if self.dataset_list:
            self.first_dataset = self.dataset_list[0]

        for model_blob in model_blobs:
            model = self._parse_model_blob(model_blob)
            self.model_list.append(model)

        total_models = len(self.model_list) + len(self.model_input_files)
        if total_models > 0:
            if self.model_fractions is None:
                self.model_fractions = [1.0 / total_models for _ in range(total_models)]
            elif len(self.model_fractions) != total_models:
                raise ValueError(
                    "# of models and # of dataset model fractions do not match! "
                    f"There are {total_models} total models ({len(self.model_list)} from new models and "
                    f"{len(self.model_input_files)} from model files) but --modelWeight has "
                    f"{len(self.model_fractions)} model fractions: {self.model_fractions}"
                )

        if self.show_help:
            self.print_option_help()

        out_show_gui = False
        self.run_document = (not self.show_help) and (not out_show_gui)
        return out_show_gui

    def _parse_dataset_blob(self, dataset_blob: str) -> DocDataset:
        parser = argparse.ArgumentParser(add_help=False, allow_abbrev=False)
        parser.add_argument("-n", "--alleleFrequencyMin", type=float)
        parser.add_argument("-x", "--alleleFrequencyMax", type=float)
        parser.add_argument("-a", "--totalAttributeCount", type=int)
        parser.add_argument("-t", "--totalCount", type=int)
        parser.add_argument("-s", "--caseCount", type=int)
        parser.add_argument("-w", "--controlCount", type=int)
        parser.add_argument("-r", "--replicateCount", type=int)
        parser.add_argument("-o", "--datasetOutputFile")
        parser.add_argument("-c", "--continuous", action="store_true")
        parser.add_argument(
            "-h",
            "--mixedModelDatasetType",
            choices=[x.value for x in MixedModelDatasetType],
        )
        parser.add_argument("-b", "--heteroLabel", action="store_true")
        parser.add_argument("-d", "--standardDeviation", type=float)
        parser.add_argument("-f", "--caseControlRatioBalanced", action="store_true")

        args = shlex.split(dataset_blob)
        ns, extra = parser.parse_known_args(args)
        if extra:
            raise ValueError(f"Unexpected argument passed into the --dataset: {extra}")

        dataset = DocDataset()
        dataset.allele_frequency_min = ns.alleleFrequencyMin if ns.alleleFrequencyMin is not None else 0.01
        dataset.allele_frequency_max = ns.alleleFrequencyMax if ns.alleleFrequencyMax is not None else 0.5
        dataset.total_attribute_count = ns.totalAttributeCount if ns.totalAttributeCount is not None else 100
        dataset.replicate_count = ns.replicateCount if ns.replicateCount is not None else 100
        dataset.create_continuous_endpoints = bool(ns.continuous)
        dataset.multiple_model_dataset_type = MixedModelDatasetType(
            ns.mixedModelDatasetType if ns.mixedModelDatasetType is not None else MixedModelDatasetType.hierarchical.value
        )
        dataset.heterogeneous_label_boolean = bool(ns.heteroLabel)

        if dataset.create_continuous_endpoints:
            dataset.continuous_endpoints_standard_deviation = (
                ns.standardDeviation if ns.standardDeviation is not None else 0.2
            )
            dataset.total_count = ns.totalCount if ns.totalCount is not None else 800
            if ns.caseCount is not None or ns.controlCount is not None:
                raise ValueError(
                    "For continuous datasets these should not be specified: --caseCount --controlCount --caseProportion"
                )
            dataset.case_proportion = None
        else:
            if ns.caseCount is not None:
                if ns.controlCount is None:
                    raise ValueError("Case count passed in but not control count")
                dataset.total_count = ns.caseCount + ns.controlCount
                dataset.case_proportion = ns.caseCount / float(dataset.total_count)
            elif ns.controlCount is not None:
                raise ValueError("control count passed in but not case count")
            else:
                dataset.total_count = ns.totalCount if ns.totalCount is not None else 800
                dataset.case_proportion = 0.5
            dataset.continuous_endpoints_standard_deviation = ns.standardDeviation

        dataset.output_file = Path(ns.datasetOutputFile) if ns.datasetOutputFile else None
        return dataset

    def _parse_model_blob(self, model_blob: str) -> DocModel:
        parser = argparse.ArgumentParser(add_help=False, allow_abbrev=False)
        parser.add_argument("-h", "--heritability", type=float)
        parser.add_argument("-p", "--caseProportion", type=float)
        parser.add_argument("-d", "--useOddsRatio", action="store_true")
        parser.add_argument("-a", "--attributeAlleleFrequency", type=float, action="append", default=[])
        parser.add_argument("-o", "--modelOutputFile")

        args = shlex.split(model_blob)
        ns, extra = parser.parse_known_args(args)
        if extra:
            raise ValueError(f"Unexpected argument passed into the --model: {extra}")

        if ns.heritability is None:
            raise MissingOptionException("--heritability")

        model_file = Path(ns.modelOutputFile) if ns.modelOutputFile else None
        model_name = model_file.name if model_file else ""

        model = DocModel(attribute_count=len(ns.attributeAlleleFrequency), model_id=model_name)
        model.file = model_file
        model.attribute_allele_frequency_array = list(ns.attributeAlleleFrequency)
        model.attribute_name_array = [f"P{i}" for i in range(len(ns.attributeAlleleFrequency))]
        model.heritability = ns.heritability
        model.prevalence = ns.caseProportion
        model.use_odds_ratio = bool(ns.useOddsRatio)
        return model

    def verify_dataset_parameters(self) -> Optional[Exception]:
        for dataset in self.dataset_list:
            err = dataset.verify_all_needed_parameters()
            if err is not None:
                return err
        return None

    def verify_model_parameters(self) -> Optional[Exception]:
        for model in self.model_list:
            err = model.verify_all_needed_parameters()
            if err is not None:
                return err
        return None

    def verify_all_needed_parameters(self) -> Optional[Exception]:
        err = self.verify_model_parameters()
        if err is None:
            err = self.verify_dataset_parameters()
        return err

    @staticmethod
    def print_option_help() -> None:
        print(
            "Usage: python -m py_gametes <program arguments>\n"
            "If there are no arguments, the desktop interface is opened.\n\n"
            "{-M,--model} Quoted model constraint string (repeatable)\n"
            "    {-h,--heritability} double\n"
            "    {-p,--caseProportion} double\n"
            "    {-d,--useOddsRatio} flag\n"
            "    {-a,--attributeAlleleFrequency} double (repeatable)\n"
            "    {-o,--modelOutputFile} string\n"
            "{-D,--dataset} Quoted dataset constraint string (repeatable)\n"
            "    {-n,--alleleFrequencyMin} double (default: 0.01)\n"
            "    {-x,--alleleFrequencyMax} double (default: 0.5)\n"
            "    {-a,--totalAttributeCount} integer (default: 100)\n"
            "    {-t,--totalCount} integer, continuous data (default: 800)\n"
            "    {-s,--caseCount} integer, binary data (default: 400)\n"
            "    {-w,--controlCount} integer, binary data (default: 400)\n"
            "    {-r,--replicateCount} integer (default: 100)\n"
            "    {-o,--datasetOutputFile} string\n"
            "    {-c,--continuous} flag\n"
            "    {-h,--mixedModelDatasetType} heterogeneous|hierarchical\n"
            "    {-b,--heteroLabel} flag\n"
            "    {-d,--standardDeviation} double (default: 0.2)\n"
            "{-i,--modelInputFile} string (repeatable)\n"
            "{-w,--modelWeight} double (repeat once per model)\n"
            "{-v,--predictiveInputFile} string\n"
            "{-z,--noiseInputFile} string\n"
            "{-q,--rasQuantileCount} integer (default: 3)\n"
            "{-p,--rasPopulationCount} integer (default: 1000)\n"
            "{-t,--rasTryCount} integer (default: 100000)\n"
            "{-r,--randomSeed} integer\n"
            "{-h,--help} Show this help"
        )


# Local import only for type-checking in this module.
from .penetrance_table import PenetranceTable  # noqa: E402  # isort:skip
