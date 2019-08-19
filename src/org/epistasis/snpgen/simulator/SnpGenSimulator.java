package org.epistasis.snpgen.simulator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.epistasis.snpgen.document.SnpGenDocument;
import org.epistasis.snpgen.document.SnpGenDocument.DocDataset;
import org.epistasis.snpgen.document.SnpGenDocument.DocModel;
import org.epistasis.snpgen.exception.InputException;
import org.epistasis.snpgen.exception.ProcessingException;

public class SnpGenSimulator {
	private static final double kErrorLimit = 0.01D;
	private static final int kMajorMajor = 0;
	private static final int kMajorMinor = 1;
	private static final int kMinorMinor = 2;
	private static final int[] kAlleleSymbols = { SnpGenSimulator.kMajorMajor, SnpGenSimulator.kMajorMinor, SnpGenSimulator.kMinorMinor };

	private static final String kAttributeToken = "Attribute names:";

	private static final String kFrequencyToken = "Minor allele frequencies:";

	private static final String kTableToken = "Table:";

	private final Random random = new Random();

	private PenetranceTableQuantile[] penetranceTableQuantiles;

	private SnpGenDocument document;

	private int tablePopulationCountFound;


	public SnpGenSimulator() {
	}

	public void combineModelTablesIntoQuantiles(final ArrayList<DocModel> modelList, final File[] inputFiles) throws Exception {
		PenetranceTableQuantile[] quantiles1 = null;
		PenetranceTableQuantile[] quantiles2 = null;

		final int modelCount = modelList.size();
		Integer quantileCount1 = null;
		Integer quantileCount2 = null;

		// Get the penetrance tables from the models:
		if (modelCount > 0) {
			// Each model has an array of penetrance tables, one for each
			// quantile.
			quantileCount1 = modelList.get(0).getPenetranceTables().length;
			for (int whichModel = 1; whichModel < modelCount; ++whichModel) {
				assert quantileCount1 == modelList.get(whichModel).getPenetranceTables().length;
			}
			quantiles1 = new PenetranceTableQuantile[quantileCount1];
			for (int q = 0; q < quantileCount1; ++q) {
				quantiles1[q] = new PenetranceTableQuantile(modelCount);
				for (int m = 0; m < modelCount; ++m) {
					final DocModel model = modelList.get(m);
					final PenetranceTable table = model.getPenetranceTables()[q];
					quantiles1[q].tables[m] = table;
				}
			}
		}

		// Get the penetrance tables from the input files:
		if (inputFiles.length > 0) {
			quantiles2 = parseModelInputFiles(inputFiles);
			quantileCount2 = quantiles2.length;
		}

		// if ((quantiles1 == null) && (quantiles2 == null)) {
		// throw new
		// InputException("Must either generate models or get them from a file");
		// }

		// Combine the penetrance tables from the models and the input files:
		int quantileCount;
		if ((quantiles1 != null) && (quantiles2 != null)) {
			assert quantileCount1 == quantileCount2;
			quantileCount = quantileCount1;
			penetranceTableQuantiles = mergeQuantiles(quantiles1, quantiles2);
		} else if (quantiles1 != null) {
			quantileCount = quantiles1.length;
			penetranceTableQuantiles = quantiles1;
		} else if (quantiles2 != null) {
			quantileCount = quantiles2.length;
			penetranceTableQuantiles = quantiles2;
		}

		// If there are no penetrance-table quantiles then create some empty
		// ones:
		// PCA must be generating datasets with no functional snps so penetrance
		// irrelevant
		if (penetranceTableQuantiles == null) {
			quantileCount = 0;
			penetranceTableQuantiles = new PenetranceTableQuantile[quantileCount];
			for (int i = 0; i < quantileCount; ++i) {
				penetranceTableQuantiles[i] = new PenetranceTableQuantile(0);
			}
		}
	}

	// A model can only contain one penetrance table for each quantile, so
	// that's all we fetch here.
	public PenetranceTable[] fetchTables(final File inInputFile) throws Exception {
		final PenetranceTableQuantile[] penetranceTableQuantiles = parseModelInputFile(inInputFile);
		final int quantileCount = penetranceTableQuantiles.length;
		final PenetranceTable[] penetranceTables = new PenetranceTable[quantileCount];
		for (int i = 0; i < quantileCount; ++i) {
			final PenetranceTableQuantile quantile = penetranceTableQuantiles[i];
			assert quantile.tables.length == 1;
			penetranceTables[i] = quantile.tables[0];
		}
		return penetranceTables;
	}

	// Output: Nested subdirectories named by #attributes, population size,
	// model #; containing files named by model-name, population size, and
	// replicate #
	// Example: parent-directory/100/400/Model10/Model10.400.007
	public void generateDatasets(final ProgressHandler inProgressHandler) throws Exception {
		String destFilename;
		File destFile;
		File directory;
		File subdirectory = null;
		File datasetFile;
		int datasetIterationCount;
		Exception ex;
		int[][] predictiveDataset;
		int[][] noiseDataset;
		int fileCount;

		setRandomSeed(document.randomSeed);
		if ((ex = document.verifyDatasetParameters()) != null) {
			throw ex;
		}
		if (document.datasetList.size() > 0) {
			System.out.println("Generating datasets...");
		}

		predictiveDataset = null;
		if (document.predictiveInputFile != null) {
			predictiveDataset = SnpGenSimulator.parseDataInputFile(document.predictiveInputFile, null);
		}
		noiseDataset = null;
		if (document.noiseInputFile != null) {
			noiseDataset = SnpGenSimulator.parseDataInputFile(document.noiseInputFile, null);
		}

		if (inProgressHandler != null) {
			int totalReplicateCount = 0;
			for (final DocDataset dd : document.datasetList) {
				totalReplicateCount += dd.replicateCount.getInteger();
			}
			final int maxProgress = penetranceTableQuantiles.length * totalReplicateCount;
			inProgressHandler.setMaximum(maxProgress);
		}
		fileCount = 0;
		final boolean createDirectories = (document.datasetList.size() > 1);
		for (final DocDataset dd : document.datasetList) {
			destFilename = null;
			directory = null;
			destFile = dd.outputFile;
			if (destFile != null) {
				if (createDirectories) {
					directory = destFile;
					directory.mkdirs();
				} else {
					directory = destFile.getParentFile();
				}
				destFilename = destFile.getName();
			}

			if (noiseDataset != null) {
				datasetIterationCount = 1; // If the noise comes from a file
				// then we only do one replicate
			}

			final int maxQuantileNumberLength = (new Integer(penetranceTableQuantiles.length)).toString().length();
			datasetIterationCount = dd.replicateCount.getInteger();
			final int maxDatasetNumberLength = (new Integer(datasetIterationCount)).toString().length();
			for (int whichQuantile = 0; whichQuantile < penetranceTableQuantiles.length; ++whichQuantile) {
				final PenetranceTableQuantile q = penetranceTableQuantiles[whichQuantile];
				String quantileName = (new Integer(whichQuantile + 1)).toString();
				quantileName = "0000000000".substring(0, maxQuantileNumberLength - quantileName.length()) + quantileName;
				if (destFilename != null) {
					subdirectory = new File(directory, destFilename + "_EDM-" + quantileName);
					subdirectory.mkdirs();
				}
				for (int whichDataset = 0; whichDataset < datasetIterationCount; ++whichDataset) {
					String datasetName = (new Integer(whichDataset + 1)).toString();
					datasetName = "0000000000".substring(0, maxDatasetNumberLength - datasetName.length()) + datasetName;
					if (destFilename != null) {
						datasetFile = new File(subdirectory, destFilename + "_EDM-" + quantileName + "_" + datasetName + ".txt");
					} else {
						datasetFile = null;
					}
					final StringBuilder header = new StringBuilder();

					assert q.tables.length == document.modelFractions.length : "q.tables.length =! document.modelFractions.length";

					SnpGenSimulator.generateAndSaveDataset(random, predictiveDataset, noiseDataset, q.tables, dd, false, datasetFile,
							header, document.modelFractions);
					if (inProgressHandler != null) {
						inProgressHandler.setValue(++fileCount);
					}
				}
			}
		}

		// File caseControlFile = null;
		// caseControlFile = new File(directory, dd.outputFile +
		// "_caseControlValues.txt");
		// if(penetranceTableQuantiles != null && caseControlFile != null)
		// {
		// boolean append = false;
		// for(PenetranceTableQuantile q: penetranceTableQuantiles)
		// {
		// for(PenetranceTable table: q.tables)
		// {
		// table.saveCaseControlValuesToFile(caseControlFile, append);
		// append = true;
		// }
		// }
		// }
		if (document.datasetList.size() > 0) {
			System.out.println("Done generating datasets.");
		}
	}

