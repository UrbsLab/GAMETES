package org.epistasis.snpgen.simulator;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Random;

public class PenetranceTable implements Cloneable {
	private static final double kValueMax = 0.95;

	private static final double kPenetranceSum = 0.0D; // kPenetranceSum must be
	// 0 for the kValue
	// calculations to work.
	private static final double kErrorLimit = 0.01D;
	private static final int kWhichPenetranceCellNone = -1;
	public static int fixedConflictSuccessfully = 0;

	public static int fixedConflictUnsuccessfully = 0;

	// Penetrance-table parameters:
	public int attributeCount;

	private String[] attributeNames;

	public int snpStateCount;

	public int cellsPicked;

	public double desiredHeritability;

	private double actualHeritability;
	public int interactingAttributeCount;
	public double prevalence;
	public Double desiredPrevalence;

	public double edm;
	public double oddsRatio;
	public boolean useOriginAsStart;
	public double[] minorAlleleFrequencies;
	public double[] majorAlleleFrequencies;
	public double[][] stateProbability;

	public CellId startPoint;

	public int cellCount;
	public int basisSize;
	public BasisCell[] basis;
	public int basisNext;
	public LinkedList<PenetranceCellWithId> pendingCellsToSet;
	public boolean normalized;
	public boolean rowSumsValid;
	public PenetranceCell[] cells;
	public int[] cellCaseCount;
	public int[] cellControlCount;

	public double[] caseIntervals;
	public double[] controlIntervals;
	private final boolean usePointMethod;
	private CellId blockedOutCellForPointMethod;
	private int nextMasterCellIdForPointMethod;
	public String name;

	public PenetranceTable(final int inSnpStateCount, final int inAttributeCount) {
		snpStateCount = inSnpStateCount;
		attributeCount = inAttributeCount;
		usePointMethod = (attributeCount >= 6);
		cellCount = 1;
		basisSize = 1;
		for (int i = 0; i < attributeCount; ++i) {
			cellCount *= snpStateCount;
			basisSize *= (snpStateCount - 1);
		}
		normalized = false;
		minorAlleleFrequencies = new double[attributeCount];
		majorAlleleFrequencies = new double[attributeCount];
		stateProbability = new double[attributeCount][snpStateCount];
		startPoint = new CellId(attributeCount);
		cells = new PenetranceCell[cellCount];
		cellCaseCount = new int[cellCount];
		cellControlCount = new int[cellCount];
		// penetranceIsSet = new boolean[penetranceArraySize];
		pendingCellsToSet = new LinkedList<PenetranceCellWithId>();
		basis = new BasisCell[basisSize];
		basisNext = -1;

		// TODO: Could reuse the cells from run to run, to save time on
		// garbage-collection
		for (int i = 0; i < cellCount; ++i) {
			cells[i] = new PenetranceCell();
		}
		clear();
	}

	public void adjustHeritability() {
		boolean success;
		double herit;
		double factor;

		herit = calcHeritability();
		// System.out.println("Penetrance-table size: " + penetranceTableSize +
		// ", kValue: " + kValue + ", Raw heritability: " + herit);
		factor = Math.sqrt(desiredHeritability / herit);
		if (factor > 1.0D) {
			success = false;
		} else {
			success = true;
			for (final PenetranceCell c : cells) {
				c.setValue((factor * c.getValue()) + (prevalence * (1 - factor))); // The
				// intercept
				// allows
				// us
				// to
				// preserve
				// the
				// value
				// of
				// K
				// and
				// makes
				// the
				// heritability-scaling
				// work.
			}
			assert Math.abs(calcPrevalence() - prevalence) < PenetranceTable.kErrorLimit;
			calcAndSetHeritability();
			// System.out.println("factor, old herit, new herit, desired herit:\t"
			// + factor + "\t " + herit + "\t " + actualHeritability + "\t " +
			// desiredHeritability);
			assert Math.abs(actualHeritability - desiredHeritability) < PenetranceTable.kErrorLimit;
		}
		if (success) {
			edm = calcEdm();
			oddsRatio = calcOddsRatio();
		}
		normalized = success;
	}

	public void adjustPrevalence() {
		double scale = 1, offset = 0;

		if ((desiredPrevalence != null) && (desiredPrevalence != prevalence)) {
			if (desiredPrevalence < prevalence) {
				scale = desiredPrevalence / prevalence;
			} else if (desiredPrevalence > prevalence) {
				scale = (1 - desiredPrevalence) / (1 - prevalence);
				offset = (desiredPrevalence - prevalence) / (1 - prevalence);
			}
			for (final PenetranceCell c : cells) {
				c.setValue((scale * c.getValue()) + offset);
				// System.out.println(c.getValue());
				assert ((-PenetranceTable.kErrorLimit < c.getValue()) && (c.getValue() < (1F + PenetranceTable.kErrorLimit)));
			}
			calcAndSetPrevalence();
			assert Math.abs(prevalence - desiredPrevalence) < PenetranceTable.kErrorLimit;
		}
	}

	public double calcAndSetEdm() {
		edm = calcEdm();
		return edm;
	}

	public double calcAndSetHeritability() {
		actualHeritability = calcHeritability();
		return actualHeritability;
	}

	public double calcAndSetOddsRatio() {
		oddsRatio = calcOddsRatio();
		return oddsRatio;
	}

	public double calcAndSetPrevalence() {
		prevalence = calcPrevalence();
		return prevalence;
	}

	public double calcEdm() {
		double sum;
		double outRas;
		double prob;
		double diff;
		double kProduct;
		final CellId cellId = new CellId(attributeCount);

		calcAndSetPrevalence();
		sum = 0;
		for (int i = 0; i < cellCount; ++i) {
			masterIndexToCellId(i, cellId);
			prob = getProbabilityProduct(cellId);
			diff = cells[i].getValue() - prevalence;
			sum += prob * prob * diff * diff;
		}
		kProduct = prevalence * (1 - prevalence);
		outRas = sum / (2 * kProduct * kProduct);
		return outRas;
	}

	public double calcHeritability() {
		double sum;
		double outHeritability;
		double prob;
		double diff;
		final CellId cellId = new CellId(attributeCount);

		calcAndSetPrevalence();
		sum = 0;
		for (int i = 0; i < cellCount; ++i) {
			masterIndexToCellId(i, cellId);
			prob = getProbabilityProduct(cellId);
			diff = cells[i].getValue() - prevalence;
			sum += prob * diff * diff;
		}
		outHeritability = sum / (prevalence * (1 - prevalence));
		return outHeritability;
	}

