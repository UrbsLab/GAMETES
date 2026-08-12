# Upstream provenance

This project ports GAMETES v2.2 from Java to Python.

- Repository: <https://github.com/UrbsLab/GAMETES>
- Tag: `v2.2`
- Commit: `beb87749495802e2b2be9cc368de78293ad1a126`
- Reference JAR: `dist/gametes_2.2_dev.jar`
- Reference JAR SHA-1: `7b3202f62f1af83df44442a7c24b4fa6d9bf60d2`

The source-to-module mapping is:

| Java v2.2 source | Python module |
|---|---|
| `document/CmdLineParserSrc.java`, `document/SnpGenDocument.java` | `py_gametes/document.py` |
| `simulator/PenetranceTable.java` | `py_gametes/penetrance_table.py` |
| `simulator/SnpGenSimulator.java` | `py_gametes/simulator.py` |
| `java.util.Random` behavior used by both simulator classes | `py_gametes/java_random.py` |
| `ui/*.java` | `py_gametes/gui.py` |
| JAR entry workflow | `py_gametes/cli.py`, `py_gametes/__main__.py` |

The Python GUI uses Tkinter in place of Swing. Its controls and workflows map
to the Java UI, while native widget rendering follows the host operating
system.