	public PenetranceTable[] generatePenetranceTables(final DocModel model, final int inDesiredTableCount, final int inTryCount,
			final ProgressHandler inProgressHandler, final int inProgressValueBase) throws Exception {
		return generatePenetranceTables(random, inDesiredTableCount, inTryCount, model.heritability.getDouble(), -1,
				model.prevalence.getDouble(), model.attributeCount.getInteger(), model.getAttributeNames(), model.getAlleleFrequencies(),
				model.getUseOddsRatio(), inProgressHandler, inProgressValueBase);
	}

	public PenetranceTable[] generatePenetranceTables(final Random inRandom, final int inDesiredTableCount, final int inTablesToTryCount,
			final double inDesiredHeritability, final double inHeritabilityTolerance, final Double inDesiredPrevalence,
			final int inAttributeCount, final String[] inAttributeNames, final double[] inAlleleFrequencies, final boolean inUseOddsRatio,
			final ProgressHandler inProgressHandler, final int inProgressValueBase) throws Exception {
		PenetranceTable.ErrorState error;
		PenetranceTable currentPenetranceTable;
		final PenetranceTable.PenetranceTableComparatorEdm edmComparator = new PenetranceTable.PenetranceTableComparatorEdm();
		final PenetranceTable.PenetranceTableComparatorOddsRatio oddsComparator = new PenetranceTable.PenetranceTableComparatorOddsRatio();

		// double[] heritabilities = new double[inTablesToTryCount];
		// int totalHeritabilityCount = 0;

		final List<PenetranceTable> penetranceTableList = new ArrayList<PenetranceTable>();
		PenetranceTable.fixedConflictSuccessfully = 0;
		PenetranceTable.fixedConflictUnsuccessfully = 0;
		for (int whichTableIteration = 0; whichTableIteration < inTablesToTryCount; ++whichTableIteration) {
			currentPenetranceTable = new PenetranceTable(3, inAttributeCount);
			currentPenetranceTable.desiredHeritability = inDesiredHeritability;
			currentPenetranceTable.desiredPrevalence = inDesiredPrevalence;
			currentPenetranceTable.setAttributeNames(inAttributeNames);
			currentPenetranceTable.initialize(inRandom, inAlleleFrequencies);
			error = currentPenetranceTable.generateUnnormalized(inRandom);

			// if (error == ErrorState.Ambiguous)
			// {
			// ++ambiguityCount;
			// if(currentPenetranceTable.fixedConflict)
			// ++ambiguousFixedConflictCount;
			// }
			// else if (error == ErrorState.Conflict)
			// {
			// ++conflictCount;
			// if(currentPenetranceTable.fixedConflict)
			// ++conflictedFixedConflictCount;
			// }
			// else
			// {
			// if(currentPenetranceTable.fixedConflict)
			// ++successfulFixedConflictCount;
			// }

			if ((error == PenetranceTable.ErrorState.Ambiguous) || (error == PenetranceTable.ErrorState.Conflict)) {
				// System.out.println("Failed to construct the penetrance table");
			} else {
				// PenetranceTable copy = null;
				// try
				// {
				// copy = (PenetranceTable) currentPenetranceTable.clone();
				// }
				// catch(CloneNotSupportedException cnse)
				// {
				//
				// }
				// currentPenetranceTable.verify();
				currentPenetranceTable.scaleToUnitInterval();
				currentPenetranceTable.adjustPrevalence();
				// currentPenetranceTable.verify();
				final double herit = currentPenetranceTable.calcHeritability();
				// heritabilities[totalHeritabilityCount++] = herit;
				boolean heritabilityAchieved = false;
				if ((inHeritabilityTolerance < 0)
						|| (Math.abs((herit - currentPenetranceTable.desiredHeritability) / currentPenetranceTable.desiredHeritability) < inHeritabilityTolerance)) {
					currentPenetranceTable.adjustHeritability();
					heritabilityAchieved = currentPenetranceTable.normalized;
				}
				if (!heritabilityAchieved) {
				} else {
					currentPenetranceTable.checkRowSums();
					// if(!currentPenetranceTable.rowSumsValid)
					// throw new Exception("Table failed the row-sum test!");
					if (currentPenetranceTable.rowSumsValid) {
						penetranceTableList.add(currentPenetranceTable);
					}
					// if(inProgressHandler != null)
					// inProgressHandler.setValue(whichModel *
					// inDesiredTableCount + tableCountSoFar);

					if (penetranceTableList.size() >= inDesiredTableCount) {
						break;
					}
				}
			}
			if (inProgressHandler != null) {
				inProgressHandler.setValue(inProgressValueBase + whichTableIteration);
			}
		}

		final PenetranceTable[] penetranceTables = penetranceTableList.toArray(new PenetranceTable[0]);
		if (inUseOddsRatio) {
			Arrays.sort(penetranceTables, oddsComparator);
		} else {
			Arrays.sort(penetranceTables, edmComparator);
		}

		// System.out.println("fixedConflictSuccessfully: " +
		// PenetranceTable.fixedConflictSuccessfully);
		// System.out.println("fixedConflictUnsuccessfully: " +
		// PenetranceTable.fixedConflictUnsuccessfully);
		// heritabilities = Arrays.copyOf(heritabilities,
		// totalHeritabilityCount);
		// printStats(heritabilities, 20);
		// System.out.print("\t");

		return penetranceTables;
	}

	public double[][] generateTablesForModels(final ArrayList<DocModel> modelList, final int desiredQuantileCount,
			final int inDesiredPopulationCount, final int inTryCount, final ProgressHandler inProgressHandler) throws Exception {
		final int modelCount = modelList.size();
		if (modelCount > 0) {
			System.out.println("Generating models...");
		}

		final double[][] allTableScores = new double[modelCount][];
		for (int whichModel = 0; whichModel < modelCount; ++whichModel) {
			final int progressValueBase = whichModel * inDesiredPopulationCount;
			final DocModel model = modelList.get(whichModel);
			allTableScores[whichModel] = generateTablesForOneModel(model, desiredQuantileCount, inDesiredPopulationCount, inTryCount,
					inProgressHandler, progressValueBase);
		}
		if (modelCount > 0) {
			System.out.println("Done generating models.");
		}
		return allTableScores;
	}

	public PenetranceTableQuantile[] getPenetranceTableQuantiles() {
		return penetranceTableQuantiles;
	}

	public int getTablePopulationCountFound() {
		return tablePopulationCountFound;
	}