	// outMarginalPenetrances[whichLocus][whichAlleleValue]
	public double[][] calcMarginalPrevalences() {
		final NumberFormat nf = NumberFormat.getInstance();
		nf.setMaximumFractionDigits(3);
		final double[][] outMarginalPenetrances = new double[attributeCount][snpStateCount];
		final CellId cellId = new CellId(attributeCount);
		// For each locus,
		for (int whichLocus = 0; whichLocus < attributeCount; ++whichLocus) {
			final double[] freq = new double[3];
			getAlleleFrequencies(whichLocus, freq);
			// System.out.println();
			// System.out.println();
			// System.out.println("-------------------");
			// System.out.println("Locus " + whichLocus);
			// System.out.println("-------------------");
			// System.out.println();
			// for each allele-value for that locus,
			for (int whichAlleleValue = 0; whichAlleleValue < snpStateCount; ++whichAlleleValue) {
				// System.out.println();
				// System.out.println();
				// System.out.println("Allele-value " + whichAlleleValue);
				// System.out.println("-------------------");
				// System.out.println();
				double prevalence;
				double prob;

				prevalence = 0;
				// iterate over all of the cells in the table,
				for (int i = 0; i < cellCount; ++i) {
					masterIndexToCellId(i, cellId);
					// if(i % 3 == 0)
					// System.out.println();
					// if(i % 9 == 0)
					// System.out.println();
					// if(cellId.getIndex(whichLocus) == whichAlleleValue)
					// System.out.print(nf.format(cells[i].getValue()) + "\t");
					// else
					// System.out.print("-" + "\t");
					// and if the current cell matches whichLocus and
					// whichAlleleValue,
					if (cellId.getIndex(whichLocus) == whichAlleleValue) {
						// then add it the cumulative caseProportion value:
						prob = getProbabilityProduct(cellId);
						prevalence += prob * cells[i].getValue();
					}
				}
				prevalence /= freq[whichAlleleValue];
				outMarginalPenetrances[whichLocus][whichAlleleValue] = prevalence;
			}
		}
		// System.out.println();
		// System.out.println();
		// System.out.println("-------------------");
		// System.out.println("Full table");
		// System.out.println("-------------------");
		// System.out.println();
		// try
		// {
		// PrintWriter writer = new PrintWriter(System.out);
		// writer.println();
		// writer.println();
		// writer.println("-------------------");
		// writer.println();
		// write(writer);
		// writer.println();
		// writer.println();
		// writer.println("-------------------");
		// writer.println();
		// writer.flush();
		// }
		// catch(IOException e)
		// {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// }
		return outMarginalPenetrances;
	}

	public double calcOddsRatio() {
		double sumTP, sumTN, sumFP, sumFN;
		double outOddsRatio;
		double prob;
		double prev;
		final CellId cellId = new CellId(attributeCount);

		calcAndSetPrevalence();
		sumTP = 0;
		sumTN = 0;
		sumFP = 0;
		sumFN = 0;
		for (int i = 0; i < cellCount; ++i) {
			masterIndexToCellId(i, cellId);
			prob = getProbabilityProduct(cellId);
			prev = cells[i].getValue();
			if (prev >= prevalence) {
				sumTP += prob * prev;
				sumFP += prob * (1 - prev);
			} else {
				sumTN += prob * (1 - prev);
				sumFN += prob * prev;
			}
		}
		outOddsRatio = (sumTP * sumTN) / (sumFN * sumFP);
		return outOddsRatio;
	}

	// Calculate caseIntervals such that the length of the interval from
	// caseIntervals[i-1] to caseIntervals[i]
	// == the probability that a random case is in the ith cell of the
	// penetrance table; similarly for controls.
	public void calcSamplingIntervals() {
		double prob;
		double penetrance;
		CellId cellId;
		double sumCaseFractions, sumControlFractions;
		// caseIntervals[i] == the right edge of the ith probability-interval
		// for cases; similarly for controls

		cellId = new CellId(attributeCount);
		sumCaseFractions = 0;
		sumControlFractions = 0;
		caseIntervals = new double[cellCount];
		controlIntervals = new double[cellCount];
		// Sum up all the case-fractions, storing the partial case-fractions to
		// the caseIntervals array; do the same with controls:
		for (int i = 0; i < cellCount; ++i) {
			masterIndexToCellId(i, cellId);
			prob = getProbabilityProduct(cellId);
			penetrance = getPenetranceValue(cellId);

			sumCaseFractions += prob * penetrance;
			sumControlFractions += prob * (1 - penetrance);
			caseIntervals[i] = sumCaseFractions;
			controlIntervals[i] = sumControlFractions;
		}
		assert Math.abs((sumCaseFractions + sumControlFractions) - 1.0) < PenetranceTable.kErrorLimit;
		// Divide each element of caseIntervals and controlIntervals by the
		// appropriate total sum.
		for (int i = 0; i < cellCount; ++i) {
			caseIntervals[i] /= sumCaseFractions;
			controlIntervals[i] /= sumControlFractions;
		}
	}

	public int cellIdToMasterIndex(final CellId inCellId) {
		return inCellId.toMasterIndex(snpStateCount, attributeCount);
	}

	public void checkRowSums() {
		rowSumsValid = checkRowSums(prevalence);
	}

	public boolean checkRowSums(final double inDesiredRowSum) {
		boolean success = true;
		final CellId cellId = new CellId(attributeCount);
		double sum;

		for (int whichDimension = 0; whichDimension < attributeCount; ++whichDimension) {
			// This is a little redundant, but only by a factor of snpStateCount
			// --
			// and it's easier to do than the non-redundant method.
			for (int i = 0; i < cellCount; ++i) {
				masterIndexToCellId(i, cellId);
				if (allValuesSetInRow(cellId, whichDimension)) {
					sum = calculateWeightedSumOfSetPenetranceValues(cellId, whichDimension);
					if (Math.abs(sum - inDesiredRowSum) > PenetranceTable.kErrorLimit) {
						success = false;
						// System.out.println("* * * ERROR * * -- Incorrect row-sum along dimension "
						// + whichDimension + " from cell " + i +
						// "; correct value is " + caseProportion +
						// ", actual value is " + sum);
						// printIn3D();
						// sum =
						// calculateWeightedSumOfSetPenetranceValues(cellId,
						// whichDimension); // For debugging only
					}
				}
			}
		}
		return success;
	}

	public void clear() {
		for (int i = 0; i < cellCount; ++i) {
			cells[i].clear();
		}

		for (int i = 0; i < cellCount; ++i) {
			cellCaseCount[i] = 0;
			cellControlCount[i] = 0;
		}
	}

