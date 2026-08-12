# GAMETES 2.2 for Python

A standalone Python port of [UrbsLab GAMETES v2.2](https://github.com/UrbsLab/GAMETES/tree/v2.2), the Genetic Architecture Model Emulator for Testing and Evaluating Software.

The port preserves the Java v2.2 workflows:

- pure, strict epistatic penetrance-table generation
- EDM and odds-ratio model ranking
- representative quantile selection
- binary case/control datasets
- continuous endpoints
- additive (hierarchical) and heterogeneous multi-model datasets
- optional heterogeneous model labels
- predictive and noise input files
- Java-compatible model and dataset text formats
- a desktop model editor and dataset-generation UI
- the original nested command-line syntax

The scientific runtime uses only the Python standard library. Tkinter is used
for the desktop UI and `pytest` is an optional test dependency.

## Compatibility target

The reference is GAMETES tag `v2.2`, commit
`beb87749495802e2b2be9cc368de78293ad1a126`. See [UPSTREAM.md](UPSTREAM.md)
for the source mapping and reference JAR checksum.

Python's ordinary random generator is not compatible with Java. This port
therefore includes the exact `java.util.Random` 48-bit generator, including
Java's bounded integers, doubles, signed longs, cached Gaussian values, and
reseed behavior. A `-r/--randomSeed` run follows the JAR's random stream.

Model score files and binary/continuous datasets used in parity tests are
byte-for-byte identical to the reference JAR. Model files use the same layout
and are interchangeable in both directions. On some platforms, a generated
floating-point value can differ in its final machine-precision digit because
the Java and Python math runtimes are separate implementations.

The Tkinter UI reproduces the Swing application's controls and workflows.
Native widgets are rendered by the operating system, so it is behaviorally—not
pixel—identical to Swing.

## Install

Python 3.9 or newer is required.

```bash
cd gametes-python
python3 -m venv .venv
.venv/bin/pip install -e '.[test]'
```

Tkinter ships with many Python installations. Verify it with:

```bash
python3 -m tkinter
```

On Homebrew Python for macOS, install the matching Tk package if needed:

```bash
brew install python-tk@3.13
```

## Run the desktop app

Like the JAR, running GAMETES with no arguments opens the UI:

```bash
.venv/bin/gametes
```

These are equivalent:

```bash
.venv/bin/gametes --gui
.venv/bin/python -m py_gametes --gui
```

The GUI supports generated models, direct 2-locus and 3-locus penetrance-table
models, Java/Python model-file loading, model editing, multi-model weighting,
binary or continuous endpoints, additive or heterogeneous composition, input
noise files, replicates, and JSON session save/open.

## Run the CLI

The Python command keeps the JAR's nested quoted `-M` and `-D` arguments:

```bash
.venv/bin/gametes \
  -M "-h 0.2 -p 0.3 -a 0.3 -a 0.2 -o basicModel" \
  -q 1 \
  -p 30 \
  -t 3000 \
  -D "-n 0.01 -x 0.5 -a 20 -s 20 -w 20 -r 1 -o myData" \
  -r 123
```

Outputs:

```text
basicModel_Models.txt
basicModel_EDM_Scores.txt
myData_EDM-1/myData_EDM-1_1.txt
```

The module and wrapper entry points are equivalent:

```bash
python3 -m py_gametes --help
python3 gametes_py.py --help
```

### Model arguments inside `-M`

```text
-h, --heritability                 required model heritability
-p, --caseProportion               optional population prevalence K
-d, --useOddsRatio                 rank by odds ratio instead of EDM
-a, --attributeAlleleFrequency     repeat once per predictive locus
-o, --modelOutputFile              model output prefix
```

### Dataset arguments inside `-D`

```text
-n, --alleleFrequencyMin           generated-noise MAF minimum (0.01)
-x, --alleleFrequencyMax           generated-noise MAF maximum (0.5)
-a, --totalAttributeCount          total output attributes (100)
-t, --totalCount                   continuous sample count (800)
-s, --caseCount                    binary case count (400)
-w, --controlCount                 binary control count (400)
-r, --replicateCount               replicate count (100)
-o, --datasetOutputFile            dataset output prefix
-c, --continuous                   create continuous endpoints
-h, --mixedModelDatasetType        heterogeneous or hierarchical
-b, --heteroLabel                  add heterogeneous model labels
-d, --standardDeviation            continuous endpoint SD (0.2)
```

### Top-level arguments

```text
-M, --model                        repeatable model constraint string
-D, --dataset                      repeatable dataset constraint string
-i, --modelInputFile               repeatable Java/Python model file
-w, --modelWeight                  repeat once per model
-v, --predictiveInputFile          predictive SNP input file
-z, --noiseInputFile               non-predictive SNP input file
-q, --rasQuantileCount             selected quantile count (3)
-p, --rasPopulationCount           candidate model count (1000)
-t, --rasTryCount                  generation attempt limit (100000)
-r, --randomSeed                   Java-compatible deterministic seed
-h, --help                         print help
```

## More examples

Continuous output:

```bash
gametes \
  -M "-h 0.1 -p 0.5 -a 0.2 -a 0.4 -o quantitativeModel" \
  -q 1 -p 30 -t 5000 \
  -D "-c -d 0.2 -t 100 -n 0.01 -x 0.5 -a 20 -r 2 -o quantitativeData" \
  -r 19
```

75/25 heterogeneous output with labels:

```bash
gametes \
  -M "-h 0.1 -p 0.5 -a 0.3 -o m1" -w 75 \
  -M "-h 0.03 -p 0.5 -a 0.4 -o m2" -w 25 \
  -q 1 -p 20 -t 2500 \
  -D "-h heterogeneous -b -n 0.01 -x 0.5 -a 10 -s 10 -w 10 -r 1 -o hetData" \
  -r 11
```

Load a saved model:

```bash
gametes \
  -i basicModel_Models.txt \
  -q 1 \
  -D "-h hierarchical -n 0.01 -x 0.5 -a 12 -s 10 -w 10 -r 1 -o loadedData" \
  -r 5
```

## Test

```bash
.venv/bin/pytest -q
```

Set `GAMETES_JAR=/path/to/gametes_2.2_dev.jar` to enable optional cross-runtime
parity tests when the JAR is not in the default Downloads location.

## Project layout

```text
py_gametes/document.py          CLI/configuration model
py_gametes/java_random.py       exact java.util.Random implementation
py_gametes/penetrance_table.py  model construction and statistics
py_gametes/simulator.py         quantile selection and dataset simulation
py_gametes/gui.py               Tkinter desktop application
py_gametes/cli.py               executable workflow
tests/                          unit, integration, and JAR parity tests
```