	public PenetranceTableQuantile[] parseModelInputFile(final File inInputFile) throws FileNotFoundException, IOException, InputException {

		final ArrayList<ArrayList<PenetranceTable>> tables = new ArrayList<>();
		ArrayList<PenetranceTable> currentSubList;
		PenetranceTable currentTable;

		try (BufferedReader tableReader = new BufferedReader(new FileReader(inInputFile));) {
			currentSubList = null;
			while (true) {
				currentTable = findTable(tableReader);
				if (currentTable == null) {
					break;
				}
				if ((currentSubList == null) || Arrays.equals(currentTable.getAttributeNames(), currentSubList.get(0).getAttributeNames())) {
					currentSubList = new ArrayList<PenetranceTable>();
					tables.add(currentSubList);
				}
				currentSubList.add(currentTable);
				parseTable(tableReader, currentTable);
			}
		}

		final int quantileCount = tables.size();
		final int quantileSize = tables.get(0).size();
		for (final ArrayList<PenetranceTable> ptl : tables) {
			if (ptl.size() != quantileSize) {
				throw new InputException("Each quantile must have the same number of tables");
			}
		}

		final PenetranceTableQuantile[] outPenetranceTableQuantiles = new PenetranceTableQuantile[quantileCount];
		for (int i = 0; i < quantileCount; ++i) {
			outPenetranceTableQuantiles[i] = new PenetranceTableQuantile(quantileSize);
			for (int j = 0; j < quantileSize; ++j) {
				outPenetranceTableQuantiles[i].tables[j] = tables.get(i).get(j);
			}
		}
		return outPenetranceTableQuantiles;
	}

	public PenetranceTableQuantile[] parseModelInputFiles(final File[] inInputFiles) throws FileNotFoundException, IOException,
	InputException {
		PenetranceTableQuantile[] outQuantiles;
		if (inInputFiles.length > 0) {
			outQuantiles = parseModelInputFile(inInputFiles[0]);
			for (int i = 1; i < inInputFiles.length; ++i) {
				final PenetranceTableQuantile[] newQuantiles = parseModelInputFile(inInputFiles[i]);
				outQuantiles = mergeQuantiles(outQuantiles, newQuantiles);
			}
		} else {
			outQuantiles = new PenetranceTableQuantile[0];
		}
		return outQuantiles;
	}

	public void setDocument(final SnpGenDocument inDoc) {
		document = inDoc;
	}

	public void setTablePopulationCountFound(final int tablePopulationCountFound) {
		this.tablePopulationCountFound = tablePopulationCountFound;
	}

	public void writeModelTables(final DocModel model, final File tablesFile, final String header, final boolean inSaveUnnormalized)
			throws IOException {
		final int quantileCount = model.getQuantileCountInModel();

		try (PrintWriter tableStream = new PrintWriter(new FileWriter(tablesFile, false));) {

			if (header != null) {
				tableStream.println(header);
			}
		}

		final PenetranceTable[] tables = model.getPenetranceTables();
		for (int q = 0; q < quantileCount; ++q) {
			if (tables.length > q) {
				tables[q].saveToFile(tablesFile, true, inSaveUnnormalized);
			}
		}
	}

	// inAllTableScores[eachModel][eachScore]
	public void writeTablesAndScoresToFile(final ArrayList<DocModel> modelList, final double[][] inAllTableScores, final int quantileCount)
			throws IOException {
		final int modelCount = modelList.size();

		// Write scores to the output files, and calculate
		// tablePopulationCountFoundMinimum:
		Integer tablePopulationCountFoundMinimum = null;
		for (int whichModel = 0; whichModel < modelCount; ++whichModel) {
			final DocModel model = modelList.get(whichModel);
			final String scoreName = calcScoreName(model);
			final File destFile = model.file;
			if (destFile != null) {
				final File scoreFile = calcCombinedFilename(destFile, "_" + scoreName + "_Scores", "txt");
				try (final PrintWriter scoreStream = new PrintWriter(new FileWriter(scoreFile));) {
					final double[] populationScores = inAllTableScores[whichModel];
					final int tablePopulationCount = populationScores.length;
					if ((tablePopulationCountFoundMinimum == null) || (tablePopulationCount < tablePopulationCountFoundMinimum)) {
						tablePopulationCountFoundMinimum = tablePopulationCount;
					}
					// scoreStream.println(scoreName + " scores for model: " +
					// whichModel);
					scoreStream.println(scoreName + " scores");
					for (int i = 0; i < tablePopulationCount; ++i) {
						scoreStream.println(populationScores[i]);
					}
				}
				final File tablesFile = calcCombinedFilename(destFile, "_Models", "txt");
				assert quantileCount == model.getQuantileCountInModel();
				final String header = "Selected " + quantileCount + " " + scoreName + " quantiles from a population of "
						+ tablePopulationCountFoundMinimum + " tables.";
				writeModelTables(model, tablesFile, header, false);
				// PrintWriter tableStream = null;
				// try
				// {
				// tableStream = new PrintWriter(new FileWriter(tablesFile,
				// false));
				// tableStream.println("Selected " + quantileCount + " " +
				// scoreName + " quantiles from a population of " +
				// tablePopulationCountFoundMinimum + " tables.");
				// }
				// finally
				// {
				// if (tableStream != null)
				// tableStream.close();
				// }
				//
				// PenetranceTable[] tables = model.getPenetranceTables();
				// for(int q = 0; q < quantileCount; ++q)
				// {
				// if(tables.length > q)
				// {
				// tables[q].saveToFile(tablesFile, true, false);
				// }
				// }
			}
		}
	}

	private File calcCombinedFilename(final File destFile, final String inSubName, final String inExtension) {
		final File directory = destFile.getParentFile();
		String baseFilename = destFile.getName();
		if (baseFilename.toLowerCase().endsWith(".txt")) {
			baseFilename = baseFilename.substring(0, baseFilename.length() - 4);
		}
		final File outFile = new File(directory, baseFilename + inSubName + "." + inExtension);
		return outFile;
	}

	private String calcScoreName(final DocModel model) {
		String scoreName = null;
		if (model.getUseOddsRatio()) {
			scoreName = "OddsRatio";
		} else {
			scoreName = "EDM";
		}
		return scoreName;
	}

	private PenetranceTable findTable(final BufferedReader modelReader) throws IOException {
		String line;
		String[] attributeNames;
		PenetranceTable outTable;

		outTable = null;
		while (true) {
			line = modelReader.readLine();
			if (line == null) {
				break;
			}
			if (line.toLowerCase().startsWith(SnpGenSimulator.kAttributeToken.toLowerCase())) {
				attributeNames = line.substring(SnpGenSimulator.kAttributeToken.length() + 1).trim().split("\t");
				outTable = new PenetranceTable(3, attributeNames.length);
				outTable.setAttributeNames(attributeNames);
				outTable.normalized = true;
				break;
			}
		}
		return outTable;
	}

	// Returns the number of tables found, from which the desired quantiles were
	// chosen.
	private double[] generateTablesForOneModel(final DocModel model, final int desiredQuantileCount, final int inDesiredPopulationCount,
			final int inTryCount, final ProgressHandler inProgressHandler, final int inProgressValueBase) throws Exception {
		double[] outAllTableScores;

		setRandomSeed(document.randomSeed);
		final PenetranceTable[] tables = generatePenetranceTables(model, inDesiredPopulationCount, inTryCount, inProgressHandler,
				inProgressValueBase);
		final int tableCount = tables.length;
		if (tableCount < desiredQuantileCount) {
			throw new ProcessingException("Unable to generate desired number of table quantiles");
		}

		outAllTableScores = new double[tableCount];
		for (int i = 0; i < tableCount; ++i) {
			outAllTableScores[i] = tables[i].getQuantileScore(model.getUseOddsRatio());
		}

		tablePopulationCountFound = tableCount;

		selectPenetranceTablesRepresentativesUniformly(desiredQuantileCount, tables, model);
		return outAllTableScores;
	}