	public void clearPenetranceValue(final CellId inCellId) {
		final int index = cellIdToMasterIndex(inCellId);
		if (cells[index].isBasisElement) {
			// Make the basis not point to the penetranceTable:
			final int whichBasisElement = cells[index].getWhichBasisElement();
			basis[whichBasisElement].whichPenetranceCell = PenetranceTable.kWhichPenetranceCellNone;
		}
		cells[index].isSet = false;
		cells[index].isBasisElement = false; // Make the
		// penetranceTable not
		// point to the basis
	}

	@Override
	public Object clone() throws CloneNotSupportedException {
		final PenetranceTable pt = (PenetranceTable) super.clone();
		pt.attributeNames = Arrays.copyOf(attributeNames, attributeNames.length);
		pt.minorAlleleFrequencies = Arrays.copyOf(minorAlleleFrequencies, minorAlleleFrequencies.length);
		pt.majorAlleleFrequencies = Arrays.copyOf(majorAlleleFrequencies, majorAlleleFrequencies.length);

		pt.stateProbability = new double[stateProbability.length][];
		for (int i = 0; i < stateProbability.length; ++i) {
			pt.stateProbability[i] = Arrays.copyOf(stateProbability[i], stateProbability[i].length);
		}

		pt.basis = new BasisCell[basis.length];
		for (int i = 0; i < basis.length; ++i) {
			if (basis[i] == null) {
				pt.basis[i] = null;
			} else {
				pt.basis[i] = (BasisCell) basis[i].clone();
			}
		}

		pt.cells = new PenetranceCell[cells.length];
		for (int i = 0; i < cells.length; ++i) {
			pt.cells[i] = (PenetranceCell) cells[i].clone();
			pt.cellCaseCount[i] = cellCaseCount[i];
			pt.cellControlCount[i] = cellControlCount[i];
		}

		return pt;
	}

	// Return the count of any cells not set yet
	public int countRemainingEmptyCells() {
		int outEmpty;

		outEmpty = 0;
		for (int i = 0; i < cellCount; ++i) {
			if (!cells[i].isSet) {
				++outEmpty;
			}
		}
		return outEmpty;
	}

	public ErrorState generateUnnormalized(final Random inRandom) throws Exception {
		ErrorState error;
		ErrorState outError = ErrorState.None;
		final CellId cellId = new CellId(attributeCount);
		boolean emptyCellsRemaining;

		if (usePointMethod) {
			blockedOutCellForPointMethod = new CellId(attributeCount);
			masterIndexToCellId(inRandom.nextInt(cellCount), blockedOutCellForPointMethod);
			nextMasterCellIdForPointMethod = 0;
		}

		final long foo = inRandom.nextLong();
		// foo = 8442823125929271046L;
		inRandom.setSeed(foo);
		cellsPicked = 0;
		while (true) {
			emptyCellsRemaining = emptyCellRemaining();
			if (!emptyCellsRemaining) {
				break;
			}
			pickNextEmptyCell(inRandom, cellId);
			++cellsPicked;
			// if(cellsPicked > basisSize)
			// {
			// outError = ErrorState.Ambiguous;
			// break;
			// }
			error = setRandomPenetranceValueAndPropagateIt(cellId);
			if (error != ErrorState.None) {
				outError = error;
				break;
			}
		}
		return outError;
	}

	public double getActualHeritability() {
		return actualHeritability;
	}

	// // Return true if there are any cells not set yet, false otherwise.
	// private boolean copyIsSetArray(boolean[] inSource, boolean[] inDest)
	// {
	// boolean outFoundEmpty;
	//
	// assert(inSource.length == inDest.length);
	// outFoundEmpty = false;
	// for(int i = 0; i < inSource.length; ++i)
	// {
	// outFoundEmpty |= !inSource[i];
	// inDest[i] = inSource[i];
	// }
	// return outFoundEmpty;
	// }

	// frequency[0] is the major-major allele and frequency[2] is the
	// minor-minor allele.
	public void getAlleleFrequencies(final int inWhichAttribute, final double[] outAlleleFrequencies) {
		final double maf = minorAlleleFrequencies[inWhichAttribute];
		PenetranceTable.calcAlleleFrequencies(maf, outAlleleFrequencies);
	}

	// private double pickRandomPenetranceValue(CellId inCellId)
	// {
	// // return 1.0;
	// // return random.nextGaussian();
	// assert(penetranceBasisNext < penetranceBasisSize);
	// return penetranceBasisValue[penetranceBasisNext++];
	// }

	public String[] getAttributeNames() {
		return attributeNames;
	}

	public double[] getMinorAlleleFrequencies() {
		return minorAlleleFrequencies;
	}

	public double getPenetranceValue(final CellId inCellId) {
		final int index = cellIdToMasterIndex(inCellId);
		return cells[index].getValue();
	}

	public double getProbabilityProduct(final CellId inCellId) {
		double product;

		product = 1;
		for (int dimension = 0; dimension < attributeCount; ++dimension) {
			product *= stateProbability[dimension][inCellId.getIndex(dimension)];
		}
		return product;
	}

	public double getQuantileScore(final boolean inUseOddsRatio) {
		if (inUseOddsRatio) {
			return oddsRatio;
		} else {
			return edm;
		}
	}

	public void initialize(final Random inRandom, final double[] inMinorAlleleFrequencies) {
		rowSumsValid = false;
		normalized = false;

		useOriginAsStart = false;

		setMinorAlleleFrequencies(inMinorAlleleFrequencies);

		double value;
		double basisSquaredSum = 0;
		// Generate basis parameters and normalize them:
		for (int i = 0; i < basisSize; ++i) {
			value = inRandom.nextGaussian();
			basis[i] = new BasisCell(value, PenetranceTable.kWhichPenetranceCellNone);
			basisSquaredSum += value * value;
		}
		final double normalizingFactor = 1 / Math.sqrt(basisSquaredSum);
		// Make the norm of the basis = 1:
		for (int i = 0; i < basisSize; ++i) {
			basis[i].value *= normalizingFactor;
		}
		basisNext = 0;

		// start with random SNP states if desired, else use (0,0,...,0)
		for (int i = 0; i < attributeCount; ++i) {
			if (useOriginAsStart) {
				startPoint.setIndex(i, 0);
			} else {
				startPoint.setIndex(i, inRandom.nextInt(snpStateCount));
			}
		}
	}

	public void masterIndexToCellId(final int inMasterIndex, final CellId outCellId) {
		assert ((0 <= inMasterIndex) && (inMasterIndex < cellCount));
		outCellId.fromMasterIndex(snpStateCount, attributeCount, inMasterIndex);
	}

