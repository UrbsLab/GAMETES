from __future__ import annotations

import sys
import traceback
from typing import List, Optional

from .document import SnpGenDocument
from .simulator import SnpGenSimulator


def run_document(doc: SnpGenDocument) -> None:
    param_error = doc.verify_all_needed_parameters()
    if param_error is not None:
        raise param_error

    simulator = SnpGenSimulator()
    simulator.set_document(doc)

    desired_quantile_count = doc.ras_quantile_count
    model_list = doc.model_list

    all_table_scores = simulator.generate_tables_for_models(
        model_list=model_list,
        desired_quantile_count=desired_quantile_count,
        desired_population_count=doc.ras_population_count,
        try_count=doc.ras_try_count,
    )

    simulator.write_tables_and_scores_to_file(model_list, all_table_scores, desired_quantile_count)
    simulator.combine_model_tables_into_quantiles(model_list, doc.model_input_files)
    simulator.generate_datasets()


def main(argv: Optional[List[str]] = None) -> int:
    args = sys.argv[1:] if argv is None else argv

    # The v2.2 JAR opens its desktop UI when invoked without arguments.
    # ``--gui`` is also accepted as an explicit Python convenience.
    if not args or "--gui" in args:
        args = [a for a in args if a != "--gui"]
        if args:
            print("`--gui` cannot be combined with other CLI arguments.", file=sys.stderr)
            return 2
        try:
            from .gui import launch_gui

            launch_gui()
            return 0
        except Exception as exc:
            print(f"Unable to launch GUI: {exc}", file=sys.stderr)
            return 1

    doc = SnpGenDocument()

    try:
        show_gui = doc.parse_arguments(args)
    except Exception as exc:
        print(str(exc), file=sys.stderr)
        traceback.print_exc()
        return 1

    if show_gui:  # Defensive: the no-argument case is handled above.
        return 0

    if doc.run_document:
        try:
            run_document(doc)
        except Exception as exc:
            print(str(exc), file=sys.stderr)
            traceback.print_exc()
            return 1

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