	private PenetranceTableQuantile[] mergeQuantiles(final PenetranceTableQuantile[] inQuantiles1,
			final PenetranceTableQuantile[] inQuantiles2) throws InputException {
		if (inQuantiles1.length != inQuantiles2.length) {
			throw new InputException("The generated models and the models from the file must have the same number of quantiles");
		}

		final int quantileCount = inQuantiles1.length;
		final PenetranceTableQuantile[] outPenetranceTableQuantiles = new PenetranceTableQuantile[quantileCount];
		for (int i = 0; i < quantileCount; ++i) {
			outPenetranceTableQuantiles[i] = new PenetranceTableQuantile(inQuantiles1[i].tables.length + inQuantiles2[i].tables.length);
			int dest = 0;
			for (final PenetranceTable t : inQuantiles1[i].tables) {
				outPenetranceTableQuantiles[i].tables[dest++] = t;
			}
			for (final PenetranceTable t : inQuantiles2[i].tables) {
				outPenetranceTableQuantiles[i].tables[dest++] = t;
			}
		}
		return outPenetranceTableQuantiles;
	}

	private void parseTable(final BufferedReader modelReader, final PenetranceTable inTable) throws IOException, InputException {
		String line;
		String[] numbers;
		int whichCell;
		double cellValue;

		// Find the table-token:
		while (true) {
			line = modelReader.readLine();
			if (line == null) {
				throw new InputException("Got a table-header without a table");
			}
			if (line.toLowerCase().startsWith(SnpGenSimulator.kFrequencyToken.toLowerCase())) {
				numbers = line.substring(SnpGenSimulator.kFrequencyToken.length() + 1).trim().split("\t");
				final double[] freqs = new double[numbers.length];
				int whichFreq = 0;
				for (final String s : numbers) {
					try {
						freqs[whichFreq++] = Double.parseDouble(s.trim());
					} catch (final NumberFormatException nfe) {
						throw new InputException("Got a table with a non-numeric minor allele frequency");
					}
				}
				inTable.setMinorAlleleFrequencies(freqs);
			}
			if (line.toLowerCase().startsWith(SnpGenSimulator.kTableToken.toLowerCase())) {
				break;
			}
		}

		whichCell = 0;
		while (true) {
			line = modelReader.readLine();
			if (line == null) {
				throw new InputException("Got a table with too few cells");
			}
			if (line.trim().length() > 0) {
				numbers = line.split(",");
				for (final String s : numbers) {
					try {
						cellValue = Double.parseDouble(s.trim());
					} catch (final NumberFormatException nfe) {
						throw new InputException("Got a table with a non-numeric cell");
					}
					if (whichCell >= inTable.cellCount) {
						throw new InputException("Got a table with too many cells");
					}
					inTable.cells[whichCell++] = new PenetranceTable.PenetranceCell(cellValue);
				}
			}
			if (whichCell >= inTable.cellCount) {
				break;
			}
		}
		inTable.calcAndSetHeritability();
	}

	private void selectPenetranceTablesRepresentativesUniformly(final int inQuantileCount, final PenetranceTable[] tablePopulation,
			final DocModel model) {
		final double[] targetRASs = new double[inQuantileCount];

		final boolean useOddsRatio = model.getUseOddsRatio();
		final int tablePopulationSize = tablePopulation.length;

		// Set the target quantile scores:
		final double minRAS = tablePopulation[0].getQuantileScore(useOddsRatio);
		final double maxRAS = tablePopulation[tablePopulationSize - 1].getQuantileScore(useOddsRatio);
		if (inQuantileCount == 1) {
			targetRASs[0] = (minRAS + maxRAS) / 2F;
		} else {
			final double delta = (maxRAS - minRAS) / (inQuantileCount - 1);
			for (int whichQuantile = 0; whichQuantile < inQuantileCount; ++whichQuantile) {
				targetRASs[whichQuantile] = minRAS + (whichQuantile * delta);
			}
		}

		int tableIter = 0;
		int quantileIter;
		int matchingTable;
		int priorMatchingTable = -1;

		// Copy the tables from the appropriate spots in
		// outPenetranceTableQuantiles to the model:
		final PenetranceTable[] modelTables = new PenetranceTable[inQuantileCount];
		model.setPenetranceTables(modelTables);

		// Initialize the outputs to null, for "not set yet":
		for (quantileIter = 0; quantileIter < inQuantileCount; ++quantileIter) {
			modelTables[quantileIter] = null;
		}

		quantileIter = 0;
		if (inQuantileCount > 1) {
			// If there's more than one quantile, then we know that the first
			// quantile is the very first table.
			modelTables[quantileIter++] = tablePopulation[tableIter++];
			priorMatchingTable = 0;
		}
		for (; tableIter < tablePopulationSize; ++tableIter) {
			// Look for the first reliefAccuracyScore greater than the current
			// target:
			if (tablePopulation[tableIter].getQuantileScore(useOddsRatio) > targetRASs[quantileIter]) {
				// If the table before the current table is closer to the target
				// than the current table, and is available,
				// then use it; else use the current table:
				if ((tableIter > 0)
						&& (Math.abs(tablePopulation[tableIter - 1].getQuantileScore(useOddsRatio) - targetRASs[quantileIter]) < Math
								.abs(tablePopulation[tableIter].getQuantileScore(useOddsRatio) - targetRASs[quantileIter]))) {
					matchingTable = tableIter - 1;
				} else {
					matchingTable = tableIter;
				}

				// Make sure that the table we want hasn't been used yet:
				if (matchingTable == priorMatchingTable) {
					// but don't go off the end:
					if (matchingTable == (tablePopulationSize - 1)) {
						break;
					}
					++matchingTable;
				}
				modelTables[quantileIter] = tablePopulation[matchingTable];
				priorMatchingTable = matchingTable;
				++quantileIter;
				if (quantileIter >= inQuantileCount) {
					break;
				}
			}
		}
		// Take care of any quantiles on the end that have not been set yet.
		// (If inQuantileCount == 1, then the quantile will have been set in the
		// loop above, unless all of the reliefAccuracyScores are identical --
		// but in that case, it doesn't matter which table we use, so we might
		// as well use the last one.)
		quantileIter = inQuantileCount - 1;
		// If the last quantile hasn't been set yet,
		if (modelTables[quantileIter] == null) {
			tableIter = tablePopulationSize - 1;
			// then set the last quantile to the last table,
			modelTables[quantileIter--] = tablePopulation[tableIter--];
			// and check the quantile before it:
			while ((quantileIter >= 0)
					&& ((modelTables[quantileIter] == null) || (modelTables[quantileIter] == modelTables[quantileIter + 1]))) {
				modelTables[quantileIter--] = tablePopulation[tableIter--];
			}
		}
	}