	public void normalize() {
		scaleToUnitInterval();
		// adjustPenetrance() must be done before adjustHeritability() because
		// adjustHeritability() preserves penetrance, but not vice-versa.
		adjustPrevalence();
		adjustHeritability();
	}

	public void print() {
		for (int i = 0; i < cellCount; ++i) {
			if ((i % 3) == 0) {
				System.out.println();
			}
			if ((i % 9) == 0) {
				System.out.println();
			}
			System.out.print(cells[i].getValue() + "\t");
		}
	}

	public void printIn3D() {
		final int[] indices = new int[3];
		CellId tempCellId = new CellId(3, 0);
		double value;
		long longValue;

		for (int i = 0; i < snpStateCount; ++i) {
			for (int j = 0; j < snpStateCount; ++j) {
				for (int k = 0; k < snpStateCount; ++k) {
					// Inefficient but simple:
					indices[0] = i;
					indices[1] = j;
					indices[2] = k;
					tempCellId = new CellId(indices);
					if (getPenetranceIsSet(tempCellId)) {
						value = getPenetranceValue(tempCellId);
						longValue = (long) ((value * 10000.0F) + 0.5);
						// System.out.print(longValue / 10000.0 + ",    ");
						System.out.print((longValue / 10000.0) + "\t");
					} else {
						// System.out.print("----,    ");
						System.out.print("----\t");
					}
				}
				System.out.println();
			}
			System.out.println();
		}
	}

	public void printRow(final CellId inWhichCell, final int inWhichDimension) {
		final CellId tempCellId = new CellId(inWhichCell);

		System.out.print("Row through ");
		inWhichCell.print();
		System.out.print(" along " + inWhichDimension + ": ");
		for (int j = 0; j < snpStateCount; ++j) {
			tempCellId.setIndex(inWhichDimension, j);
			if (getPenetranceIsSet(tempCellId)) {
				System.out.print(getPenetranceValue(tempCellId) + ", ");
			} else {
				System.out.print("*, ");
			}
		}
	}

	public void saveBasisToFile(final File inDestFile, final boolean inAppend) throws IOException {

		try (PrintWriter outputStream = new PrintWriter(new FileWriter(inDestFile, inAppend));) {
			if (inAppend) {
				outputStream.println();
				outputStream.println();
				outputStream.println();
				outputStream.println();
			}
			outputStream.println("Basis:");
			outputStream.println();
			for (int i = 0; i < basis.length; ++i) {
				outputStream.print(basis[i].value);
				if (i < (basis.length - 1)) {
					outputStream.print(",  ");
				}
			}
		}
	}

	public double saveCaseControlValuesToFile(final File inDestFile, final boolean inAppend) throws IOException {
		double outBalancedAccuracy;

		try(PrintWriter outputStream = new PrintWriter(new FileWriter(inDestFile, inAppend));) {
			if (inAppend) {
				outputStream.println();
				outputStream.println();
				outputStream.println();
				outputStream.println();
			}
			outputStream.print("Attribute names:");
			for (final String n : attributeNames) {
				outputStream.print("\t" + n);
			}
			outputStream.println();

			int totalCaseCount = 0;
			int totalControlCount = 0;
			int correctCaseCount = 0;
			int correctControlCount = 0;
			for (int i = 0; i < cellCount; ++i) {
				totalCaseCount += cellCaseCount[i];
				totalControlCount += cellControlCount[i];
			}
			for (int i = 0; i < cellCount; ++i) {
				if ((cellCaseCount[i] * totalControlCount) >= (cellControlCount[i] * totalCaseCount)) {
					correctCaseCount += cellCaseCount[i];
				} else {
					correctControlCount += cellControlCount[i];
				}
			}
			outBalancedAccuracy = (((double) correctCaseCount / (double) totalCaseCount) + ((double) correctControlCount / (double) totalControlCount)) / 2;
			outputStream.println("Balanced accuracy of best model: " + outBalancedAccuracy);

			outputStream.println("Case values:");
			for (int i = 0; i < cellCount; ++i) {
				if (i > 0) // Don't print blank lines at the beginning
				{
					if ((i % snpStateCount) == 0) {
						outputStream.println(); // Skip to the next line of
						// output
					}
					if ((i % (snpStateCount * snpStateCount)) == 0) {
						outputStream.println(); // Print a blank line between
						// squares
					}
				}
				outputStream.print(cellCaseCount[i]);
				if (((i + 1) % snpStateCount) != 0) {
					outputStream.print(",  ");
				}
			}
			outputStream.println();
			outputStream.println("Control values:");
			for (int i = 0; i < cellCount; ++i) {
				if (i > 0) // Don't print blank lines at the beginning
				{
					if ((i % snpStateCount) == 0) {
						outputStream.println(); // Skip to the next line of
						// output
					}
					if ((i % (snpStateCount * snpStateCount)) == 0) {
						outputStream.println(); // Print a blank line between
						// squares
					}
				}
				outputStream.print(cellControlCount[i]);
				if (((i + 1) % snpStateCount) != 0) {
					outputStream.print(",  ");
				}
			}
		}
		return outBalancedAccuracy;
	}

	public void savePenetranceCell(final PenetranceCellWithId inCell) {
		final CellId cellId = inCell.cellId;
		assert (!getPenetranceIsSet(cellId));
		final int index = cellIdToMasterIndex(cellId);
		cells[index] = new PenetranceCell(inCell);
		if (inCell.isBasisElement) {
			// if(cellIdToMasterIndex(cellId) == -1)
			// index = -1000;
			// System.out.println("Setting whichPenetranceCell for " +
			// inCell.getWhichBasisElement() + " to " +
			// cellIdToMasterIndex(cellId));
			basis[inCell.getWhichBasisElement()].whichPenetranceCell = cellIdToMasterIndex(cellId);
		}
	}

	public void saveToFile(final File inDestFile, final boolean inAppend, final boolean inSaveUnnormalized) throws IOException {
		try (PrintWriter outputStream = new PrintWriter(new FileWriter(inDestFile, inAppend));) {
			if (inAppend) {
				outputStream.println();
				outputStream.println();
				outputStream.println();
				outputStream.println();
			}
			writeWithStats(outputStream, inSaveUnnormalized);
		}
	}

