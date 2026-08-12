from __future__ import annotations

import math
from collections import deque
from dataclasses import dataclass
from enum import Enum
from pathlib import Path
from random import Random
from typing import List, Optional, Union


class ErrorState(str, Enum):
    NONE = "None"
    AMBIGUOUS = "Ambiguous"
    CONFLICT = "Conflict"


@dataclass
class BasisCell:
    value: float = 0.0
    which_penetrance_cell: int = -1
    is_set: bool = True


class CellId:
    def __init__(self, length_or_indices: Union[int, List[int], "CellId"], fill: int = 0):
        if isinstance(length_or_indices, CellId):
            self.indices = list(length_or_indices.indices)
        elif isinstance(length_or_indices, int):
            self.indices = [fill for _ in range(length_or_indices)]
        else:
            self.indices = list(length_or_indices)

    def clear(self) -> None:
        for i in range(len(self.indices)):
            self.indices[i] = 0

    def copy_from(self, other: "CellId") -> None:
        self.indices[:] = other.indices

    def from_master_index(self, snp_state_count: int, attribute_count: int, master_index: int) -> None:
        idx = master_index
        for i in range(attribute_count):
            self.indices[i] = idx % snp_state_count
            idx //= snp_state_count

    def get_index(self, dimension: int) -> int:
        return self.indices[dimension]

    def get_length(self) -> int:
        return len(self.indices)

    def matches_on_any_dimension(self, other: "CellId") -> bool:
        for i in range(len(other.indices)):
            if self.indices[i] == other.indices[i]:
                return True
        return False

    def set_index(self, dimension: int, index: int) -> None:
        self.indices[dimension] = index

    def to_master_index(self, snp_state_count: int) -> int:
        index = 0
        for i in range(len(self.indices) - 1, -1, -1):
            index = (index * snp_state_count) + self.indices[i]
        return index


class PenetranceCell:
    def __init__(self, value: Optional[float] = None, which_basis_element: Optional[int] = None):
        self.is_set = value is not None
        self.value = 0.0 if value is None else float(value)
        self.is_basis_element = which_basis_element is not None
        self.which_basis_element = -1 if which_basis_element is None else int(which_basis_element)

    def clear(self) -> None:
        self.is_set = False
        self.is_basis_element = False


class PenetranceCellWithId(PenetranceCell):
    def __init__(
        self,
        cell_id: CellId,
        value: Optional[float] = None,
        which_basis_element: Optional[int] = None,
        is_basis_element: bool = False,
    ):
        super().__init__(value=value, which_basis_element=which_basis_element)
        self.cell_id = CellId(cell_id)
        if is_basis_element:
            self.is_basis_element = True