	// private void printStats(final Random inRandom, final double herit, final
	// double inHeritabilityTolerance, final double ras,
	// final double maf, final int inPopCount, final int inBucketCount, final
	// int inWhichModel) throws Exception {
	// int popCount = inPopCount;
	// double[] scores = new double[popCount];
	// PenetranceTable[] pop;
	// pop = generatePenetranceTables(inRandom, popCount, 100 * popCount,
	// (double) herit, inHeritabilityTolerance, null, 2, new String[] {
	// "foo", "bar" }, new double[] { (double) maf, (double) maf }, false, null,
	// 0);
	// int scoreCount = 0;
	// for (final PenetranceTable t : pop) {
	// if (t == null) {
	// popCount = scoreCount;
	// break;
	// }
	// scores[scoreCount++] = t.edm;
	// }
	// if (popCount != inPopCount) {
	// scores = Arrays.copyOf(scores, popCount);
	// }
	// Arrays.sort(scores);
	// int whichScore = 0;
	// while ((whichScore < popCount) && (scores[whichScore] < ras)) {
	// ++whichScore;
	// }
	// final double percentile = (100 * (double) whichScore) / popCount;
	// System.out.print(inWhichModel + "\t" + maf + "\t" + herit + "\t" + ras +
	// "\t" + percentile + "\t");
	// SnpGenSimulator.printStats(scores, inBucketCount);
	// System.out.println();
	// }

	private void setRandomSeed(final Integer inSeed) {
		SnpGenSimulator.setRandomSeed(random, inSeed);
	}

	public static int[][] parseDataInputFile(final File inInputFile, final StringBuilder outHeader) throws FileNotFoundException,
	IOException, InputException {
		List<String> lines;
		int[][] outDataset;
		lines = new ArrayList<String>();
		try (BufferedReader reader = new BufferedReader(new FileReader(inInputFile))) {
			String line;
			if ((line = reader.readLine()) != null) {
				boolean isNumeric = true;
				for (int i = 0; i < line.length(); ++i) {
					if ("0123456789 \t".indexOf(line.charAt(i)) == -1) {
						isNumeric = false;
						break;
					}
				}
				// Don't use the first line if it is non-numeric:
				if (isNumeric) {
					lines.add(line);
				} else {
					// A first, non-numeric, line is a header, in case such is
					// requested by the caller:
					if (outHeader != null) {
						outHeader.append(line);
					}
				}
				while ((line = reader.readLine()) != null) {
					lines.add(line);
				}
			}
		}
		String[] items = lines.get(0).split("\t");
		outDataset = new int[lines.size()][items.length];
		int whichLine = 0;
		for (final String line : lines) {
			int whichItem = 0;
			items = line.split("\t");
			for (final String item : items) {
				outDataset[whichLine][whichItem] = Integer.valueOf(item);
				++whichItem;
			}
			++whichLine;
		}
		return outDataset;
	}

	private static int[][] generateAndSaveDataset(final Random inRandom, final int[][] inPredictiveDataset, final int[][] inNoiseDataset,
			final PenetranceTable[] inTables, final DocDataset dd, final boolean inReturnDataset,
			final File inDestFile,
			final StringBuilder outHeader, final double[] modelFractions) throws Exception {
		// int caseCount = inCaseCount.getInteger().intValue();
		// int controlCount = inControlCount.getInteger().intValue();

		int attributeCountPredictiveFromTables = 0;
		for (final PenetranceTable t : inTables) {
			attributeCountPredictiveFromTables += t.attributeCount;
		}

		int attributeCountPredictiveFromFile = 0;
		if (inPredictiveDataset != null) {
			attributeCountPredictiveFromFile = inPredictiveDataset[0].length - 1; // The
			// columns
			// in
			// inPredictiveDataset,
			// not
			// counting
			// the
			// class
			// column
		}

		final int predictiveAttributeCount = attributeCountPredictiveFromTables + attributeCountPredictiveFromFile;

		int attributeCountNoiseFile = 0;
		if (inNoiseDataset != null) {
			attributeCountNoiseFile = inNoiseDataset[0].length;
		}

		int totalAttributeCount = dd.totalAttributeCount.getInteger().intValue();
		int attributeCountNoiseGenerated;

		final int instanceCount = (inNoiseDataset != null) ? inNoiseDataset.length : dd.totalCount.getInteger().intValue();

		if (inNoiseDataset != null) {
			// If there is a noise dataset then we don't generate any noise
			// attributes:
			totalAttributeCount = predictiveAttributeCount + attributeCountNoiseFile; // ALERT:
			// we
			// ignore
			// inTotalAttributeCount
			// in
			// this
			// case
			attributeCountNoiseGenerated = 0;
			// and we base caseCount and controlCount on the number of instances
			// in the noise dataset:
		} else {
			// If there is *no* noise dataset we generate exactly enough noise
			// to fill out the desired number of attributes:
			attributeCountNoiseGenerated = totalAttributeCount - predictiveAttributeCount - attributeCountNoiseFile;
			assert attributeCountNoiseFile == 0;
		}

		final int noiseAttributeCount = attributeCountNoiseGenerated + attributeCountNoiseFile;
		assert totalAttributeCount == (predictiveAttributeCount + noiseAttributeCount);

		double prob;
		double penetrance;
		PenetranceTable.CellId cellId;
		double sumCaseFractions, sumControlFractions;
		// caseIntervals[i] == the right edge of the ith probability-interval
		// for cases; similarly for controls
		double[][] caseIntervals, controlIntervals;
		int[][] outputArray = null;
		if (inReturnDataset) {
			outputArray = new int[dd.totalCount.getInteger().intValue()][totalAttributeCount + 1];
		}

		try (PrintWriter outputStream = (inDestFile == null) ? null : new PrintWriter(new FileWriter(inDestFile));) {

			// The order of attributes: non-predictive attributes, followed by
			// predictive attributes from the file, followed by predictive
			// attributes from the SNPGen models.

			// Header for non-predictive attributes:
			for (int i = 0; i < noiseAttributeCount; ++i) {
				if (outputStream != null) {
					outputStream.print("N" + i + "\t");
				}
				outHeader.append("N" + i + "\t");
			}

			// Header for predictive attributes from file:
			if (inPredictiveDataset != null) {
				for (int i = 0; i < (inPredictiveDataset[0].length - 1); ++i) {
					final String attributeName = "P" + (attributeCountPredictiveFromTables + 1 + i);
					if (outputStream != null) {
						outputStream.print(attributeName + "\t");
					}
					outHeader.append(attributeName + "\t");
				}
			}

			// Header for predictive attributes from SNPGen models:
			for (int i = 0; i < inTables.length; ++i) {
				final PenetranceTable t = inTables[i];
				for (final String n : t.getAttributeNames()) {
					final String name = "M" + i + n;
					if (outputStream != null) {
						outputStream.print(name + "\t");
					}
					outHeader.append(name + "\t");
				}
			}

			if (outputStream != null) {
				outputStream.println("Class");
			}
			outHeader.append("Class");


			// Generate allele frequencies
			// For each attribute, frequency[0] is the major-major allele and
			// frequency[2] is the minor-minor allele.
			final double[][] alleleFrequencies = new double[attributeCountNoiseGenerated][3];
			final double alleleFrequencyMin = dd.alleleFrequencyMin.getDouble().doubleValue();
			final double alleleFrequencyRange = dd.alleleFrequencyMax.getDouble().doubleValue() - alleleFrequencyMin;
			for (int i = 0; i < attributeCountNoiseGenerated; ++i) {
				final double maf = (inRandom.nextDouble() * alleleFrequencyRange) + alleleFrequencyMin;
				PenetranceTable.calcAlleleFrequencies(maf, alleleFrequencies[i]);
			}

			if (dd.createContinuousEndpoints.getBoolean()) {
				// for continuous endpoints only need to calculate # of samples
				// for each genotype since there are no case and controls
				final int tableCount = inTables.length;
				final double[][] genotypeIntervals = new double[tableCount][];
				for (int j = 0; j < tableCount; ++j) {
					cellId = new PenetranceTable.CellId(inTables[j].attributeCount);
					double sumGenotypeFractions = 0.0;
					genotypeIntervals[j] = new double[inTables[j].cellCount];
					// Sum up all the count fractions, storing the partial
					// count-fractions to the array
					for (int i = 0; i < inTables[j].cellCount; ++i) {
						inTables[j].masterIndexToCellId(i, cellId);
						// Note the getProbabilityProduct uses the
						// alleleFrequencies which were provided when the
						// penetrance table was constructed
						prob = inTables[j].getProbabilityProduct(cellId);

						sumGenotypeFractions += prob;
						genotypeIntervals[j][i] = sumGenotypeFractions;
					}
					assert Math.abs(sumGenotypeFractions - 1.0) < SnpGenSimulator.kErrorLimit;
				}
				// Now, the length of the interval from genotypeIntervals[i-1]
				// to
				// genotypeIntervals[i]
				// == the proportion of samples that will have that cells
				// genotype

				for (int j = 0; j < tableCount; ++j) {
					inTables[j].clear();
				}
				SnpGenSimulator.printInstances(dd, inRandom, inPredictiveDataset, inNoiseDataset, 0, inTables,
						attributeCountNoiseGenerated, alleleFrequencies, 1, dd.totalCount.getInteger(), genotypeIntervals, outputStream,
						outputArray, 0, modelFractions);
			} else {
				// Calculate the values of caseIntervals and controlIntervals,
				// such
				// that the length of each interval is the desired probability
				// of a given case or control (respectively) landing in a given
				// cell.
				// This is used for sampling the cells in the dataset.
				final int tableCount = inTables.length;
				caseIntervals = new double[tableCount][];
				controlIntervals = new double[tableCount][];
				for (int j = 0; j < tableCount; ++j) {
					cellId = new PenetranceTable.CellId(inTables[j].attributeCount);
					sumCaseFractions = 0;
					sumControlFractions = 0;
					caseIntervals[j] = new double[inTables[j].cellCount];
					controlIntervals[j] = new double[inTables[j].cellCount];
					// Sum up all the case-fractions, storing the partial
					// case-fractions to the caseIntervals array; do the same
					// with
					// controls:
					for (int i = 0; i < inTables[j].cellCount; ++i) {
						inTables[j].masterIndexToCellId(i, cellId);
						// Note the getProbabilityProduct uses the
						// alleleFrequencies which were provided when the
						// penetrance table was constructed
						prob = inTables[j].getProbabilityProduct(cellId);
						penetrance = inTables[j].getPenetranceValue(cellId);

						sumCaseFractions += prob * penetrance;
						sumControlFractions += prob * (1 - penetrance);
						caseIntervals[j][i] = sumCaseFractions;
						controlIntervals[j][i] = sumControlFractions;
					}
					assert Math.abs((sumCaseFractions + sumControlFractions) - 1.0) < SnpGenSimulator.kErrorLimit;
					// Divide each element of caseIntervals and controlIntervals
					// by
					// the appropriate total sum.
					for (int i = 0; i < inTables[j].cellCount; ++i) {
						caseIntervals[j][i] /= sumCaseFractions;
						controlIntervals[j][i] /= sumControlFractions;
					}
				}
				// Now, the length of the interval from caseIntervals[i-1] to
				// caseIntervals[i]
				// == the probability that a random case is in the ith cell of
				// the
				// penetrance table; similarly for controls.

				for (int j = 0; j < tableCount; ++j) {
					inTables[j].clear();
				}
				final int caseCount = (int) Math.round(dd.caseProportion.value * instanceCount);
				final int controlCount = instanceCount - caseCount;
				// write out all the cases
				SnpGenSimulator.printInstances(dd, inRandom, inPredictiveDataset, inNoiseDataset, 0, inTables,
						attributeCountNoiseGenerated,
						alleleFrequencies, 1, caseCount, caseIntervals, outputStream, outputArray, 0,
						modelFractions);
				// write out all the controls
				SnpGenSimulator.printInstances(dd, inRandom, inPredictiveDataset, inNoiseDataset, caseCount, inTables,
						attributeCountNoiseGenerated, alleleFrequencies, 0, controlCount, controlIntervals, outputStream, outputArray,
						caseCount, modelFractions);
			}
		} // end try-release on dataset printWriter
		return outputArray;
	}