	public void scaleToUnitInterval() {
		double min, max;
		double slope;

		max = cells[0].getValue();
		min = cells[0].getValue();
		for (final PenetranceCell c : cells) {
			if (max < c.getValue()) {
				max = c.getValue();
			}
			if (min > c.getValue()) {
				min = c.getValue();
			}
		}
		// At this point, min must be < 0 and max must be > 0, from the way the
		// penetrance table was constructed.
		// We want slope * min + kValue = 0
		// and slope * max + kValue = 1.
		// So kValue = min / (min - max)
		// and slope = - kValue / min.
		prevalence = min / (min - max);
		if (prevalence > PenetranceTable.kValueMax) {
			prevalence = PenetranceTable.kValueMax;
		}
		slope = -prevalence / min;
		for (final PenetranceCell c : cells) {
			c.setValue((slope * c.getValue()) + prevalence);
			// The unnormalized penetrance table was constructed to have a
			// weighted average == 0,
			// so the new penetrance table has a weighted average == caseProportion.
		}
	}

	public void setAttributeNames(final String[] attributeNames) {
		this.attributeNames = attributeNames;
	}

	public void setMinorAlleleFrequencies(final double[] inMinorAlleleFrequencies) {
		assert inMinorAlleleFrequencies.length == attributeCount;
		for (int i = 0; i < attributeCount; ++i) {
			minorAlleleFrequencies[i] = inMinorAlleleFrequencies[i];
			majorAlleleFrequencies[i] = 1 - minorAlleleFrequencies[i];
		}

		// stateProbability[i][0] == the probability of each state of the ith
		// attribute.
		// If snpStateCount == 3, stateProbability[i][0] == prob of AA, [i][1]
		// == prob of Aa or aA, and [i][2] == prob of aa;
		// ie, the major allele comes first and the minor allele comes last.
		// What do others mean?
		// 4 = AAA, AAa or AaA or aAA, Aaa or aAa or aaA, aaa
		// etc (binomial expansion)

		int comb = 1; // N = snpStateCount - 1, comb(N, 0) = N! / (0! * (N-0)!)
		// = 1
		for (int j = 0; j < snpStateCount; ++j) {
			// System.out.println("comb(" + (snpStateCount - 1) + ", " + j +
			// ") = " + comb);
			for (int i = 0; i < attributeCount; ++i) {
				stateProbability[i][j] = comb * Math.pow(minorAlleleFrequencies[i], j)
						* Math.pow(majorAlleleFrequencies[i], snpStateCount - j - 1);
			}
			// Calculate the next comb:
			// N = snpStateCount - 1, comb(N, j) = N! / (j! * (N-j)!)
			// comb(N, j+1) = comb(N, j) * (N - (j-1)) / j
			comb *= snpStateCount - 1 - j; // Divide the denominator by (N-j)
			comb /= j + 1; // Multiply the denominator by the next j
		}
	}

	public void setPenetranceValue(final CellId inCellId, final double inValue) {
		// assert (!getPenetranceIsSet(inCellId));
		final int index = cellIdToMasterIndex(inCellId);
		cells[index].setValue(inValue);
		cells[index].isSet = true;
	}

	public void write(final PrintWriter outputStream) throws IOException {
		write(outputStream, "\t");
	}

	public void write(final PrintWriter outputStream, final String delimiter) throws IOException {
		for (int i = 0; i < cellCount; ++i) {
			if (i > 0) // Don't print blank lines at the beginning
			{
				if ((i % snpStateCount) == 0) {
					outputStream.println(); // Skip to the next line of output
				}
				if ((i % (snpStateCount * snpStateCount)) == 0) {
					outputStream.println(); // Print a blank line between
					// squares
				}
			}
			outputStream.print(cells[i].getValue());
			if (((i + 1) % snpStateCount) != 0) {
				outputStream.print(delimiter);
			}
		}
	}

	public void writeWithStats(final PrintWriter outputStream, final boolean inSaveUnnormalized) throws IOException {
		// outputStream.println("Attribute count: " + attributeCount);
		outputStream.print("Attribute names:");
		for (final String n : attributeNames) {
			outputStream.print("\t" + n);
		}
		outputStream.println();
		outputStream.print("Minor allele frequencies:");
		for (final Double freq : minorAlleleFrequencies) {
			outputStream.print("\t" + freq);
		}
		outputStream.println();
		if (!normalized && !inSaveUnnormalized) {
			outputStream.println("Failed to normalize penetrance table!");
		} else {
			outputStream.println("K: " + prevalence);
			outputStream.println("Heritability: " + actualHeritability);
			outputStream.println("Ease-of-detection metric: " + edm);
			outputStream.println("Odds ratio: " + oddsRatio);
			if (normalized) {
				if (rowSumsValid) {
					outputStream.println("Table has passed the row-sum test.");
				} else {
					outputStream.println("Table has FAILED the row-sum test.");
				}
			} else {
				outputStream.println("Table is NOT normalized.");
			}
			outputStream.println();
			outputStream.println("Table:");
			outputStream.println();
			write(outputStream, ",  ");
			if (!normalized && inSaveUnnormalized) {
				boolean writeBasis = false;
				for (final BasisCell basi : basis) {
					if (basi != null) {
						writeBasis = true;
						break;
					}
				}
				if (writeBasis) {
					outputStream.println();
					outputStream.println();
					outputStream.println("Basis:");
					outputStream.println();
					for (final BasisCell basi : basis) {
						outputStream.println(basi.whichPenetranceCell + ", " + basi.value);
					}
				}
			}
		}
	}

	// Return true iff all the penetrance-values are set in the row along
	// inWhichDimension through the cell specified by inCellId.
	// inWhichDimension is the same as which-snp
	private boolean allValuesSetInRow(final CellId inCellId, final int inWhichDimension) {
		return (countFilledCells(inCellId, inWhichDimension, null) == snpStateCount);
	}

	private double calcPrevalence() {
		double outPrevalence;
		double prob;
		final CellId cellId = new CellId(attributeCount);

		outPrevalence = 0;
		for (int i = 0; i < cellCount; ++i) {
			masterIndexToCellId(i, cellId);
			prob = getProbabilityProduct(cellId);
			outPrevalence += prob * cells[i].getValue();
		}
		return outPrevalence;
	}

	// All of the cells in the row through inCellId along inWhichDimension are
	// filled,
	// so we can use them to calculate the value at inCellId.
	private double calculateForcedPenetranceValue(final CellId inCellId, final int inWhichDimension) {
		double sum;
		double outValue;
		int whereIsCellAlongDimension;

		whereIsCellAlongDimension = inCellId.getIndex(inWhichDimension);
		sum = calculateWeightedSumOfSetPenetranceValues(inCellId, inWhichDimension);
		outValue = (PenetranceTable.kPenetranceSum - sum) / stateProbability[inWhichDimension][whereIsCellAlongDimension];

		// For debugging only:
		assert (countFilledCells(inCellId, inWhichDimension, null) == (snpStateCount - 1));
		setPenetranceValue(inCellId, outValue);
		// printRow(inCellId, inWhichDimension); System.out.println();
		sum = calculateWeightedSumOfSetPenetranceValues(inCellId, inWhichDimension);
		assert ((Math.abs(sum - PenetranceTable.kPenetranceSum) < PenetranceTable.kErrorLimit));
		clearPenetranceValue(inCellId);

		return outValue;
	}

