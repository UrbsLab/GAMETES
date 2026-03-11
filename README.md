# GAMETES Python Port

This repository now includes a Python port of the GAMETES workflow with both CLI and GUI modes.

## Run

From the repo root:

```bash
python3 -m py_gametes --help
```

or:

```bash
python3 gametes_py.py --help
```

## GUI

Launch the desktop interface:

```bash
python3 -m py_gametes --gui
```

The GUI mirrors the Java workflow with:
- Model construction (add/edit/remove models, odds ratio option, per-model weights)
- Loaded model files (`-i`) with per-file weights
- Dataset construction (binary/continuous endpoint, hierarchical/heterogeneous mode)
- Predictive/noise input file selection (`-v`, `-z`)
- In-app generation log console

## Example: Generate One Model + One Dataset

```bash
python3 -m py_gametes \
  -M "-h 0.2 -p 0.3 -a 0.3 -a 0.2 -o /tmp/basicModel" \
  -q 1 -p 100 -t 5000 \
  -D "-n 0.01 -x 0.5 -a 20 -s 100 -w 100 -r 2 -o /tmp/myData" \
  -r 123
```

Outputs include:
- Model files: `<model_prefix>_Models.txt`, `<model_prefix>_EDM_Scores.txt` (or OddsRatio)
- Dataset folders: `<dataset_prefix>_EDM-<quantile>/...`

## Notes

- The GUI is implemented in Tkinter (not Swing) but follows the Java JAR’s same configuration flow.
- The implementation preserves model/dataset generation flow and file formats used by the JAR workflow.