	private static int noiseToOutput(final Random inRandom, final double[] inAlleleFrequencies, final PrintWriter inOutputStream,
			final int[][] inOutputArray, final int inWhichOutputLine, final int inWhichOutputColumn) {
		int outWhich;
		final double rand = inRandom.nextDouble();
		if (rand < inAlleleFrequencies[0]) {
			outWhich = 0;
			SnpGenSimulator.valueToOutput(SnpGenSimulator.kMajorMajor, null, inOutputStream, true, inOutputArray,
					inWhichOutputLine, inWhichOutputColumn);
		} else if (rand < (inAlleleFrequencies[0] + inAlleleFrequencies[1])) {
			outWhich = 1;
			SnpGenSimulator.valueToOutput(SnpGenSimulator.kMajorMinor, null, inOutputStream, true, inOutputArray,
					inWhichOutputLine, inWhichOutputColumn);
		} else {
			outWhich = 2;
			SnpGenSimulator.valueToOutput(SnpGenSimulator.kMinorMinor, null, inOutputStream, true, inOutputArray,
					inWhichOutputLine, inWhichOutputColumn);
		}
		return outWhich;
	}

	// public void generateDatasets() throws Exception
	// {
	// generateDatasets(progressHandler);
	// }

	private static void printInstances(final DocDataset dd, final Random inRandom, final int[][] inPredictiveDataset,
			final int[][] inNoiseDataset,
			final int inWhichFirstNoise, final PenetranceTable[] inTables, final int inNoiseAttributeCount,
			final double[][] inAlleleFrequencies, final int inInstanceClass, final int inInstanceCount,
			final double[][] inInstanceIntervals, final PrintWriter inOutputStream, final int[][] inOutputArray,
			final int inFirstOutputLine, final double[] modelFractions)
					throws Exception {
		double rand;
		int whichCell;
		PenetranceTable.CellId cellId;
		int whichOutputLine = inFirstOutputLine;

		int predictiveDatasetAttributeCount = 0;
		if (inPredictiveDataset != null) {
			predictiveDatasetAttributeCount = inPredictiveDataset[0].length - 1; // Don't
			// count
			// the
			// class
			// column
		}
		int whichPredictive = 0;

		int noiseDatasetAttributeCount = 0;
		if (inNoiseDataset != null) {
			noiseDatasetAttributeCount = inNoiseDataset[0].length;
		}
		int whichNoise = inWhichFirstNoise;

		final double[] alleleFrequencies = new double[3];

		// The order of attributes: non-predictive attributes, followed by
		// predictive attributes from the file, followed by predictive
		// attributes from the SNPGen models.

		// How heterogeneity works:
		// If there are two tables, and Table 1 has a contribution-fraction of
		// 0.3 and Table 2 has a contribution-fraction of 0.7,
		// then for the first 0.3 of the instances we generate the columns
		// corresponding to Table 1 according to Table 1's signal (ie, according
		// to Table 1's inInstanceIntervals)
		// and we generate the columns corresponding to Table 2 as noise;
		// for the next 0.7 of the instances we generate noise for Table 1 and
		// signal for Table 2.

		// For each instance desired:
		assert modelFractions.length == inTables.length;
		double sumTableFractions = 0;
		for (final double modelFraction : modelFractions) {
			sumTableFractions += modelFraction;
		}
		assert Math.abs(sumTableFractions - 1.0d) < 0.00001d : "sum of model weights should be 1 but is: " + sumTableFractions
		+ " table weights: " + Arrays.toString(modelFractions);
		for (int row = 0; row < inInstanceCount; ++row) {

			Integer heterogeneousCurrentTable = null;
			if (dd.multipleModelDatasetType.getValue() == SnpGenDocument.MIXED_MODEL_DATASET_TYPE.heterogeneous) {
				// Figure out which table has the signal for the current row:
				final double rowFraction = (double) row / inInstanceCount;
				double tableFractionBefore = 0;
				// slight doubling-point
				// discrepancies.
				for (int k = 0; k < inTables.length; ++k) {
					final double currentTableFraction = modelFractions[k];
					if (rowFraction < (tableFractionBefore + currentTableFraction)) {
						heterogeneousCurrentTable = k;
						break;
					}
					tableFractionBefore += currentTableFraction;
				}
			}	//end if using heterogeneous models
			int destWhich = 0;

			if (inNoiseDataset != null) {
				// Copy the noise attributes from inNoiseDataset
				if (whichNoise >= inNoiseDataset.length) {
					throw new Exception("Not enough noise input data");
				}
				for (int j = 0; j < noiseDatasetAttributeCount; ++j) {
					SnpGenSimulator.valueToOutput(inNoiseDataset[whichNoise][j], null, inOutputStream, true, inOutputArray,
							whichOutputLine, destWhich++);
				}
				++whichNoise;
			}

			// Generate noise attributes
			for (int j = 0; j < inNoiseAttributeCount; ++j) {
				SnpGenSimulator
				.noiseToOutput(inRandom, inAlleleFrequencies[j], inOutputStream, inOutputArray, whichOutputLine, destWhich++);
				// rand = inRandom.nextDouble();
				// if(rand < inAlleleFrequencyIntervals[0][j])
				// valueToOutput(kMajorMajor, inOutputStream, true,
				// inOutputArray, whichOutputLine, destWhich++);
				// else if(rand < inAlleleFrequencyIntervals[1][j])
				// valueToOutput(kMajorMinor, inOutputStream, true,
				// inOutputArray, whichOutputLine, destWhich++);
				// else
				// valueToOutput(kMinorMinor, inOutputStream, true,
				// inOutputArray, whichOutputLine, destWhich++);
			}

			if (inPredictiveDataset != null) {
				// Copy the predictive attributes from inPredictiveDataset:
				// First, find a match in inPredictiveDataset for
				// inInstanceClass:
				while ((whichPredictive < inPredictiveDataset.length)
						&& (inPredictiveDataset[whichPredictive][predictiveDatasetAttributeCount] != inInstanceClass)) {
					++whichPredictive;
				}
				if (whichPredictive >= inPredictiveDataset.length) {
					throw new Exception("Not enough predictive input data");
				}
				for (int j = 0; j < predictiveDatasetAttributeCount; ++j) {
					SnpGenSimulator.valueToOutput(inPredictiveDataset[whichPredictive][j], null, inOutputStream, true,
							inOutputArray, whichOutputLine, destWhich++);
				}
				++whichPredictive;
			}

			// We're going to put the current instance (either a case or a
			// control, as determined by this method's caller; let's say it's a
			// case)
			// into some cell of each specified table.
			// Let's say there are three tables.
			// Then we're going to put the current case into some cell of the
			// first table,
			// and, simultaneously, into some cell of the second table, and some
			// cell of the third table.
			// Equivalently, we're going to put the case into some cell of the
			// cross-product of the three tables:
			// if the first table is 2-D, the second table is 4-D, and the third
			// table is 3-D,
			// then we are choosing a cell in the 9-D table which is the
			// cross-product of the three given tables.
			// (Added later: I'm not sure what the above comment is in aid of.)

			// As we iterate through the outer loop, we need to fill in each
			// table's cellCaseCount or cellControlCount, as determined by
			// inInstanceClass (a kluge).

			// System.out.println("signal\t" + whichSignalTable);

			double phenotypeValue = 0.0;
			// now generate values for each of the model columns
			for (int whichTable = 0; whichTable < inTables.length; ++whichTable) {
				final PenetranceTable table = inTables[whichTable];
				cellId = new PenetranceTable.CellId(table.attributeCount);

				// do we need to use the model's penetrance or is the model
				// being skipped due to heterogeneity?
				if ((dd.multipleModelDatasetType.getValue() == SnpGenDocument.MIXED_MODEL_DATASET_TYPE.heterogeneous) && (whichTable != heterogeneousCurrentTable)) {
					// System.out.println("noise");
					// The current table is not the signal table, so generate
					// noise:
					for (int j = 0; j < table.attributeCount; ++j) {
						table.getAlleleFrequencies(j, alleleFrequencies);
						final int whichValue = SnpGenSimulator.noiseToOutput(inRandom, alleleFrequencies, inOutputStream, inOutputArray,
								whichOutputLine, destWhich++);
						cellId.setIndex(j, whichValue);
					}
					whichCell = cellId.toMasterIndex(3);
					if (inInstanceClass == 1) {
						++table.cellCaseCount[whichCell];
					} else {
						++table.cellControlCount[whichCell];
					}
				}
				else { //must either be hierarchical or it is heterogeneous and this row uses the current table
					assert (dd.multipleModelDatasetType.getValue() == SnpGenDocument.MIXED_MODEL_DATASET_TYPE.hierarchical)
					|| (whichTable == heterogeneousCurrentTable);
					// System.out.println("signal");
					// Pick a random number from 0 to 1 and see which
					// instance-interval it's in:
					rand = inRandom.nextDouble();
					whichCell = -1;
					for (int k = 0; k < table.cellCount; ++k) {
						if (rand < inInstanceIntervals[whichTable][k]) {
							whichCell = k;
							break;
						}
					}

					if (dd.createContinuousEndpoints.getBoolean()) {
						// if creating continuous endpoints use the cell's
						// penetrance value as the mean of a distribution
						final double penetranceForCell = table.cells[whichCell].getValue();
						final double nextGaussian = inRandom.nextGaussian();
						// see
						// http://www.javamex.com/tutorials/random_numbers/gaussian_distribution_2.shtml
						// Random.nextGaussian() method returns random numbers
						// with a mean of 0 and a standard deviation of 1.
						// to change the standard deviation, we multiply the
						// value.
						// to change the mean (average) of the distribution, we
						// add the required value;
						final double continuousEndpoint = (nextGaussian * dd.continuousEndpointsStandardDeviation.getDouble())
								+ penetranceForCell;
						if (dd.multipleModelDatasetType.getValue() == SnpGenDocument.MIXED_MODEL_DATASET_TYPE.heterogeneous) {
							assert heterogeneousCurrentTable != null;
							assert phenotypeValue == 0.0 : "for heterogeneous models, phenotype value should only be set once";
							phenotypeValue = continuousEndpoint;
						} else {	//must be hierarchical
							assert heterogeneousCurrentTable == null;
							final double weightedContinuousEndpoint = continuousEndpoint * modelFractions[whichTable];
							phenotypeValue += weightedContinuousEndpoint;
						}
						// System.err.println("row-" + row + " whichCell-" +
						// whichCell + " penetranceForCell-" + penetranceForCell
						// + " scaledOntoRangePenetrance-" +
						// scaledOntoRangePenetrance + " continuousEndpoint-" +
						// continuousEndpoint);
					} else {
						// if not continuous data use 0 or 1
						phenotypeValue = inInstanceClass;
					}
					assert (0 <= whichCell) && (whichCell < table.cellCount);
					if (inInstanceClass == 1) {
						++table.cellCaseCount[whichCell];
					} else {
						++table.cellControlCount[whichCell];
					}
					table.masterIndexToCellId(whichCell, cellId);
					for (int k = 0; k < table.attributeCount; ++k) {
						final int alleleSymbol = SnpGenSimulator.kAlleleSymbols[cellId.getIndex(k)];
						SnpGenSimulator.valueToOutput(alleleSymbol, null, inOutputStream,
								true, inOutputArray, whichOutputLine, destWhich++);
					}
				}
			} // end which Table

			final String instanceClassRepresentation = SnpGenDocument.kDecimalFormatTenDecimals.format(phenotypeValue);

			SnpGenSimulator.valueToOutput(inInstanceClass, instanceClassRepresentation, inOutputStream, false, inOutputArray,
					whichOutputLine, destWhich++);
			if (inOutputStream != null) {
				inOutputStream.println();
			}
			++whichOutputLine;
		}

		// // Output for testing:
		// if(inInstanceClass == 0)
		// {
		// NumberFormat nf = NumberFormat.getInstance();
		// nf.setMaximumFractionDigits(4);
		// for(int whichTable = 0; whichTable < inTables.length; ++whichTable)
		// {
		// PenetranceTable table = inTables[whichTable];
		// System.out.println("Table " + whichTable + ", penetrances");
		// for(int j = 0; j < 3; ++j)
		// {
		// for(int k = 0; k < 3; ++k)
		// {
		// System.out.print(nf.format(table.cells[3 * j + k].getValue()) +
		// "\t");
		// }
		// System.out.println();
		// }
		// System.out.println();
		// System.out.println("Table " + whichTable + ", allele frequencies");
		// double[] alleleFrequencies0 = new double[3];
		// double[] alleleFrequencies1 = new double[3];
		// table.getAlleleFrequencies(0, alleleFrequencies0);
		// table.getAlleleFrequencies(1, alleleFrequencies1);
		// for(int j = 0; j < 3; ++j)
		// {
		// for(int k = 0; k < 3; ++k)
		// {
		// System.out.print(nf.format(alleleFrequencies0[j] *
		// alleleFrequencies1[k]) + "\t");
		// }
		// System.out.println();
		// }
		// System.out.println();
		// System.out.println("Table " + whichTable + ", case-counts");
		// for(int j = 0; j < 3; ++j)
		// {
		// for(int k = 0; k < 3; ++k)
		// {
		// System.out.print(table.cellCaseCount[3 * j + k] + "\t");
		// }
		// System.out.println();
		// }
		// System.out.println();
		// System.out.println("Table " + whichTable + ", control-counts");
		// for(int j = 0; j < 3; ++j)
		// {
		// for(int k = 0; k < 3; ++k)
		// {
		// System.out.print(table.cellControlCount[3 * j + k] + "\t");
		// }
		// System.out.println();
		// }
		// System.out.println();
		// System.out.println();
		// }
		// }
	}