	// Return the sum of the set penetrance-values in the row along
	// inWhichDimension through the cell specified by inCellId.
	// inWhichDimension is the same as which-snp
	private double calculateWeightedSumOfSetPenetranceValues(final CellId inCellId, final int inWhichDimension) {
		double sum;
		final CellId tempCellId = new CellId(inCellId);

		sum = 0;
		for (int i = 0; i < snpStateCount; ++i) {
			tempCellId.setIndex(inWhichDimension, i);
			if (getPenetranceIsSet(tempCellId)) {
				sum += stateProbability[inWhichDimension][i] * getPenetranceValue(tempCellId);
			}
		}
		return sum;
	}

	// For each cell in the "row" specified by inWhichDimension of the
	// hypercube, return how many cells are set.
	// If there is at least one empty cell, outEmptyCellId returns one of them.
	private int countFilledCells(final CellId inWhichCell, final int inWhichDimension, final CellId outEmptyCellId) {
		int filledCells;
		final CellId tempCellId = new CellId(inWhichCell);

		filledCells = 0;
		for (int j = 0; j < snpStateCount; ++j) {
			tempCellId.setIndex(inWhichDimension, j);
			if (getPenetranceIsSet(tempCellId)) {
				++filledCells;
			} else {
				if (outEmptyCellId != null) {
					outEmptyCellId.copyFrom(tempCellId);
				}
			}
		}
		return filledCells;
	}

	// Return true if there are any cells not set yet, false otherwise.
	private boolean emptyCellRemaining() {
		boolean outFoundEmpty;

		outFoundEmpty = false;
		for (int i = 0; i < cellCount; ++i) {
			if (!cells[i].isSet) {
				outFoundEmpty = true;
				break;
			}
		}
		return outFoundEmpty;
	}

	private boolean getPenetranceIsSet(final CellId inCellId) {
		final int index = cellIdToMasterIndex(inCellId);
		return cells[index].isSet;
	}

	// inPreviousAttempts can be used for randomization if we want determinism
	private void pickNextEmptyCell(final Random inRandom, final CellId outCellId) throws Exception {
		int attempts;
		int masterIndex;

		// // For testing only -- use the block from 0 to snpStateCount - 2 (ie,
		// 1, in most cases) in each dimension:
		// outCellId.copyFrom(currentRandomCell);
		// int currentIndex;
		// int i = snpCount - 1;
		// boolean success = false;
		// while(i >= 0)
		// {
		// currentIndex = currentRandomCell.getIndex(i);
		// if(currentIndex >= snpStateCount - 2)
		// {
		// // If the current index is maxed out then set it to zero and move
		// back to the index before it:
		// currentRandomCell.setIndex(i, 0);
		// --i;
		// }
		// else
		// {
		// currentRandomCell.setIndex(i, currentIndex + 1);
		// success = true;
		// break;
		// }
		// }

		// THE REAL VERSION:
		if (usePointMethod) {
			boolean found = false;
			while (nextMasterCellIdForPointMethod < cellCount) {
				masterIndexToCellId(nextMasterCellIdForPointMethod++, outCellId);
				if (!blockedOutCellForPointMethod.matchesOnAnyDimension(outCellId)) {
					// The point we're returning is not on one of the
					// blocked-out staves, so it should not be set yet:
					assert !cells[nextMasterCellIdForPointMethod - 1].isSet;
					found = true;
					break;
				}
			}
			if (!found) {
				throw new Exception("Unable to find an empty cell that works");
			}
		} else {
			attempts = 0;
			while (true) {
				masterIndex = inRandom.nextInt(cellCount);
				if (!cells[masterIndex].isSet) {
					break;
				}
				if (attempts > 100) {
					throw new Exception("Unable to find an empty cell that works");
				}
			}
			masterIndexToCellId(masterIndex, outCellId);
		}
	}

	// Returns true if the value at inCellId causes a conflict, false otherwise.
	private ErrorState setRandomPenetranceValueAndPropagateIt(final CellId inCellId) throws Exception {
		ErrorState outError = ErrorState.None;
		int filledCells;
		PenetranceCellWithId penetranceCell;
		PenetranceCellWithId currPenetranceCell;
		final CellId emptyCellId = new CellId(attributeCount);
		double sum;

		penetranceCell = new PenetranceCellWithId(inCellId);
		penetranceCell.isBasisElement = true;
		pendingCellsToSet.add(penetranceCell);
		QUEUE: while ((currPenetranceCell = pendingCellsToSet.poll()) != null) {
			// TODO: If we add a penetranceIsPending global array, we can avoid
			// putting redundant entries in the queue in the first place.
			if (!getPenetranceIsSet(currPenetranceCell.cellId)) {
				assert (currPenetranceCell.isSet || currPenetranceCell.isBasisElement);
				if (currPenetranceCell.isBasisElement) {
					if (basisNext >= basisSize) {
						outError = ErrorState.Ambiguous;
						break QUEUE;
					}
					currPenetranceCell.setValue(basis[basisNext].value);
					currPenetranceCell.setWhichBasisElement(basisNext);
					++basisNext;
				}
				savePenetranceCell(currPenetranceCell);
				boolean foundError = false;
				// Check each "row" in the snpCount-dimensional hypercube that
				// goes through currPenetranceCell.cellId:
				for (int whichDimension = 0; whichDimension < attributeCount; ++whichDimension) {
					// For each cell in the current "row" of the hypercube,
					// count how many cells are set:
					filledCells = countFilledCells(currPenetranceCell.cellId, whichDimension, emptyCellId);
					// If the current row is all filled in, then check for a
					// conflict:
					if (filledCells == snpStateCount) {
						sum = calculateWeightedSumOfSetPenetranceValues(currPenetranceCell.cellId, whichDimension);
						if (Math.abs(sum - PenetranceTable.kPenetranceSum) > PenetranceTable.kErrorLimit) {
							foundError = true;
							// Don't bother trying to fix conflicts, it only
							// works a small fraction of the time.
							// if (!fixConflict(currPenetranceCell,
							// whichDimension))
							{
								outError = ErrorState.Conflict;
								break QUEUE;
							}
						}
					}

					// If there are snpStateCount - 1 cells set in the current
					// row,
					// then we can propagate to the remaining empty cell:
					if (filledCells == (snpStateCount - 1)) {
						assert (!getPenetranceIsSet(emptyCellId));
						pendingCellsToSet.add(new PenetranceCellWithId(emptyCellId, calculateForcedPenetranceValue(emptyCellId,
								whichDimension)));
					}
				}
				if (foundError) {
					if (!checkRowSums(0)) {
						// fixConflict said that it succeeded, but it actually
						// failed:
						// System.out.println("Failed to fix a conflict, but thought we succeeded!");
						++PenetranceTable.fixedConflictUnsuccessfully;
						outError = ErrorState.Conflict;
						break QUEUE;
					} else {
						++PenetranceTable.fixedConflictSuccessfully;
						// System.out.println("Successfully fixed a conflict");
					}
				}

			}
			// checkRowSums(0);
		}
		return outError;
	}

