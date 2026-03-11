from __future__ import annotations

import math
from dataclasses import dataclass
from pathlib import Path
from random import Random
from typing import List, Optional, Sequence, TextIO

from .document import DocDataset, DocModel, InputException, MixedModelDatasetType, SnpGenDocument
from .penetrance_table import CellId, ErrorState, PenetranceCell, PenetranceTable


class ProcessingException(Exception):
    pass


@dataclass
class PenetranceTableQuantile:
    tables: List[PenetranceTable]


class SnpGenSimulator:
    ERROR_LIMIT = 0.01
    MAJOR_MAJOR = 0
    MAJOR_MINOR = 1
    MINOR_MINOR = 2
    ALLELE_SYMBOLS = [MAJOR_MAJOR, MAJOR_MINOR, MINOR_MINOR]

    ATTRIBUTE_TOKEN = "Attribute names:"
    FREQUENCY_TOKEN = "Minor allele frequencies:"
    TABLE_TOKEN = "Table:"

    def __init__(self) -> None:
        self.random = Random()
        self.penetrance_table_quantiles: Optional[List[PenetranceTableQuantile]] = None
        self.document: Optional[SnpGenDocument] = None
        self.table_population_count_found = 0

    def set_document(self, doc: SnpGenDocument) -> None:
        self.document = doc

    def set_random_seed(self, seed: Optional[int]) -> None:
        if seed is not None:
            self.random.seed(seed)

    def combine_model_tables_into_quantiles(self, model_list: List[DocModel], input_files: List[Path]) -> None:
        quantiles1: Optional[List[PenetranceTableQuantile]] = None
        quantiles2: Optional[List[PenetranceTableQuantile]] = None

        model_count = len(model_list)
        if model_count > 0:
            quantile_count_1 = len(model_list[0].penetrance_tables)
            for which_model in range(1, model_count):
                assert quantile_count_1 == len(model_list[which_model].penetrance_tables)

            quantiles1 = []
            for q in range(quantile_count_1):
                tables = []
                for m in range(model_count):
                    tables.append(model_list[m].penetrance_tables[q])
                quantiles1.append(PenetranceTableQuantile(tables=tables))

        if len(input_files) > 0:
            quantiles2 = self.parse_model_input_files(input_files)

        if quantiles1 is not None and quantiles2 is not None:
            self.penetrance_table_quantiles = self._merge_quantiles(quantiles1, quantiles2)
        elif quantiles1 is not None:
            self.penetrance_table_quantiles = quantiles1
        elif quantiles2 is not None:
            self.penetrance_table_quantiles = quantiles2
        else:
            self.penetrance_table_quantiles = []

    def fetch_tables(self, input_file: Path) -> List[PenetranceTable]:
        quantiles = self.parse_model_input_file(input_file)
        out_tables = []
        for q in quantiles:
            assert len(q.tables) == 1
            out_tables.append(q.tables[0])
        return out_tables

    def generate_datasets(self) -> None:
        if self.document is None:
            raise RuntimeError("Document is not set")
        if self.penetrance_table_quantiles is None:
            raise RuntimeError("Penetrance tables are not initialized")

        self.set_random_seed(self.document.random_seed)
        ex = self.document.verify_dataset_parameters()
        if ex is not None:
            raise ex

        if len(self.document.dataset_list) > 0:
            print("Generating datasets...")

        predictive_dataset = None
        if self.document.predictive_input_file is not None:
            predictive_dataset = self.parse_data_input_file(self.document.predictive_input_file)

        noise_dataset = None
        if self.document.noise_input_file is not None:
            noise_dataset = self.parse_data_input_file(self.document.noise_input_file)

        create_directories = len(self.document.dataset_list) > 1

        for dd in self.document.dataset_list:
            dest_file = dd.output_file
            directory: Optional[Path] = None
            dest_filename: Optional[str] = None

            if dest_file is not None:
                if create_directories:
                    directory = dest_file
                    directory.mkdir(parents=True, exist_ok=True)
                else:
                    directory = dest_file.parent
                    if directory:
                        directory.mkdir(parents=True, exist_ok=True)
                dest_filename = dest_file.name

            dataset_iteration_count = dd.replicate_count

            max_quantile_number_length = len(str(len(self.penetrance_table_quantiles)))
            max_dataset_number_length = len(str(dataset_iteration_count))

            for which_quantile, q in enumerate(self.penetrance_table_quantiles):
                quantile_name = str(which_quantile + 1).rjust(max_quantile_number_length, "0")

                subdirectory: Optional[Path] = None
                if dest_filename is not None and directory is not None:
                    subdirectory = directory / f"{dest_filename}_EDM-{quantile_name}"
                    subdirectory.mkdir(parents=True, exist_ok=True)

                for which_dataset in range(dataset_iteration_count):
                    dataset_name = str(which_dataset + 1).rjust(max_dataset_number_length, "0")
                    dataset_file: Optional[Path]
                    if subdirectory is not None and dest_filename is not None:
                        dataset_file = subdirectory / f"{dest_filename}_EDM-{quantile_name}_{dataset_name}.txt"
                    else:
                        dataset_file = None

                    header: List[str] = []

                    assert self.document.model_fractions is not None
                    assert len(q.tables) == len(self.document.model_fractions)

                    self.generate_and_save_dataset(
                        rng=self.random,
                        predictive_dataset=predictive_dataset,
                        noise_dataset=noise_dataset,
                        tables=q.tables,
                        dd=dd,
                        return_dataset=True,
                        dest_file=dataset_file,
                        out_header=header,
                        model_fractions=self.document.model_fractions,
                    )

                    if (
                        dd.heterogeneous_label_boolean
                        and dd.multiple_model_dataset_type != MixedModelDatasetType.hierarchical
                        and len(self.document.model_fractions) > 1
                        and dataset_file is not None
                    ):
                        instance_count = dd.total_count
                        case_prop = 0.0 if dd.case_proportion is None else dd.case_proportion
                        case_count = int(round(case_prop * instance_count))
                        self.add_model_label_to_het_output_case_control(dataset_file, self.document.model_fractions, case_count)

        if len(self.document.dataset_list) > 0:
            print("Done generating datasets.")

    def generate_penetrance_tables_for_model(
        self,
        model: DocModel,
        desired_table_count: int,
        try_count: int,
    ) -> List[PenetranceTable]:
        return self.generate_penetrance_tables(
            rng=self.random,
            desired_table_count=desired_table_count,
            tables_to_try_count=try_count,
            desired_heritability=model.heritability or 0.0,
            heritability_tolerance=-1,
            desired_prevalence=model.prevalence,
            attribute_count=model.attribute_count,
            attribute_names=model.get_attribute_names(),
            allele_frequencies=model.get_allele_frequencies(),
            use_odds_ratio=model.get_use_odds_ratio(),
        )

    def generate_penetrance_tables(
        self,
        rng: Random,
        desired_table_count: int,
        tables_to_try_count: int,
        desired_heritability: float,
        heritability_tolerance: float,
        desired_prevalence: Optional[float],
        attribute_count: int,
        attribute_names: Sequence[str],
        allele_frequencies: Sequence[float],
        use_odds_ratio: bool,
    ) -> List[PenetranceTable]:
        penetrance_table_list: List[PenetranceTable] = []

        PenetranceTable.fixed_conflict_successfully = 0
        PenetranceTable.fixed_conflict_unsuccessfully = 0

        for _ in range(tables_to_try_count):
            current = PenetranceTable(3, attribute_count)
            current.desired_heritability = desired_heritability
            current.desired_prevalence = desired_prevalence
            current.set_attribute_names(list(attribute_names))
            current.initialize(rng, list(allele_frequencies))
            error = current.generate_unnormalized(rng)

            if error in (ErrorState.AMBIGUOUS, ErrorState.CONFLICT):
                continue

            current.scale_to_unit_interval()
            current.adjust_prevalence()
            herit = current.calc_heritability()
            heritability_achieved = False

            if heritability_tolerance < 0:
                current.adjust_heritability()
                heritability_achieved = current.normalized
            elif desired_heritability != 0 and abs((herit - desired_heritability) / desired_heritability) < heritability_tolerance:
                current.adjust_heritability()
                heritability_achieved = current.normalized

            if heritability_achieved:
                current.check_row_sums()
                if current.row_sums_valid:
                    penetrance_table_list.append(current)
                if len(penetrance_table_list) >= desired_table_count:
                    break

        if use_odds_ratio:
            penetrance_table_list.sort(key=lambda t: t.odds_ratio)
        else:
            penetrance_table_list.sort(key=lambda t: t.edm)

        return penetrance_table_list

    def generate_tables_for_models(
        self,
        model_list: List[DocModel],
        desired_quantile_count: int,
        desired_population_count: int,
        try_count: int,
    ) -> List[List[float]]:
        model_count = len(model_list)
        if model_count > 0:
            print("Generating models...")

        all_scores: List[List[float]] = []
        for model in model_list:
            scores = self.generate_tables_for_one_model(
                model=model,
                desired_quantile_count=desired_quantile_count,
                desired_population_count=desired_population_count,
                try_count=try_count,
            )
            all_scores.append(scores)

        if model_count > 0:
            print("Done generating models.")

        return all_scores

    def generate_tables_for_one_model(
        self,
        model: DocModel,
        desired_quantile_count: int,
        desired_population_count: int,
        try_count: int,
    ) -> List[float]:
        if self.document is None:
            raise RuntimeError("Document is not set")

        self.set_random_seed(self.document.random_seed)

        tables = self.generate_penetrance_tables_for_model(
            model=model,
            desired_table_count=desired_population_count,
            try_count=try_count,
        )
        table_count = len(tables)

        if table_count < desired_quantile_count:
            raise ProcessingException("Unable to generate desired number of table quantiles")

        all_scores = [table.get_quantile_score(model.get_use_odds_ratio()) for table in tables]
        self.table_population_count_found = table_count

        self.select_penetrance_tables_representatives_uniformly(desired_quantile_count, tables, model)
        return all_scores

    def parse_model_input_file(self, input_file: Path) -> List[PenetranceTableQuantile]:
        tables: List[List[PenetranceTable]] = []
        current_sub_list: Optional[List[PenetranceTable]] = None

        with input_file.open("r", encoding="utf-8") as reader:
            while True:
                current_table = self._find_table(reader)
                if current_table is None:
                    break

                if current_sub_list is None or current_table.get_attribute_names() == current_sub_list[0].get_attribute_names():
                    current_sub_list = []
                    tables.append(current_sub_list)

                current_sub_list.append(current_table)
                self._parse_table(reader, current_table)

        quantile_count = len(tables)
        quantile_size = len(tables[0])
        for ptl in tables:
            if len(ptl) != quantile_size:
                raise InputException("Each quantile must have the same number of tables")

        out_quantiles = []
        for i in range(quantile_count):
            out_quantiles.append(PenetranceTableQuantile(tables=list(tables[i])))

        return out_quantiles

    def parse_model_input_files(self, input_files: List[Path]) -> List[PenetranceTableQuantile]:
        if len(input_files) == 0:
            return []

        out_quantiles = self.parse_model_input_file(input_files[0])
        for i in range(1, len(input_files)):
            new_quantiles = self.parse_model_input_file(input_files[i])
            out_quantiles = self._merge_quantiles(out_quantiles, new_quantiles)
        return out_quantiles

    def write_model_tables(self, model: DocModel, tables_file: Path, header: Optional[str], save_unnormalized: bool) -> None:
        tables_file.parent.mkdir(parents=True, exist_ok=True)
        with tables_file.open("w", encoding="utf-8") as stream:
            if header is not None:
                stream.write(header + "\n")

        for q, table in enumerate(model.penetrance_tables):
            if q < model.quantile_count_in_model:
                table.save_to_file(tables_file, append=True, save_unnormalized=save_unnormalized)

    def write_tables_and_scores_to_file(
        self,
        model_list: List[DocModel],
        all_table_scores: List[List[float]],
        quantile_count: int,
    ) -> None:
        table_population_count_found_minimum: Optional[int] = None

        for which_model, model in enumerate(model_list):
            score_name = self._calc_score_name(model)
            dest_file = model.file
            if dest_file is None:
                continue

            score_file = self._calc_combined_filename(dest_file, f"_{score_name}_Scores", "txt")
            population_scores = all_table_scores[which_model]
            table_population_count = len(population_scores)
            if table_population_count_found_minimum is None or table_population_count < table_population_count_found_minimum:
                table_population_count_found_minimum = table_population_count

            with score_file.open("w", encoding="utf-8") as score_stream:
                score_stream.write(f"{score_name} scores\n")
                for s in population_scores:
                    score_stream.write(f"{s}\n")

            tables_file = self._calc_combined_filename(dest_file, "_Models", "txt")
            assert quantile_count == model.quantile_count_in_model

            header = (
                f"Selected {quantile_count} {score_name} quantiles from a population of "
                f"{table_population_count_found_minimum} tables."
            )
            self.write_model_tables(model, tables_file, header, save_unnormalized=False)

    def select_penetrance_tables_representatives_uniformly(
        self,
        quantile_count: int,
        table_population: List[PenetranceTable],
        model: DocModel,
    ) -> None:
        target_ras = [0.0 for _ in range(quantile_count)]

        use_odds_ratio = model.get_use_odds_ratio()
        table_population_size = len(table_population)

        min_ras = table_population[0].get_quantile_score(use_odds_ratio)
        max_ras = table_population[table_population_size - 1].get_quantile_score(use_odds_ratio)

        if quantile_count == 1:
            target_ras[0] = (min_ras + max_ras) / 2.0
        else:
            delta = (max_ras - min_ras) / (quantile_count - 1)
            for which_quantile in range(quantile_count):
                target_ras[which_quantile] = min_ras + (which_quantile * delta)

        model_tables: List[Optional[PenetranceTable]] = [None for _ in range(quantile_count)]

        table_iter = 0
        quantile_iter = 0
        prior_matching_table = -1

        if quantile_count > 1:
            model_tables[quantile_iter] = table_population[table_iter]
            quantile_iter += 1
            table_iter += 1
            prior_matching_table = 0

        for table_iter in range(table_iter, table_population_size):
            if table_population[table_iter].get_quantile_score(use_odds_ratio) > target_ras[quantile_iter]:
                if (
                    table_iter > 0
                    and abs(table_population[table_iter - 1].get_quantile_score(use_odds_ratio) - target_ras[quantile_iter])
                    < abs(table_population[table_iter].get_quantile_score(use_odds_ratio) - target_ras[quantile_iter])
                ):
                    matching_table = table_iter - 1
                else:
                    matching_table = table_iter

                if matching_table == prior_matching_table:
                    if matching_table == (table_population_size - 1):
                        break
                    matching_table += 1

                model_tables[quantile_iter] = table_population[matching_table]
                prior_matching_table = matching_table
                quantile_iter += 1

                if quantile_iter >= quantile_count:
                    break

        quantile_iter = quantile_count - 1
        if model_tables[quantile_iter] is None:
            table_iter = table_population_size - 1
            model_tables[quantile_iter] = table_population[table_iter]
            quantile_iter -= 1
            table_iter -= 1
            while quantile_iter >= 0 and (
                model_tables[quantile_iter] is None or model_tables[quantile_iter] == model_tables[quantile_iter + 1]
            ):
                model_tables[quantile_iter] = table_population[table_iter]
                quantile_iter -= 1
                table_iter -= 1

        model.set_penetrance_tables([t for t in model_tables if t is not None])

    @staticmethod
    def parse_data_input_file(input_file: Path, out_header: Optional[List[str]] = None) -> List[List[int]]:
        lines: List[str] = []

        with input_file.open("r", encoding="utf-8") as reader:
            first = reader.readline()
            if first:
                first = first.rstrip("\n")
                is_numeric = True
                for c in first:
                    if c not in "0123456789 \t":
                        is_numeric = False
                        break

                if is_numeric:
                    lines.append(first)
                elif out_header is not None:
                    out_header.append(first)

                for line in reader:
                    lines.append(line.rstrip("\n"))

        if not lines:
            return []

        out_dataset: List[List[int]] = []
        for line in lines:
            out_dataset.append([int(x) for x in line.split("\t")])
        return out_dataset

    @classmethod
    def generate_and_save_dataset(
        cls,
        rng: Random,
        predictive_dataset: Optional[List[List[int]]],
        noise_dataset: Optional[List[List[int]]],
        tables: List[PenetranceTable],
        dd: DocDataset,
        return_dataset: bool,
        dest_file: Optional[Path],
        out_header: List[str],
        model_fractions: List[float],
    ) -> Optional[List[List[int]]]:
        attribute_count_predictive_from_tables = sum(t.attribute_count for t in tables)

        attribute_count_predictive_from_file = 0
        if predictive_dataset is not None and len(predictive_dataset) > 0:
            attribute_count_predictive_from_file = len(predictive_dataset[0]) - 1

        predictive_attribute_count = attribute_count_predictive_from_tables + attribute_count_predictive_from_file

        attribute_count_noise_file = 0
        if noise_dataset is not None and len(noise_dataset) > 0:
            attribute_count_noise_file = len(noise_dataset[0])

        total_attribute_count = dd.total_attribute_count

        if noise_dataset is not None:
            total_attribute_count = predictive_attribute_count + attribute_count_noise_file
            attribute_count_noise_generated = 0
            instance_count = len(noise_dataset)
        else:
            attribute_count_noise_generated = total_attribute_count - predictive_attribute_count - attribute_count_noise_file
            instance_count = dd.total_count

        noise_attribute_count = attribute_count_noise_generated + attribute_count_noise_file
        assert total_attribute_count == (predictive_attribute_count + noise_attribute_count)

        output_array: Optional[List[List[int]]] = None
        if return_dataset:
            output_array = [[0 for _ in range(total_attribute_count + 1)] for _ in range(dd.total_count)]

        output_stream: Optional[TextIO] = None
        try:
            if dest_file is not None:
                dest_file.parent.mkdir(parents=True, exist_ok=True)
                output_stream = dest_file.open("w", encoding="utf-8")

            for i in range(noise_attribute_count):
                if output_stream is not None:
                    output_stream.write(f"N{i}\t")
                out_header.append(f"N{i}\t")

            if predictive_dataset is not None and len(predictive_dataset) > 0:
                for i in range(len(predictive_dataset[0]) - 1):
                    attribute_name = f"P{attribute_count_predictive_from_tables + 1 + i}"
                    if output_stream is not None:
                        output_stream.write(attribute_name + "\t")
                    out_header.append(attribute_name + "\t")

            for i, t in enumerate(tables):
                for n in t.get_attribute_names():
                    name = f"M{i}{n}"
                    if output_stream is not None:
                        output_stream.write(name + "\t")
                    out_header.append(name + "\t")

            if output_stream is not None:
                output_stream.write("Class\n")
            out_header.append("Class")

            allele_frequencies = [[0.0, 0.0, 0.0] for _ in range(attribute_count_noise_generated)]
            allele_frequency_min = dd.allele_frequency_min
            allele_frequency_range = dd.allele_frequency_max - allele_frequency_min
            for i in range(attribute_count_noise_generated):
                maf = (rng.random() * allele_frequency_range) + allele_frequency_min
                PenetranceTable.calc_allele_frequencies(maf, allele_frequencies[i])

            if dd.create_continuous_endpoints:
                table_count = len(tables)
                genotype_intervals: List[List[float]] = []

                for j in range(table_count):
                    cell_id = CellId(tables[j].attribute_count)
                    sum_genotype_fractions = 0.0
                    intervals = [0.0 for _ in range(tables[j].cell_count)]

                    for i in range(tables[j].cell_count):
                        tables[j].master_index_to_cell_id(i, cell_id)
                        prob = tables[j].get_probability_product(cell_id)
                        sum_genotype_fractions += prob
                        intervals[i] = sum_genotype_fractions

                    assert abs(sum_genotype_fractions - 1.0) < cls.ERROR_LIMIT
                    genotype_intervals.append(intervals)

                for t in tables:
                    t.clear()

                cls.print_instances(
                    dd,
                    rng,
                    predictive_dataset,
                    noise_dataset,
                    0,
                    tables,
                    attribute_count_noise_generated,
                    allele_frequencies,
                    1,
                    dd.total_count,
                    genotype_intervals,
                    output_stream,
                    output_array,
                    0,
                    model_fractions,
                )
            else:
                table_count = len(tables)
                case_intervals: List[List[float]] = []
                control_intervals: List[List[float]] = []

                for j in range(table_count):
                    cell_id = CellId(tables[j].attribute_count)
                    sum_case_fractions = 0.0
                    sum_control_fractions = 0.0
                    c_intervals = [0.0 for _ in range(tables[j].cell_count)]
                    ctl_intervals = [0.0 for _ in range(tables[j].cell_count)]

                    for i in range(tables[j].cell_count):
                        tables[j].master_index_to_cell_id(i, cell_id)
                        prob = tables[j].get_probability_product(cell_id)
                        penetrance = tables[j].get_penetrance_value(cell_id)

                        sum_case_fractions += prob * penetrance
                        sum_control_fractions += prob * (1.0 - penetrance)
                        c_intervals[i] = sum_case_fractions
                        ctl_intervals[i] = sum_control_fractions

                    assert abs((sum_case_fractions + sum_control_fractions) - 1.0) < cls.ERROR_LIMIT

                    for i in range(tables[j].cell_count):
                        c_intervals[i] /= sum_case_fractions
                        ctl_intervals[i] /= sum_control_fractions

                    case_intervals.append(c_intervals)
                    control_intervals.append(ctl_intervals)

                for t in tables:
                    t.clear()

                case_count = int(round((dd.case_proportion or 0.0) * instance_count))
                control_count = instance_count - case_count

                cls.print_instances(
                    dd,
                    rng,
                    predictive_dataset,
                    noise_dataset,
                    0,
                    tables,
                    attribute_count_noise_generated,
                    allele_frequencies,
                    1,
                    case_count,
                    case_intervals,
                    output_stream,
                    output_array,
                    0,
                    model_fractions,
                )

                cls.print_instances(
                    dd,
                    rng,
                    predictive_dataset,
                    noise_dataset,
                    case_count,
                    tables,
                    attribute_count_noise_generated,
                    allele_frequencies,
                    0,
                    control_count,
                    control_intervals,
                    output_stream,
                    output_array,
                    case_count,
                    model_fractions,
                )

        finally:
            if output_stream is not None:
                output_stream.close()

        return output_array

    @classmethod
    def noise_to_output(
        cls,
        rng: Random,
        allele_frequencies: Sequence[float],
        output_stream: Optional[TextIO],
        output_array: Optional[List[List[int]]],
        which_output_line: int,
        which_output_column: int,
    ) -> int:
        rand = rng.random()

        if rand < allele_frequencies[0]:
            out_which = 0
            cls.value_to_output(
                cls.MAJOR_MAJOR,
                None,
                output_stream,
                True,
                output_array,
                which_output_line,
                which_output_column,
            )
        elif rand < (allele_frequencies[0] + allele_frequencies[1]):
            out_which = 1
            cls.value_to_output(
                cls.MAJOR_MINOR,
                None,
                output_stream,
                True,
                output_array,
                which_output_line,
                which_output_column,
            )
        else:
            out_which = 2
            cls.value_to_output(
                cls.MINOR_MINOR,
                None,
                output_stream,
                True,
                output_array,
                which_output_line,
                which_output_column,
            )

        return out_which

    @classmethod
    def print_instances(
        cls,
        dd: DocDataset,
        rng: Random,
        predictive_dataset: Optional[List[List[int]]],
        noise_dataset: Optional[List[List[int]]],
        which_first_noise: int,
        tables: List[PenetranceTable],
        noise_attribute_count: int,
        allele_frequencies: List[List[float]],
        instance_class: int,
        instance_count: int,
        instance_intervals: List[List[float]],
        output_stream: Optional[TextIO],
        output_array: Optional[List[List[int]]],
        first_output_line: int,
        model_fractions: List[float],
    ) -> None:
        which_output_line = first_output_line

        predictive_dataset_attribute_count = 0
        if predictive_dataset is not None and len(predictive_dataset) > 0:
            predictive_dataset_attribute_count = len(predictive_dataset[0]) - 1
        which_predictive = 0

        noise_dataset_attribute_count = 0
        if noise_dataset is not None and len(noise_dataset) > 0:
            noise_dataset_attribute_count = len(noise_dataset[0])
        which_noise = which_first_noise

        sum_table_fractions = sum(model_fractions)
        if abs(sum_table_fractions - 1.0) >= 1e-5:
            raise ValueError(
                "sum of model weights should be 1 but is: "
                f"{sum_table_fractions} table weights: {model_fractions}"
            )

        for row in range(instance_count):
            heterogeneous_current_table: Optional[int] = None

            if dd.multiple_model_dataset_type == MixedModelDatasetType.heterogeneous:
                row_fraction = float(row) / float(instance_count)
                table_fraction_before = 0.0
                for k in range(len(tables)):
                    current_table_fraction = model_fractions[k]
                    if row_fraction < (table_fraction_before + current_table_fraction):
                        heterogeneous_current_table = k
                        break
                    table_fraction_before += current_table_fraction

            dest_which = 0

            if noise_dataset is not None and len(noise_dataset) > 0:
                if which_noise >= len(noise_dataset):
                    raise Exception("Not enough noise input data")

                for j in range(noise_dataset_attribute_count):
                    cls.value_to_output(
                        noise_dataset[which_noise][j],
                        None,
                        output_stream,
                        True,
                        output_array,
                        which_output_line,
                        dest_which,
                    )
                    dest_which += 1

                which_noise += 1

            for j in range(noise_attribute_count):
                cls.noise_to_output(
                    rng,
                    allele_frequencies[j],
                    output_stream,
                    output_array,
                    which_output_line,
                    dest_which,
                )
                dest_which += 1

            if predictive_dataset is not None and len(predictive_dataset) > 0:
                while (
                    which_predictive < len(predictive_dataset)
                    and predictive_dataset[which_predictive][predictive_dataset_attribute_count] != instance_class
                ):
                    which_predictive += 1

                if which_predictive >= len(predictive_dataset):
                    raise Exception("Not enough predictive input data")

                for j in range(predictive_dataset_attribute_count):
                    cls.value_to_output(
                        predictive_dataset[which_predictive][j],
                        None,
                        output_stream,
                        True,
                        output_array,
                        which_output_line,
                        dest_which,
                    )
                    dest_which += 1

                which_predictive += 1

            phenotype_value = 0.0

            for which_table, table in enumerate(tables):
                cell_id = CellId(table.attribute_count)

                if (
                    dd.multiple_model_dataset_type == MixedModelDatasetType.heterogeneous
                    and which_table != heterogeneous_current_table
                ):
                    allele = [0.0, 0.0, 0.0]
                    for j in range(table.attribute_count):
                        table.get_allele_frequencies(j, allele)
                        which_value = cls.noise_to_output(
                            rng,
                            allele,
                            output_stream,
                            output_array,
                            which_output_line,
                            dest_which,
                        )
                        dest_which += 1
                        cell_id.set_index(j, which_value)

                    which_cell = cell_id.to_master_index(3)
                    if instance_class == 1:
                        table.cell_case_count[which_cell] += 1
                    else:
                        table.cell_control_count[which_cell] += 1

                else:
                    rand = rng.random()
                    which_cell = -1
                    for k in range(table.cell_count):
                        if rand < instance_intervals[which_table][k]:
                            which_cell = k
                            break

                    if dd.create_continuous_endpoints:
                        penetrance_for_cell = table.cells[which_cell].value
                        next_gaussian = rng.gauss(0.0, 1.0)
                        continuous_endpoint = (next_gaussian * (dd.continuous_endpoints_standard_deviation or 0.0)) + penetrance_for_cell

                        if dd.multiple_model_dataset_type == MixedModelDatasetType.heterogeneous:
                            phenotype_value = continuous_endpoint
                        else:
                            weighted_continuous_endpoint = continuous_endpoint * model_fractions[which_table]
                            phenotype_value += weighted_continuous_endpoint
                    else:
                        phenotype_value = float(instance_class)

                    if instance_class == 1:
                        table.cell_case_count[which_cell] += 1
                    else:
                        table.cell_control_count[which_cell] += 1

                    table.master_index_to_cell_id(which_cell, cell_id)
                    for k in range(table.attribute_count):
                        allele_symbol = cls.ALLELE_SYMBOLS[cell_id.get_index(k)]
                        cls.value_to_output(
                            allele_symbol,
                            None,
                            output_stream,
                            True,
                            output_array,
                            which_output_line,
                            dest_which,
                        )
                        dest_which += 1

            instance_class_repr = cls._format_ten_decimals(phenotype_value)
            cls.value_to_output(
                instance_class,
                instance_class_repr,
                output_stream,
                False,
                output_array,
                which_output_line,
                dest_which,
            )

            if output_stream is not None:
                output_stream.write("\n")

            which_output_line += 1

    @staticmethod
    def value_to_output(
        value: int,
        value_string: Optional[str],
        output_stream: Optional[TextIO],
        tab_after: bool,
        output_array: Optional[List[List[int]]],
        which_output_line: int,
        which_output_column: int,
    ) -> None:
        if output_stream is not None:
            output_stream.write(value_string if value_string is not None else str(value))
            if tab_after:
                output_stream.write("\t")

        if output_array is not None:
            output_array[which_output_line][which_output_column] = value

    @staticmethod
    def read_in_2d_table_from_text(dest_file: Path) -> List[List[str]]:
        lines = dest_file.read_text(encoding="utf-8").splitlines()
        out = []
        for line in lines:
            out.append(line.split("\t"))
        return out

    @classmethod
    def add_model_label_to_het_output_case_control(
        cls,
        dest_file: Path,
        model_proportions: List[float],
        case_count: int,
    ) -> List[List[str]]:
        output_array = cls.read_in_2d_table_from_text(dest_file)
        first_column: List[Optional[str]] = [None for _ in range(len(output_array))]

        sum_props = sum(model_proportions)
        normalized_props = [p / sum_props for p in model_proportions]

        cumulative_model_proportions = []
        total_sum = 0.0
        for p in normalized_props:
            total_sum += p
            cumulative_model_proportions.append(total_sum)

        for case_iter in range(case_count):
            current_row_prop = float(case_iter) / float(case_count if case_count > 0 else 1)
            desired_index = -1
            for idx, p in enumerate(cumulative_model_proportions):
                if current_row_prop < p:
                    desired_index = idx
                    break
            first_column[case_iter + 1] = f"Model_{desired_index}"

        control_total = len(output_array) - case_count - 1
        for control_iter in range(control_total):
            denom = control_total if control_total > 0 else 1
            current_row_prop = float(control_iter) / float(denom)
            desired_index = -1
            for idx, p in enumerate(cumulative_model_proportions):
                if current_row_prop < p:
                    desired_index = idx
                    break
            first_column[control_iter + case_count + 1] = f"Model_{desired_index}"

        first_column[0] = "Model"

        num_rows = len(output_array)
        num_cols = len(output_array[0]) if output_array else 0
        new_output_array = [["" for _ in range(num_cols + 1)] for _ in range(num_rows)]

        for r in range(num_rows):
            new_output_array[r][0] = first_column[r] or ""

        for r in range(num_rows):
            for c in range(num_cols):
                new_output_array[r][c + 1] = output_array[r][c]

        dest_file_for_writing = dest_file.with_name(dest_file.stem + "_hetLabel.txt")
        with dest_file_for_writing.open("w", encoding="utf-8") as writer:
            for row in new_output_array:
                writer.write("\t".join(row))
                writer.write("\t\n")

        return new_output_array

    def _calc_combined_filename(self, dest_file: Path, sub_name: str, extension: str) -> Path:
        directory = dest_file.parent
        base_filename = dest_file.name
        if base_filename.lower().endswith(".txt"):
            base_filename = base_filename[:-4]
        return directory / f"{base_filename}{sub_name}.{extension}"

    @staticmethod
    def _calc_score_name(model: DocModel) -> str:
        return "OddsRatio" if model.get_use_odds_ratio() else "EDM"

    def _find_table(self, model_reader: TextIO) -> Optional[PenetranceTable]:
        while True:
            line = model_reader.readline()
            if not line:
                return None

            line = line.rstrip("\n")
            if line.lower().startswith(self.ATTRIBUTE_TOKEN.lower()):
                attribute_names = line[len(self.ATTRIBUTE_TOKEN) + 1 :].strip().split("\t")
                out_table = PenetranceTable(3, len(attribute_names))
                out_table.set_attribute_names(attribute_names)
                out_table.normalized = True
                return out_table

    def _merge_quantiles(
        self,
        quantiles_1: List[PenetranceTableQuantile],
        quantiles_2: List[PenetranceTableQuantile],
    ) -> List[PenetranceTableQuantile]:
        if len(quantiles_1) != len(quantiles_2):
            raise InputException("The generated models and the models from the file must have the same number of quantiles")

        out_quantiles: List[PenetranceTableQuantile] = []
        for i in range(len(quantiles_1)):
            out_quantiles.append(PenetranceTableQuantile(tables=list(quantiles_1[i].tables) + list(quantiles_2[i].tables)))
        return out_quantiles

    def _parse_table(self, model_reader: TextIO, table: PenetranceTable) -> None:
        while True:
            line = model_reader.readline()
            if not line:
                raise InputException("Got a table-header without a table")
            line = line.rstrip("\n")

            if line.lower().startswith(self.FREQUENCY_TOKEN.lower()):
                numbers = line[len(self.FREQUENCY_TOKEN) + 1 :].strip().split("\t")
                freqs = []
                for s in numbers:
                    try:
                        freqs.append(float(s.strip()))
                    except ValueError as exc:
                        raise InputException("Got a table with a non-numeric minor allele frequency") from exc
                table.set_minor_allele_frequencies(freqs)

            if line.lower().startswith(self.TABLE_TOKEN.lower()):
                break

        which_cell = 0
        while which_cell < table.cell_count:
            line = model_reader.readline()
            if not line:
                raise InputException("Got a table with too few cells")

            line = line.rstrip("\n")
            if len(line.strip()) == 0:
                continue

            numbers = line.split(",")
            for s in numbers:
                try:
                    cell_value = float(s.strip())
                except ValueError as exc:
                    raise InputException("Got a table with a non-numeric cell") from exc

                if which_cell >= table.cell_count:
                    raise InputException("Got a table with too many cells")

                table.cells[which_cell] = PenetranceCell(cell_value)
                which_cell += 1

        table.calc_and_set_heritability()

    @staticmethod
    def _format_ten_decimals(value: float) -> str:
        formatted = f"{value:.10f}".rstrip("0").rstrip(".")
        if formatted == "-0":
            return "0"
        return formatted if formatted else "0"