	// public void calcStatsForStdModels(SnpGenDocument inDoc) throws Exception
	// {
	// if(inDoc.inputFile != null)
	// {
	// Random random = createRandom(inDoc.randomSeed);
	// int popCount = 1000;
	// int bucketCount = 100;
	//
	// printStats(random, 0.02, 0.05F, 0.01, 0.2, popCount, bucketCount, -1);
	// printStats(random, 0.02, 0.05F, 0.01, 0.2, popCount, bucketCount, -1);
	// printStats(random, 0.02, 0.05F, 0.01, 0.2, popCount, bucketCount, -1);
	// printStats(random, 0.02, -1, 0.01, 0.2, popCount, bucketCount, -1);
	// printStats(random, 0.02, -1, 0.01, 0.2, popCount, bucketCount, -1);
	// printStats(random, 0.02, -1, 0.01, 0.2, popCount, bucketCount, -1);
	//
	// printStats(random, 0.1, 0.05F, 0.01, 0.4, popCount, bucketCount, -1);
	// printStats(random, 0.1, 0.05F, 0.01, 0.4, popCount, bucketCount, -1);
	// printStats(random, 0.1, 0.05F, 0.01, 0.4, popCount, bucketCount, -1);
	// printStats(random, 0.1, -1, 0.01, 0.4, popCount, bucketCount, -1);
	// printStats(random, 0.1, -1, 0.01, 0.4, popCount, bucketCount, -1);
	// printStats(random, 0.1, -1, 0.01, 0.4, popCount, bucketCount, -1);
	//
	// PenetranceTable[] tables = parseStandardTables(inDoc.inputFile);
	// int whichModel = 0;
	// System.out.println("Model #\tMAF\tHeritability\tRAS\tRAS percentile\tMinimum RAS\tMaximum RAS\tMean RAS\tStd Dev RAS\tBucket-counts");
	// for(PenetranceTable t: tables)
	// {
	// // Generate the distribution twice, to see whether there's any
	// significant variation:
	// printStats(random, t, popCount, bucketCount, whichModel);
	// printStats(random, t, popCount, bucketCount, whichModel);
	// ++whichModel;
	// }
	// }
	// }