	// frequency[0] is the major-major allele and frequency[2] is the
	// minor-minor allele.
	public static void calcAlleleFrequencies(final double maf, final double[] outAlleleFrequencies) {
		outAlleleFrequencies[0] = (1.0 - maf) * (1.0 - maf); // major-major
		outAlleleFrequencies[1] = 2.0 * maf * (1.0 - maf); // major-minor
		outAlleleFrequencies[2] = maf * maf; // minor-minor
	}

	// Calculate outInstanceIntervals such that the length of the interval from
	// outInstanceIntervals[i-1] to outInstanceIntervals[i]
	// == the probability that a random instance is in the ith cell of the
	// penetrance table.
	// This method calculates outInstanceIntervals from the relative values of
	// inCellInstanceCounts.
	// public static void calcSamplingIntervals(final int inCellCount, final
	// int[] inCellInstanceCounts, final double[] outInstanceIntervals) {
	// double sumCaseFractions;
	// // caseIntervals[i] == the right edge of the ith probability-interval
	// // for cases; similarly for controls
	//
	// assert inCellInstanceCounts.length == inCellCount;
	// sumCaseFractions = 0;
	// // Sum up all the case-fractions, storing the partial case-fractions to
	// // the caseIntervals array:
	// for (int i = 0; i < inCellCount; ++i) {
	// sumCaseFractions += inCellInstanceCounts[i];
	// outInstanceIntervals[i] = sumCaseFractions;
	// }
	// // Divide each element of caseIntervals and controlIntervals by the
	// // appropriate total sum.
	// for (int i = 0; i < inCellCount; ++i) {
	// outInstanceIntervals[i] /= sumCaseFractions;
	// }
	// }

	// public static void generateInstanceCountsByMinorAlleleFrequencies(final
	// Random random, final int inTotalInstanceCount,
	// final double[][] inAlleleFrequencyIntervals, final int[]
	// outCellInstanceCounts) {
	// double rand;
	// final int attributeCount = inAlleleFrequencyIntervals[0].length;
	// final CellId cellId = new CellId(attributeCount);
	//
	// Arrays.fill(outCellInstanceCounts, 0);
	// for (int i = 0; i < inTotalInstanceCount; ++i) {
	// for (int j = 0; j < attributeCount; ++j) {
	// rand = random.nextDouble();
	// if (rand < inAlleleFrequencyIntervals[0][j]) {
	// cellId.indices[j] = 0;
	// } else if (rand < inAlleleFrequencyIntervals[1][j]) {
	// cellId.indices[j] = 1;
	// } else {
	// cellId.indices[j] = 2;
	// }
	// }
	// ++outCellInstanceCounts[cellId.toMasterIndex(3, attributeCount)];
	// }
	// }

	// Fill in outCellInstanceCounts with randomly-allocated instances according
	// to inInstanceIntervals
	// public static void generateInstanceCountsBySamplingIntervals(final Random
	// inRandom, final int inTotalInstanceCount,
	// final double[] inInstanceIntervals, final int[] inCellInstanceLimits,
	// final int[] outCellInstanceCounts) {
	// final int cellCount = inInstanceIntervals.length;
	// Arrays.fill(outCellInstanceCounts, 0);
	// for (int i = 0; i < inTotalInstanceCount; ++i) {
	// final double rand = inRandom.nextDouble();
	// for (int k = 0; k < cellCount; ++k) {
	// if (rand < inInstanceIntervals[k]) {
	// if ((inCellInstanceLimits != null) && (outCellInstanceCounts[k] >=
	// inCellInstanceLimits[k])) {
	// --i;
	// } else {
	// ++outCellInstanceCounts[k];
	// }
	// break;
	// }
	// }
	// }
	// }

	// Generate allele frequencies
	// The index-order is a little counter-intuitive, but this avoids having
	// lots of extra references:
	// double[][] alleleFrequencyIntervals = new double[snpStateCount ==
	// 3][attributeCount];
	// public static void generateMinorAlleleFrequencyIntervals(final Random
	// random, final int inAttributeCount,
	// final double inMinorAlleleFreqMin, final double inMinorAlleleFreqMax,
	// final double[][] outAlleleFrequencyIntervals) {
	// for (int i = 0; i < inAttributeCount; ++i) {
	// final double maf = (random.nextDouble() * (inMinorAlleleFreqMax -
	// inMinorAlleleFreqMin)) + inMinorAlleleFreqMin;
	// outAlleleFrequencyIntervals[0][i] = (1.0 - maf) * (1.0 - maf);
	// outAlleleFrequencyIntervals[1][i] = outAlleleFrequencyIntervals[0][i] +
	// (2.0 * maf * (1.0 - maf));
	// outAlleleFrequencyIntervals[2][i] = outAlleleFrequencyIntervals[1][i] +
	// (maf * maf);
	// }
	// }

	// The basis of a penetrance table is the set of independent parameters that
	// are used to generate the table.
	public static class BasisCell implements Cloneable {
		public boolean isSet;
		public double value;
		public int whichPenetranceCell; // master-index

		public BasisCell() {
			isSet = false;
		}

		public BasisCell(final BasisCell inCell) {
			isSet = inCell.isSet;
			value = inCell.value;
			whichPenetranceCell = inCell.whichPenetranceCell;
		}

		public BasisCell(final double inValue) {
			isSet = true;
			value = inValue;
			whichPenetranceCell = PenetranceTable.kWhichPenetranceCellNone;
		}

		public BasisCell(final double inValue, final int inWhichPenetranceCell) {
			isSet = true;
			value = inValue;
			whichPenetranceCell = inWhichPenetranceCell;
		}

		@Override
		public Object clone() throws CloneNotSupportedException {
			return super.clone();
		}
	}