class PenetranceTable:
    K_VALUE_MAX = 0.95
    PENETRANCE_SUM = 0.0
    ERROR_LIMIT = 0.01
    WHICH_PENETRANCE_CELL_NONE = -1

    fixed_conflict_successfully = 0
    fixed_conflict_unsuccessfully = 0

    def __init__(self, snp_state_count: int, attribute_count: int):
        self.snp_state_count = snp_state_count
        self.attribute_count = attribute_count
        self.use_point_method = attribute_count >= 6

        self.cell_count = 1
        self.basis_size = 1
        for _ in range(attribute_count):
            self.cell_count *= snp_state_count
            self.basis_size *= (snp_state_count - 1)

        self.attribute_names: List[str] = [f"P{i}" for i in range(attribute_count)]
        self.desired_heritability = 0.0
        self.actual_heritability = 0.0
        self.interacting_attribute_count = 0
        self.prevalence = 0.0
        self.desired_prevalence: Optional[float] = None
        self.edm = 0.0
        self.odds_ratio = 0.0
        self.use_origin_as_start = False

        self.minor_allele_frequencies = [0.0 for _ in range(attribute_count)]
        self.major_allele_frequencies = [0.0 for _ in range(attribute_count)]
        self.state_probability = [[0.0 for _ in range(snp_state_count)] for _ in range(attribute_count)]

        self.start_point = CellId(attribute_count)
        self.cells = [PenetranceCell() for _ in range(self.cell_count)]
        self.cell_case_count = [0 for _ in range(self.cell_count)]
        self.cell_control_count = [0 for _ in range(self.cell_count)]

        self.pending_cells_to_set: deque[PenetranceCellWithId] = deque()
        self.basis: List[Optional[BasisCell]] = [None for _ in range(self.basis_size)]
        self.basis_next = -1

        self.case_intervals: Optional[List[float]] = None
        self.control_intervals: Optional[List[float]] = None

        self.normalized = False
        self.row_sums_valid = False

        self.blocked_out_cell_for_point_method: Optional[CellId] = None
        self.next_master_cell_id_for_point_method = 0

        self.clear()

    def adjust_heritability(self) -> None:
        herit = self.calc_heritability()
        factor = math.sqrt(self.desired_heritability / herit)
        if factor > 1.0:
            self.normalized = False
            return

        for c in self.cells:
            c.value = (factor * c.value) + (self.prevalence * (1.0 - factor))
            c.is_set = True

        self.calc_and_set_heritability()
        self.edm = self.calc_edm()
        self.odds_ratio = self.calc_odds_ratio()
        self.normalized = True

    def adjust_prevalence(self) -> None:
        scale = 1.0
        offset = 0.0

        if self.desired_prevalence is not None and self.desired_prevalence != self.prevalence:
            if self.desired_prevalence < self.prevalence:
                scale = self.desired_prevalence / self.prevalence
            elif self.desired_prevalence > self.prevalence:
                scale = (1.0 - self.desired_prevalence) / (1.0 - self.prevalence)
                offset = (self.desired_prevalence - self.prevalence) / (1.0 - self.prevalence)

            for c in self.cells:
                c.value = (scale * c.value) + offset
                c.is_set = True

            self.calc_and_set_prevalence()

    def calc_and_set_edm(self) -> float:
        self.edm = self.calc_edm()
        return self.edm

    def calc_and_set_heritability(self) -> float:
        self.actual_heritability = self.calc_heritability()
        return self.actual_heritability

    def calc_and_set_odds_ratio(self) -> float:
        self.odds_ratio = self.calc_odds_ratio()
        return self.odds_ratio

    def calc_and_set_prevalence(self) -> float:
        self.prevalence = self.calc_prevalence()
        return self.prevalence

    def calc_edm(self) -> float:
        self.calc_and_set_prevalence()
        out_sum = 0.0
        cell_id = CellId(self.attribute_count)
        for i in range(self.cell_count):
            self.master_index_to_cell_id(i, cell_id)
            prob = self.get_probability_product(cell_id)
            diff = self.cells[i].value - self.prevalence
            out_sum += prob * prob * diff * diff
        k_prod = self.prevalence * (1.0 - self.prevalence)
        if abs(k_prod) < 1e-15:
            return math.nan
        return out_sum / (2.0 * k_prod * k_prod)

    def calc_heritability(self) -> float:
        self.calc_and_set_prevalence()
        out_sum = 0.0
        cell_id = CellId(self.attribute_count)
        for i in range(self.cell_count):
            self.master_index_to_cell_id(i, cell_id)
            prob = self.get_probability_product(cell_id)
            diff = self.cells[i].value - self.prevalence
            out_sum += prob * diff * diff
        denominator = self.prevalence * (1.0 - self.prevalence)
        if abs(denominator) < 1e-15:
            return math.nan
        return out_sum / denominator

    def calc_odds_ratio(self) -> float:
        self.calc_and_set_prevalence()
        sum_tp = 0.0
        sum_tn = 0.0
        sum_fp = 0.0
        sum_fn = 0.0
        cell_id = CellId(self.attribute_count)

        for i in range(self.cell_count):
            self.master_index_to_cell_id(i, cell_id)
            prob = self.get_probability_product(cell_id)
            prev = self.cells[i].value
            if prev >= self.prevalence:
                sum_tp += prob * prev
                sum_fp += prob * (1.0 - prev)
            else:
                sum_tn += prob * (1.0 - prev)
                sum_fn += prob * prev

        numerator = sum_tp * sum_tn
        denominator = sum_fn * sum_fp
        if abs(denominator) < 1e-15:
            return math.inf if numerator > 0.0 else math.nan
        return numerator / denominator

    def calc_marginal_prevalences(self) -> List[List[float]]:
        """Return P(disease | genotype) for each attribute and genotype state."""
        numerators = [[0.0 for _ in range(self.snp_state_count)] for _ in range(self.attribute_count)]
        cell_id = CellId(self.attribute_count)

        for master_index, cell in enumerate(self.cells):
            self.master_index_to_cell_id(master_index, cell_id)
            for fixed_dimension in range(self.attribute_count):
                state = cell_id.get_index(fixed_dimension)
                numerators[fixed_dimension][state] += self.get_probability_product(cell_id) * cell.value

        marginals: List[List[float]] = []
        for dimension, values in enumerate(numerators):
            dimension_values = []
            for state, numerator in enumerate(values):
                frequency = self.state_probability[dimension][state]
                dimension_values.append(numerator / frequency if frequency != 0.0 else math.nan)
            marginals.append(dimension_values)
        return marginals

    def calc_sampling_intervals(self) -> None:
        cell_id = CellId(self.attribute_count)
        sum_case = 0.0
        sum_control = 0.0
        self.case_intervals = [0.0 for _ in range(self.cell_count)]
        self.control_intervals = [0.0 for _ in range(self.cell_count)]

        for i in range(self.cell_count):
            self.master_index_to_cell_id(i, cell_id)
            prob = self.get_probability_product(cell_id)
            penetrance = self.get_penetrance_value(cell_id)

            sum_case += prob * penetrance
            sum_control += prob * (1.0 - penetrance)
            self.case_intervals[i] = sum_case
            self.control_intervals[i] = sum_control

        for i in range(self.cell_count):
            self.case_intervals[i] /= sum_case
            self.control_intervals[i] /= sum_control

    def cell_id_to_master_index(self, cell_id: CellId) -> int:
        return cell_id.to_master_index(self.snp_state_count)

    def check_row_sums(self, desired_row_sum: Optional[float] = None) -> bool:
        if desired_row_sum is None:
            desired_row_sum = self.prevalence

        cell_id = CellId(self.attribute_count)
        for which_dimension in range(self.attribute_count):
            for i in range(self.cell_count):
                self.master_index_to_cell_id(i, cell_id)
                if self._all_values_set_in_row(cell_id, which_dimension):
                    row_sum = self._calculate_weighted_sum_of_set_penetrance_values(cell_id, which_dimension)
                    if abs(row_sum - desired_row_sum) > self.ERROR_LIMIT:
                        self.row_sums_valid = False
                        return False

        self.row_sums_valid = True
        return True

    def clear(self) -> None:
        for c in self.cells:
            c.clear()
        for i in range(self.cell_count):
            self.cell_case_count[i] = 0
            self.cell_control_count[i] = 0

    def clear_penetrance_value(self, cell_id: CellId) -> None:
        idx = self.cell_id_to_master_index(cell_id)
        cell = self.cells[idx]
        if cell.is_basis_element:
            which = cell.which_basis_element
            if 0 <= which < len(self.basis) and self.basis[which] is not None:
                self.basis[which].which_penetrance_cell = self.WHICH_PENETRANCE_CELL_NONE
        cell.is_set = False
        cell.is_basis_element = False

    def count_remaining_empty_cells(self) -> int:
        return sum(1 for c in self.cells if not c.is_set)

    def generate_unnormalized(self, rng: Random) -> ErrorState:
        out_error = ErrorState.NONE

        if self.use_point_method:
            self.blocked_out_cell_for_point_method = CellId(self.attribute_count)
            self.master_index_to_cell_id(rng.randrange(self.cell_count), self.blocked_out_cell_for_point_method)
            self.next_master_cell_id_for_point_method = 0

        # Mirrors Java behavior where the current RNG is reseeded from its
        # signed nextLong value.
        if hasattr(rng, "next_long"):
            rng.seed(rng.next_long())
        else:
            rng.seed(rng.getrandbits(64))

        cell_id = CellId(self.attribute_count)
        while self._empty_cell_remaining():
            self._pick_next_empty_cell(rng, cell_id)
            error = self._set_random_penetrance_value_and_propagate_it(cell_id)
            if error is not ErrorState.NONE:
                out_error = error
                break

        return out_error

    def get_allele_frequencies(self, which_attribute: int, out_allele_frequencies: List[float]) -> None:
        maf = self.minor_allele_frequencies[which_attribute]
        aa = [0.0, 0.0, 0.0]
        self.calc_allele_frequencies(maf, aa)
        out_allele_frequencies[:] = aa

    def get_attribute_names(self) -> List[str]:
        return list(self.attribute_names)

    def get_minor_allele_frequencies(self) -> List[float]:
        return list(self.minor_allele_frequencies)

    def get_penetrance_value(self, cell_id: CellId) -> float:
        idx = self.cell_id_to_master_index(cell_id)
        return self.cells[idx].value

    def get_probability_product(self, cell_id: CellId) -> float:
        product = 1.0
        for dimension in range(self.attribute_count):
            product *= self.state_probability[dimension][cell_id.get_index(dimension)]
        return product

    def get_quantile_score(self, use_odds_ratio: bool) -> float:
        return self.odds_ratio if use_odds_ratio else self.edm

    def initialize(self, rng: Random, minor_allele_frequencies: List[float]) -> None:
        self.row_sums_valid = False
        self.normalized = False
        self.use_origin_as_start = False

        self.set_minor_allele_frequencies(minor_allele_frequencies)

        basis_squared_sum = 0.0
        for i in range(self.basis_size):
            value = rng.gauss(0.0, 1.0)
            self.basis[i] = BasisCell(value=value, which_penetrance_cell=self.WHICH_PENETRANCE_CELL_NONE, is_set=True)
            basis_squared_sum += value * value

        normalizing_factor = 1.0 / math.sqrt(basis_squared_sum)
        for i in range(self.basis_size):
            assert self.basis[i] is not None
            self.basis[i].value *= normalizing_factor

        self.basis_next = 0
        for i in range(self.attribute_count):
            if self.use_origin_as_start:
                self.start_point.set_index(i, 0)
            else:
                self.start_point.set_index(i, rng.randrange(self.snp_state_count))

    def master_index_to_cell_id(self, master_index: int, out_cell_id: CellId) -> None:
        out_cell_id.from_master_index(self.snp_state_count, self.attribute_count, master_index)

    def scale_to_unit_interval(self) -> None:
        max_v = self.cells[0].value
        min_v = self.cells[0].value
        for c in self.cells:
            if max_v < c.value:
                max_v = c.value
            if min_v > c.value:
                min_v = c.value

        self.prevalence = min_v / (min_v - max_v)
        if self.prevalence > self.K_VALUE_MAX:
            self.prevalence = self.K_VALUE_MAX

        slope = -self.prevalence / min_v
        for c in self.cells:
            c.value = (slope * c.value) + self.prevalence
            c.is_set = True

    def set_attribute_names(self, attribute_names: List[str]) -> None:
        self.attribute_names = list(attribute_names)

    def set_minor_allele_frequencies(self, in_minor_allele_frequencies: List[float]) -> None:
        for i in range(self.attribute_count):
            self.minor_allele_frequencies[i] = in_minor_allele_frequencies[i]
            self.major_allele_frequencies[i] = 1.0 - in_minor_allele_frequencies[i]

        comb = 1
        for j in range(self.snp_state_count):
            for i in range(self.attribute_count):
                self.state_probability[i][j] = (
                    comb
                    * (self.minor_allele_frequencies[i] ** j)
                    * (self.major_allele_frequencies[i] ** (self.snp_state_count - j - 1))
                )
            comb *= self.snp_state_count - 1 - j
            comb //= j + 1

    def set_penetrance_value(self, cell_id: CellId, value: float) -> None:
        idx = self.cell_id_to_master_index(cell_id)
        self.cells[idx].value = value
        self.cells[idx].is_set = True

    def write(self, delimiter: str = "\t") -> str:
        lines: List[str] = []
        current = ""
        for i in range(self.cell_count):
            if i > 0:
                if (i % self.snp_state_count) == 0:
                    lines.append(current)
                    current = ""
                if (i % (self.snp_state_count * self.snp_state_count)) == 0:
                    lines.append("")
            current += f"{self.cells[i].value}"
            if ((i + 1) % self.snp_state_count) != 0:
                current += delimiter
        lines.append(current)
        return "\n".join(lines)

    def write_with_stats(self, save_unnormalized: bool = False) -> str:
        lines = []
        lines.append("Attribute names:\t" + "\t".join(self.attribute_names))
        lines.append("Minor allele frequencies:\t" + "\t".join(str(x) for x in self.minor_allele_frequencies))

        if (not self.normalized) and (not save_unnormalized):
            lines.append("Failed to normalize penetrance table!")
            return "\n".join(lines)

        lines.append(f"K: {self.prevalence}")
        lines.append(f"Heritability: {self.actual_heritability}")
        lines.append(f"Ease-of-detection metric: {self.edm}")
        lines.append(f"Odds ratio: {self.odds_ratio}")

        if self.normalized:
            lines.append("Table has passed the row-sum test." if self.row_sums_valid else "Table has FAILED the row-sum test.")
        else:
            lines.append("Table is NOT normalized.")

        lines.append("")
        lines.append("Table:")
        lines.append("")
        lines.append(self.write(delimiter=",  "))

        if (not self.normalized) and save_unnormalized:
            write_basis = any(b is not None for b in self.basis)
            if write_basis:
                lines.append("")
                lines.append("")
                lines.append("Basis:")
                lines.append("")
                for b in self.basis:
                    if b is not None:
                        lines.append(f"{b.which_penetrance_cell}, {b.value}")

        return "\n".join(lines)

    def save_to_file(self, dest_file: Path, append: bool, save_unnormalized: bool) -> None:
        dest_file.parent.mkdir(parents=True, exist_ok=True)
        mode = "a" if append else "w"
        with dest_file.open(mode, encoding="utf-8") as f:
            if append:
                f.write("\n\n\n\n")
            f.write(self.write_with_stats(save_unnormalized))

    def _all_values_set_in_row(self, cell_id: CellId, which_dimension: int) -> bool:
        return self._count_filled_cells(cell_id, which_dimension, None) == self.snp_state_count

    def _calc_prevalence(self) -> float:
        out_prev = 0.0
        cell_id = CellId(self.attribute_count)
        for i in range(self.cell_count):
            self.master_index_to_cell_id(i, cell_id)
            prob = self.get_probability_product(cell_id)
            out_prev += prob * self.cells[i].value
        return out_prev

    def _calculate_forced_penetrance_value(self, cell_id: CellId, which_dimension: int) -> float:
        where_along_dim = cell_id.get_index(which_dimension)
        row_sum = self._calculate_weighted_sum_of_set_penetrance_values(cell_id, which_dimension)
        out_val = (self.PENETRANCE_SUM - row_sum) / self.state_probability[which_dimension][where_along_dim]

        # Debug parity with Java
        self.set_penetrance_value(cell_id, out_val)
        _ = self._calculate_weighted_sum_of_set_penetrance_values(cell_id, which_dimension)
        self.clear_penetrance_value(cell_id)

        return out_val

    def _calculate_weighted_sum_of_set_penetrance_values(self, cell_id: CellId, which_dimension: int) -> float:
        tmp = CellId(cell_id)
        out_sum = 0.0
        for i in range(self.snp_state_count):
            tmp.set_index(which_dimension, i)
            if self._get_penetrance_is_set(tmp):
                out_sum += self.state_probability[which_dimension][i] * self.get_penetrance_value(tmp)
        return out_sum

    def _count_filled_cells(self, which_cell: CellId, which_dimension: int, out_empty_cell_id: Optional[CellId]) -> int:
        filled = 0
        tmp = CellId(which_cell)
        for j in range(self.snp_state_count):
            tmp.set_index(which_dimension, j)
            if self._get_penetrance_is_set(tmp):
                filled += 1
            elif out_empty_cell_id is not None:
                out_empty_cell_id.copy_from(tmp)
        return filled

    def _empty_cell_remaining(self) -> bool:
        return any(not c.is_set for c in self.cells)

    def _get_penetrance_is_set(self, cell_id: CellId) -> bool:
        idx = self.cell_id_to_master_index(cell_id)
        return self.cells[idx].is_set

    def _pick_next_empty_cell(self, rng: Random, out_cell_id: CellId) -> None:
        if self.use_point_method:
            assert self.blocked_out_cell_for_point_method is not None
            found = False
            while self.next_master_cell_id_for_point_method < self.cell_count:
                self.master_index_to_cell_id(self.next_master_cell_id_for_point_method, out_cell_id)
                self.next_master_cell_id_for_point_method += 1
                if not self.blocked_out_cell_for_point_method.matches_on_any_dimension(out_cell_id):
                    found = True
                    break
            if not found:
                raise RuntimeError("Unable to find an empty cell that works")
            return

        attempts = 0
        while True:
            master_index = rng.randrange(self.cell_count)
            if not self.cells[master_index].is_set:
                self.master_index_to_cell_id(master_index, out_cell_id)
                return
            attempts += 1
            if attempts > 10000:
                raise RuntimeError("Unable to find an empty cell that works")

    def save_penetrance_cell(self, in_cell: PenetranceCellWithId) -> None:
        idx = self.cell_id_to_master_index(in_cell.cell_id)
        self.cells[idx] = PenetranceCell(value=in_cell.value)
        self.cells[idx].is_basis_element = in_cell.is_basis_element
        self.cells[idx].which_basis_element = in_cell.which_basis_element
        if in_cell.is_basis_element:
            which = in_cell.which_basis_element
            if which >= 0 and self.basis[which] is not None:
                self.basis[which].which_penetrance_cell = idx

    def _set_random_penetrance_value_and_propagate_it(self, cell_id: CellId) -> ErrorState:
        out_error = ErrorState.NONE
        empty_cell_id = CellId(self.attribute_count)

        self.pending_cells_to_set.append(PenetranceCellWithId(cell_id, is_basis_element=True))

        while self.pending_cells_to_set:
            curr = self.pending_cells_to_set.popleft()

            if self._get_penetrance_is_set(curr.cell_id):
                continue

            if curr.is_basis_element:
                if self.basis_next >= self.basis_size:
                    return ErrorState.AMBIGUOUS
                basis_cell = self.basis[self.basis_next]
                assert basis_cell is not None
                curr.value = basis_cell.value
                curr.is_set = True
                curr.which_basis_element = self.basis_next
                curr.is_basis_element = True
                self.basis_next += 1

            self.save_penetrance_cell(curr)

            for which_dimension in range(self.attribute_count):
                filled_cells = self._count_filled_cells(curr.cell_id, which_dimension, empty_cell_id)

                if filled_cells == self.snp_state_count:
                    row_sum = self._calculate_weighted_sum_of_set_penetrance_values(curr.cell_id, which_dimension)
                    if abs(row_sum - self.PENETRANCE_SUM) > self.ERROR_LIMIT:
                        return ErrorState.CONFLICT

                if filled_cells == (self.snp_state_count - 1):
                    forced = self._calculate_forced_penetrance_value(empty_cell_id, which_dimension)
                    self.pending_cells_to_set.append(PenetranceCellWithId(empty_cell_id, value=forced))

        return out_error

    @staticmethod
    def calc_allele_frequencies(maf: float, out_allele_frequencies: List[float]) -> None:
        out_allele_frequencies[0] = (1.0 - maf) * (1.0 - maf)
        out_allele_frequencies[1] = 2.0 * maf * (1.0 - maf)
        out_allele_frequencies[2] = maf * maf

    def calc_prevalence(self) -> float:
        return self._calc_prevalence()