	// private static void printStats(final double[] scores, final int
	// inBucketCount) {
	// final NumberFormat f = NumberFormat.getInstance();
	// f.setMaximumFractionDigits(2);
	// Arrays.sort(scores);
	// final int scoreCount = scores.length;
	// double sum = 0;
	// double sumOfSquares = 0;
	// for (final double d : scores) {
	// sum += d;
	// sumOfSquares += d * d;
	// }
	// final double mean = sum / scoreCount;
	// System.out.print("\t" + scoreCount + "\t" + scores[0] + "\t" +
	// scores[scoreCount - 1] + "\t" + mean + "\t"
	// + Math.sqrt((sumOfSquares / scoreCount) - (mean * mean)));
	// int whichScore = 0;
	// final double bucketWidth = (scores[scoreCount - 1] - scores[0]) /
	// inBucketCount;
	// int bucketCount;
	// int bucketCountSoFar = 0;
	// double bucketRight;
	// // System.out.println("\t" + scores[0] + "\t" + scores[inPopCount - 1]);
	// for (int i = 1; i < inBucketCount; ++i) // Don't count the last bucket,
	// // assume it's whatever's left
	// // at the end
	// {
	// bucketRight = scores[0] + (i * bucketWidth);
	// bucketCount = 0;
	// while (scores[whichScore] <= bucketRight) {
	// ++bucketCount;
	// ++whichScore;
	// }
	// // Now, whichScore is the first score past the current bucket
	// bucketCountSoFar += bucketCount;
	// System.out.print("\t" + f.format((100 * (double) bucketCount) /
	// scoreCount));
	// }
	// bucketCount = scoreCount - bucketCountSoFar;
	// System.out.print("\t" + f.format((100 * (double) bucketCount) /
	// scoreCount));
	// }
	//
	private static void setRandomSeed(final Random inRandom, final Integer inSeed) {
		if (inSeed != null) {
			inRandom.setSeed(inSeed);
		}
	}

	private static void valueToOutput(final int inValue, final String inValueString, final PrintWriter inOutputStream,
			final boolean inTabAfter, final int[][] inOutputArray, final int inWhichOutputLine, final int inWhichOutputColumn) {
		if (inOutputStream != null) {
			inOutputStream.print((inValueString != null) ? inValueString : inValue);
			if (inTabAfter) {
				inOutputStream.print("\t");
			}
		}
		if (inOutputArray != null) {
			inOutputArray[inWhichOutputLine][inWhichOutputColumn] = inValue;
		}
	}

	public static class PenetranceTableQuantile {
		public PenetranceTable[] tables;

		public PenetranceTableQuantile(final int inTableCount) {
			tables = new PenetranceTable[inTableCount];
		}
	}

	public interface ProgressHandler {
		public void setMaximum(int inMax);

		public void setValue(int inMax);
	}

	// private static class PenetranceTablePopulation {
	// public int currentTableCount;
	// public PenetranceTable[] tables;
	// }
}