	public static class CellId implements Cloneable {
		public int[] indices;

		// Construct a copy of the given CellId
		public CellId(final CellId inCellId) {
			this(inCellId.indices);
		}

		// Construct a CellId with unspecified indices
		public CellId(final int inLength) {
			indices = new int[inLength];
		}

		// Construct a CellId with the specified index repeated
		public CellId(final int inLength, final int inIndex) {
			indices = new int[inLength];
			for (int i = 0; i < inLength; ++i) {
				indices[i] = inIndex;
			}
		}

		// Construct a CellId with the specified indices
		public CellId(final int[] inIndices) {
			indices = new int[inIndices.length];
			System.arraycopy(inIndices, 0, indices, 0, inIndices.length);
		}

		public void clear() {
			Arrays.fill(indices, 0);
		}

		@Override
		public Object clone() throws CloneNotSupportedException {
			final CellId outClone = new CellId(this);
			return outClone;
		}

		public void copyFrom(final CellId inCellId) {
			assert (indices.length == inCellId.indices.length);
			for (int i = 0; i < inCellId.indices.length; ++i) {
				setIndex(i, inCellId.getIndex(i));
			}
		}

		// Incrementing the master-index amounts to incrementing the cellId in
		// the following order (for, say, a 3-dimensional cellId):
		// 000, 100, 200, 010, 110, 210, 020, 120, 220, 001, 101, 201, 011, ...
		// This would seem to be backwards, but it means that the
		// least-significant entry of the incrementing corresponds to
		// minor-allele-frequency[0], the next to MAF[1], etc.
		// This is needed in order to get both of the following properties:
		// 1. Incrementing the master-index goes first across rows, then down
		// columns, then from one square to the next.
		// 2. MAF[0] goes across rows, MAF[1] goes down columns, and MAF[2] goes
		// across squares.
		public void fromMasterIndex(final int inSnpStateCount, final int inAttributeCount, final int inMasterIndex) {
			int index;
			assert ((0 <= inMasterIndex) && (inMasterIndex < (int) Math.round(Math.pow(inSnpStateCount, inAttributeCount))));
			index = inMasterIndex;
			for (int i = 0; i < inAttributeCount; ++i)
				// for(int i = inAttributeCount - 1; i >= 0; --i)
			{
				setIndex(i, index % inSnpStateCount);
				index /= inSnpStateCount;
			}
		}

		public int getIndex(final int inDimension) {
			return indices[inDimension];
		}

		public int getLength() {
			return indices.length;
		}

		public boolean matchesOnAnyDimension(final CellId inCellId) {
			boolean outMatches = false;
			for (int i = 0; i < inCellId.indices.length; ++i) {
				if (indices[i] == inCellId.indices[i]) {
					outMatches = true;
					break;
				}
			}
			return outMatches;
		}

		public void print() {
			for (final int indice : indices) {
				System.out.print(indice + ", ");
			}
		}

		public void setIndex(final int inDimension, final int inIndex) {
			indices[inDimension] = inIndex;
		}

		public int toMasterIndex(final int inSnpStateCount) {
			int index;
			final int attributeCount = getLength();
			index = 0;
			for (int i = attributeCount - 1; i >= 0; --i) {
				// for(int i = 0; i < attributeCount; ++i)
				index = (index * inSnpStateCount) + getIndex(i);
			}
			return index;
		}

		public int toMasterIndex(final int inSnpStateCount, final int inAttributeCount) {
			if (getLength() != inAttributeCount) {
			}
			assert (getLength() == inAttributeCount);
			return toMasterIndex(inSnpStateCount);
		}
	}

	public enum ErrorState {
		None, Ambiguous, Conflict
	}

	public static class PenetranceCell implements Cloneable {
		public boolean isSet;
		private double value;
		public boolean isBasisElement;
		private int whichBasisElement;

		public PenetranceCell() {
			clear();
		}

		public PenetranceCell(final double inValue) {
			setValue(inValue);
			isSet = true;
			isBasisElement = false;
		}

		public PenetranceCell(final double inValue, final int inWhichBasisElement) {
			setValue(inValue);
			isSet = true;
			setWhichBasisElement(inWhichBasisElement);
			isBasisElement = true;
		}

		public PenetranceCell(final PenetranceCell inCell) {
			setValue(inCell.getValue());
			isSet = inCell.isSet;
			setWhichBasisElement(inCell.getWhichBasisElement());
			isBasisElement = inCell.isBasisElement;
		}

		public void clear() {
			isSet = false;
			isBasisElement = false;
		}

		@Override
		public Object clone() throws CloneNotSupportedException {
			return super.clone();
		}

		public double getValue() {
			return value;
		}

		public int getWhichBasisElement() {
			return whichBasisElement;
		}

		public void setValue(final double value) {
			this.value = value;
			isSet = true;
		}

		public void setWhichBasisElement(final int whichBasisElement) {
			this.whichBasisElement = whichBasisElement;
			isBasisElement = true;
		}
	}

	public static class PenetranceCellWithId extends PenetranceCell {
		public CellId cellId;

		public PenetranceCellWithId(final CellId inCellId) {
			super();
			cellId = new CellId(inCellId);
		}

		public PenetranceCellWithId(final CellId inCellId, final double inValue) {
			super(inValue);
			cellId = new CellId(inCellId);
		}

		public PenetranceCellWithId(final CellId inCellId, final double inValue, final Integer inWhichBasisElement) {
			super(inValue, inWhichBasisElement);
			cellId = new CellId(inCellId);
		}
	}

	public static class PenetranceTableComparatorEdm implements Comparator<PenetranceTable> {
		@Override
		public int compare(final PenetranceTable in1, final PenetranceTable in2) {
			if (!(in1 instanceof PenetranceTable) || !(in2 instanceof PenetranceTable)) {
				throw new ClassCastException();
			} else {
				if (in1.edm < in2.edm) {
					return -1;
				} else if (in1.edm > in2.edm) {
					return 1;
				} else {
					return 0;
				}
			}
		}
	}

	public static class PenetranceTableComparatorOddsRatio implements Comparator<PenetranceTable> {
		@Override
		public int compare(final PenetranceTable in1, final PenetranceTable in2) {
			if (!(in1 instanceof PenetranceTable) || !(in2 instanceof PenetranceTable)) {
				throw new ClassCastException();
			} else {
				if (in1.oddsRatio < in2.oddsRatio) {
					return -1;
				} else if (in1.oddsRatio > in2.oddsRatio) {
					return 1;
				} else {
					return 0;
				}
			}
		}
	}
} // end class PenetranceTable
