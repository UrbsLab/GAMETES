package org.epistasis.snpgen.document;

import java.io.File;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Vector;

import org.epistasis.snpgen.document.CmdLineParserSrc.Option;
import org.epistasis.snpgen.exception.InputException;
import org.epistasis.snpgen.simulator.PenetranceTable;

public class SnpGenDocument {
	public static final Double kDefaultFrequencyMin = 0.01;
	public static final Double kDefaultFrequencyMax = 0.5;
	public static final Double kDefaultMissingValueRate = 0.0;
	public static final Integer kDefaultAtrributeCount = 100;
	public static final Integer kDefaultCaseCount = 400;
	public static final Integer kDefaultControlCount = 400;
	public static final Integer kDefaultReplicateCount = 100;
	public static final Integer kDefaultRasQuantileCount = 3;
	public static final Integer kDefaultRasPopulationCount = 1000;
	public static final Integer kDefaultRasTryCount = 50000;
	public static final Double kDefaultModelFraction = 1.0;
	public static final Double kDefaultDatasetCaseProportion = 0.5;
	public static final Boolean kDefaultCreateContinuousEndpoints = Boolean.FALSE;
	public static final Double kDefaultContinuousEndpointsStandardDeviation = 0.2;
	public static final Integer kDefaultTotalCount = SnpGenDocument.kDefaultCaseCount + SnpGenDocument.kDefaultCaseCount;
	public static final DecimalFormat kDecimalFormatCommaInteger = new DecimalFormat("#,###.####");
	public static final DecimalFormat kDecimalFormatFourDecimals = new DecimalFormat("#.####");
	public static final DecimalFormat kDecimalFormatTenDecimals = new DecimalFormat("#.##########");
	private static final String kDefaultAttributeNameBase = "P";
	//private static final MIXED_MODEL_DATASET_TYPE kDefaultMultipleModelDatasetType = MIXED_MODEL_DATASET_TYPE.heterogeneous;
	private static final MIXED_MODEL_DATASET_TYPE kDefaultMultipleModelDatasetType = MIXED_MODEL_DATASET_TYPE.hierarchical;


	private int nextModelNumber;
	private final ArrayList<DocListener> listeners;
	


	public double[] modelFractions = null;

	public DocBoolean generateReplicates;
	public DocBoolean includeMissingValues;
	public DocBoolean caseControlRatioBalanced;
	public DocInteger replicateCount;
	public DocInteger rasQuantileCount;
	public DocInteger rasPopulationCount;
	public DocInteger rasTryCount;
	public DocDataset firstDataset;
	public ArrayList<DocModel> modelList;
	public ArrayList<DocDataset> datasetList;

	public File[] modelInputFiles;
	public File predictiveInputFile;
	public File noiseInputFile;
	public File testingOutputFile;

	public boolean showHelp;
	public boolean runDocument;
	
	public Integer randomSeed;
	public String predictiveInputFilename;
	

	public SnpGenDocument(final boolean inCreateFirstDataset) {
		setNextModelNumber(1);
		randomSeed = null;
		modelInputFiles = new File[0];
		includeMissingValues = new DocBoolean();
		caseControlRatioBalanced = new DocBoolean();
		generateReplicates = new DocBoolean();
		rasQuantileCount = new DocInteger();
		rasPopulationCount = new DocInteger();
		rasPopulationCount.setValue(SnpGenDocument.kDefaultRasPopulationCount);
		rasTryCount = new DocInteger();
		rasTryCount.setValue(50000); // Default

		listeners = new ArrayList<DocListener>();
		modelList = new ArrayList<DocModel>();
		datasetList = new ArrayList<DocDataset>();
		firstDataset = null;
		if (inCreateFirstDataset) {
			createFirstDataset();
		}

		createDocument();
	}

	public void addDocDataset(final DocDataset inDataset) {
		datasetList.add(inDataset);
		final Integer whichDataset = findDataset(inDataset);
		assert whichDataset != null;
		for (final DocListener l : listeners) {
			l.datasetAdded(this, inDataset, whichDataset);
		}
	}

	public void addDocModel(final DocModel inModel) {
		modelList.add(inModel);
		final Integer whichModel = findModel(inModel);
		assert whichModel != null;
		for (final DocListener l : listeners) {
			l.modelAdded(this, inModel, whichModel);
		}
	}

	public void addDocumentListener(final DocListener inListener) {
		listeners.add(inListener);
	}

	public DocDataset addNewDocDataset() {
		final DocDataset outDataset = new DocDataset(this);
		addDocDataset(outDataset);
		return outDataset;
	}

	public DocModel addNewDocModel(final int inAttributeCount, final String inModelId, final String[] inAttributeNames,
			final double[] inAttributeAlleleFrequencies) {
		final DocModel outModel = new DocModel(this, inAttributeCount);
		outModel.modelId.setValue(inModelId);
		for (int i = 0; i < inAttributeCount; ++i) {
			if (inAttributeNames == null) {
				outModel.attributeNameArray[i].setValue(SnpGenDocument.getDefaultAttributeNameBase() + i);
			} else {
				outModel.attributeNameArray[i].setValue(inAttributeNames[i]);
			}
			if (inAttributeAlleleFrequencies != null) {
				outModel.attributeAlleleFrequencyArray[i].setValue(inAttributeAlleleFrequencies[i]);
			}
		}
		addDocModel(outModel);
		return outModel;
	}

	public DocDataset createFirstDataset() {
		firstDataset = addNewDocDataset();
		return firstDataset;
	}

	public int findDataset(final DocDataset inDataset) {
		Integer whichDataset = null;
		for (int i = 0; i < datasetList.size(); ++i) {
			if (datasetList.get(i) == inDataset) {
				whichDataset = i;
				break;
			}
		}
		return whichDataset;
	}

	public Integer findModel(final DocModel inModel) {
		Integer whichModel = null;
		for (int i = 0; i < modelList.size(); ++i) {
			if (modelList.get(i) == inModel) {
				whichModel = i;
				break;
			}
		}
		return whichModel;
	}

	public DocModel getModel(final int inWhich) {
		return modelList.get(inWhich);
	}

	public int getModelCount() {
		return modelList.size();
	}

	public int getNextModelNumber() {
		return nextModelNumber;
	}

	public int getNextPredictiveAttributeNumber() {
		int outAttributeNumber = 1; // First guess
		for (final DocModel model : modelList) {
			for (final DocString attr : model.attributeNameArray) {
				if ((attr != null) && (attr.getValue() != null)) {
					final String attrName = attr.getValue().toLowerCase();
					if (attrName.charAt(0) == 'p') {
						int attrNumber = 0;
						boolean isNumber = true; // First guess
						try {
							attrNumber = Integer.parseInt(attrName.substring(1));
						} catch (final NumberFormatException nfe) {
							isNumber = false;
						}
						if (isNumber) {
							if (outAttributeNumber < (attrNumber + 1)) {
								outAttributeNumber = attrNumber + 1;
							}
						}
					}
				}
			}
		}
		return outAttributeNumber;
	}

	// Load the specified file into the document
	public void load(final File inFile) {
		assert false : "SnpGenDocument.load() not implemented";
	}

	public boolean parseArguments(final String[] args) throws Exception {
		boolean outShowGui;
		final Boolean showHelpObject;

		 /* THE FOLLOWING COMMENTED-OUT CODE IS FOR TESTING ONLY:

		   String argString =
		   "-M%-n 'Model1' -h 0.3 -a 0.25 -a 0.25%-o%/Users/jfisher/_SnpGen/tables.txt%-q%1%-p%1000%-t%100000%-D%-a 1000 -s 200 -w 200 -n 0.05 -x .211 -r 10 -o /Users/jfisher/_SnpGen/Surf";
		  
		   args = argString.split("%");

		   String argString =
		   "-M%-n 'Model1' -h 0.4 -a 0.25 -a 0.25%-o%/Users/jfisher/_SnpGen/tables.txt%-q%1%-p%1000%-t%50000%-D%-a 1000 -s 200 -w 200 -m 0.05 -x 0.1 -r 100 -o /Users/jfisher/_SnpGen/Surf";
		   // String argString =
		   "-M%-n 'Model1' -h 0.4 -p 0.6 -a 0.25 -a 0.25%-o%/Users/jfisher/_SnpGen/tables.txt%-q%1%-p%1000%-t%50000%-D%-a 10 -s 200 -w 200 -m 0.05 -x 0.1 -r 10 -o /Users/jfisher/_SnpGen/Foo%-n%2%-x%2";
		   //
		   args = argString.split("%");

		   String argString =
		   "-M%-n 'Model1' -h 0.015 -a 0.25 -a 0.25%-o%/Users/jfisher/_SnpGen/tables.txt%-q%40%-p%1000%-t%50000%-D%-a 10 -s 400 -w 400 -r 1 -o /Users/jfisher/_SnpGen/40_200";
		   String argString = ""
		   +
		   "-s%hyperIndep%-s%mutualInfo%-s%mdr%-v%/Users/jfisher/_SnpGen/best1.txt%-u%/Users/jfisher/_SnpGen/foo%-r%23%"
		   + "-q%1%-n%1%-x%3%-D%-a 70 -s 100 -w 100 -r 10";
		  
		   String argString = ""
		   +
		   "-s%heritability%-s%hyperIndep%-s%mutualInfo%-s%mdr%-u%/Users/jfisher/_SnpGen/foo%-r%23%"
		   + "-q%10%-n%1%-x%3%-D%-a 10 -s 100 -w 100 -r 100";
		  
		   String argString = "-M%-n 'Model1' -h 0.01 -a 0.2 -a 0.2%"
		   +
		   "-s%heritability%-s%hyperIndep%-s%mutualInfo%-s%mdr%-u%/Users/jfisher/_SnpGen/foo%-r%233%"
		   + "-q%10%-n%1%-x%2%-D%-a 20 -s 100 -w 100 -r 100";
		  
		   args = argString.split("%");

		   String argString =
		   "-M%-n 'Model1' -h 0.1 -a 0.25 -a 0.5%-M%-n 'Model2' -h 0.02 -a 0.25 -a 0.5%-o%/Users/jfisher/_SnpGen/tables.txt%-q%50%-p%1000%-t%50000%"
		   +
		   "-D%-a 20 -s 100 -w 100 -r 1 -o /Users/jfisher/_SnpGen/20_200%-D%-a 40 -s 100 -w 100 -r 100 -o /Users/jfisher/_SnpGen/40_200";
		  
		   args = argString.split("%");

		   String argString =
		   "-M%-n 'Model1' -h 0.0001 -a 0.2 -a 0.2 -a 0.2 -a 0.2 -a 0.2%-o%/Users/jfisher/_SnpGen/tables.txt%-q%3%-p%100%-t%10000000%-r%2";
		  
		   args = argString.split("%");

		   String argString = ""
		   +
		   "-s%hyperIndep%-s%heritability%-s%mutualInfo%-s%mdr%-v%/Users/jfisher/Genomics/MDR/Testing/Standard_Models/VelezData_20_attributes_balanced_200_400_800_1600_samples/200/69%-r%37%";
		  
		   args = argString.split("%");

		   String argString = ""
		   +
		   "-s%hyperIndep%-s%heritability%-s%mutualInfo%-s%mdr%-v%/Users/jfisher/Genomics/MDR/Testing/Standard_Models/Foo/200/00%-r%37%";
		  
		   args = argString.split("%");

		   String argString = ""
		   +
		   "-s%hyperIndep%-s%heritability%-s%mutualInfo%-s%mdr%-i%/Users/jfisher/Genomics/MDR/Testing/Standard_Models/README.data.txt%-r%37%";
		  
		   args = argString.split("%");

		*/
		if (args.length == 0) {
			outShowGui = true;
		} else {
			outShowGui = false;

			final CmdLineParserSrc datasetParserTemplate = new CmdLineParserSrc();
			final Option<Double> alleleFrequencyMinOption = datasetParserTemplate.addDoubleOption('n', "alleleFrequencyMin",
					"Minimum minor allele frequency for randomly generated, non-predictive attributes in datasets.");
			final Option<Double> alleleFrequencyMaxOption = datasetParserTemplate.addDoubleOption('x', "alleleFrequencyMax",
					"Maximum minor allele frequency for randomly generated, non-predictive attributes in datasets.");
			final Option<Integer> totalAttributeCountOption = datasetParserTemplate.addIntegerOption('a', "totalAttributeCount",
					"Total number of attributes to be generated in simulated dataset(s).");
			final Option<Double> missingValueRateOption = datasetParserTemplate.addDoubleOption('m', "missingValueRate", "???");
			final Option<Integer> totalCountOption = datasetParserTemplate.addIntegerOption('t', "totalCount",
					"(continuous data only) How many samples to generate for each dataset.");
			final Option<Integer> caseCountOption = datasetParserTemplate.addIntegerOption('s', "caseCount",
					"(discrete data only) Number of case instances in simulated dataset(s).  Cases have class = '1'.");
			final Option<Integer> controlCountOption = datasetParserTemplate
					.addIntegerOption('w', "controlCount",
							"(discrete data only) Number of control instances in simulated dataset(s).  Controls have class = '0'.");
			final Option<Integer> replicateCountOption = datasetParserTemplate.addIntegerOption('r', "replicateCount",
					"Total number of replicate datasets generated from given model(s) to be randomly generated.");
			final Option<String> datasetOutputFileOption = datasetParserTemplate
					.addStringOption('o', "datasetOutputFile",
							"Output file name/path.  This parameter is used for -D to specify how dataset files are saved, and how they will be named.");

			final Option<Boolean> createContinuousEndpointsOption = datasetParserTemplate.addBooleanOption('c', "continuous",
					"Directs algorithm to generate datasets with continuous-valued endpoints rather than binary discrete datasets.");

			final Option.EnumParserOption<MIXED_MODEL_DATASET_TYPE> heterogeneousOptionLocal = new Option.EnumParserOption<MIXED_MODEL_DATASET_TYPE>(
					'h', "mixedModelDatasetType", "if there are multiple models use " + MIXED_MODEL_DATASET_TYPE.heterogeneous + " or "
							+ MIXED_MODEL_DATASET_TYPE.hierarchical, MIXED_MODEL_DATASET_TYPE.class);

			final Option<MIXED_MODEL_DATASET_TYPE> multipleModelDatasetType = datasetParserTemplate.addOption(heterogeneousOptionLocal);

			final Option<Double> continuousEndpointsStandardDeviationOption = datasetParserTemplate
					.addDoubleOption(
							'd',
							"standardDeviation",
							"The standard deviation around model penetrance values used to simulated continuous-valued endpoints.  Larger standard deviation values should yield noisier datasets, with a signal that is more difficult to detect.");
			final String minMaxDescription = "Minimum and maximum determine the range that model penetrance values are mapped to. Because of statistical sampling, based on the magnitude of the standard deviation, some points will be outside this range.";

			final CmdLineParserSrc modelParserTemplate = new CmdLineParserSrc();
			final Option<Double> modelHeritabilityOption = modelParserTemplate.addDoubleOption('h', "heritability",
					"Specifies the heritability of a given model.");
			final Option<Double> modelPrevalenceOption = modelParserTemplate.addDoubleOption('p', "caseProportion",
					"Specifies the caseProportion of a given model.");
			final Option<Boolean> modelOddsRatioOption = modelParserTemplate
					.addBooleanOption(
							'd',
							"useOddsRatio",
							"Normally, the EDM difficulty model difficulty estimate is used to rank models for selection.  This parameter over-rides the default in favor of the COR difficulty estimate.");
			final Option<Double> modelAttributeOption = modelParserTemplate
					.addDoubleOption(
							'a',
							"attributeAlleleFrequency",
							"Specifies the minor allele frequency of a given attribute in a model.  The number of times this parameter is specified determines the number of attributes that are included in the model.  E.g. 2-locus, 3-locus, 4-locus models.");
			final Option<String> modelOutputFileOption = modelParserTemplate
					.addStringOption('o', "modelOutputFile",
							" Output file name/path.  This parameter is used for -M to specify how model files are saved, and how they will be named..");

			final CmdLineParserSrc parser = new CmdLineParserSrc();
			final Option<CmdLineParserSrc> modelOption = parser.addOption(new Option.CmdLineParserOption('M', "model",
					"Command to generate model(s) with specified model constraints", modelParserTemplate));
			final Option<CmdLineParserSrc> datasetOption = parser.addOption(new Option.CmdLineParserOption('D', "dataset",
					"Command to generate dataset(s) with specified dataset constraints.", datasetParserTemplate));

			final Option<String> modelInputFileOption = parser.addStringOption('i', "modelInputFile",
					"Path/Name of input model file used for generating dataset(s).");
			final Option<Double> modelWeightOption = parser
					.addDoubleOption(
							'w',
							"modelWeight",
							"For each model, the relative weight compared to other models. If not specified all models will have equal weight. The weight/totalWeight determines the model fraction. The fraction is used in heterogeneous datasets to determine the # of rows generated from each model. In continuous hierarchical models, the fraction controls the relative contribution to the continuous endpoint. For discrete hierarchical models, weight is ignored. The order of the fractions is 1) new models and then 2) input models. ");
			final Option<String> predictiveInputFileOption = parser.addStringOption('v', "predictiveInputFile", "???");
			final Option<String> noiseInputFileOption = parser.addStringOption('z', "noiseInputFile",
					"a file containing snps data. The number of rows will determine your dataset output size.");
			final Option<Integer> rasQuantileCountOption = parser
					.addIntegerOption('q', "rasQuantileCount",
							"Number of quantiles (i.e. number of models selected across a difficulty-ranked distribution) that will be saved to the model file.");
			final Option<Integer> rasPopulationCountOption = parser.addIntegerOption('p', "rasPopulationCount",
					"Number of models to be generated, from which representative 'quantile'-models will be selected and saved.");
			final Option<Integer> rasTryCountOption = parser.addIntegerOption('t', "rasTryCount",
					"Number of algorithmic attempts to generate a random model to satisfy parameter -p, before algorithm quits.");
			final Option<Integer> randomSeedOption = parser
					.addIntegerOption(
							'r',
							"randomSeed",
							"Seed for random number generator used to simulate models and datasets. If specified, repeated runs will generate identical outputs. In this way, a colleague could recreate your datasets without needing to transfer the actual files.");
			final Option<Boolean> helpOption = parser.addBooleanOption('h', "help", "What you are reading now");

			parser.parse(args);

			showHelpObject = parser.getOptionValue(helpOption);
			showHelp = ((showHelpObject != null) && showHelpObject);

			final Vector<String> modelInputFileOptionList = parser.getOptionValues(modelInputFileOption);
			final int modelInputFileCount = modelInputFileOptionList.size();
			modelInputFiles = new File[modelInputFileCount];
			for (int i = 0; i < modelInputFileCount; ++i) {
				modelInputFiles[i] = new File(modelInputFileOptionList.get(i));
			}

			final Vector<Double> modelWeights = parser.getOptionValues(modelWeightOption);
			if (!modelWeights.isEmpty()) {
				double totalWeights = 0.0d;
				for (final Double modelWeight : modelWeights) {
					totalWeights += modelWeight;
				}
				modelFractions = new double[modelWeights.size()];
				int modelIndex = 0;
				for (final Double modelWeight : modelWeights) {
					modelFractions[modelIndex++] = modelWeight / totalWeights;
				}
			}

			predictiveInputFile = null;
			predictiveInputFilename = null;
			String fileName = parser.getOptionValue(predictiveInputFileOption);
			if (fileName != null) {
				predictiveInputFilename = fileName;
				predictiveInputFile = new File(fileName);
			}

			fileName = parser.getOptionValue(noiseInputFileOption);
			if (fileName == null) {
				noiseInputFile = null;
			} else {
				noiseInputFile = new File(fileName);
			}

			rasQuantileCount.setValue(parser.getOptionValue(rasQuantileCountOption), SnpGenDocument.kDefaultRasQuantileCount);
			rasPopulationCount.setValue(parser.getOptionValue(rasPopulationCountOption), SnpGenDocument.kDefaultRasPopulationCount);
			rasTryCount.setValue(parser.getOptionValue(rasTryCountOption), SnpGenDocument.kDefaultRasTryCount);
			randomSeed = parser.getOptionValue(randomSeedOption);

			final Vector<CmdLineParserSrc> datasetOptionList = parser.getOptionValues(datasetOption);
			for (final CmdLineParserSrc datasetParser : datasetOptionList) {
				if (datasetParser.getRemainingArgs().length > 0) {
					throw new IllegalArgumentException("Unexpected argument passed into the --dataset: "
							+ Arrays.toString(datasetParser.getRemainingArgs()));
				}
				final DocDataset dataset = addNewDocDataset();
				dataset.alleleFrequencyMin.setValue(datasetParser.getOptionValue(alleleFrequencyMinOption),
						SnpGenDocument.kDefaultFrequencyMin);
				dataset.alleleFrequencyMax.setValue(datasetParser.getOptionValue(alleleFrequencyMaxOption),
						SnpGenDocument.kDefaultFrequencyMax);
				dataset.totalAttributeCount.setValue(datasetParser.getOptionValue(totalAttributeCountOption),
						SnpGenDocument.kDefaultAtrributeCount);
				dataset.missingValueRate.setValue(datasetParser.getOptionValue(missingValueRateOption),
						SnpGenDocument.kDefaultMissingValueRate);
				dataset.replicateCount.setValue(datasetParser.getOptionValue(replicateCountOption), SnpGenDocument.kDefaultReplicateCount);
				dataset.createContinuousEndpoints.setValue(datasetParser.getOptionValue(createContinuousEndpointsOption),
						SnpGenDocument.kDefaultCreateContinuousEndpoints);

				dataset.multipleModelDatasetType.setValue(datasetParser.getOptionValue(multipleModelDatasetType),
						SnpGenDocument.kDefaultMultipleModelDatasetType);

				final boolean continuousEndpoints = dataset.createContinuousEndpoints.getBoolean().booleanValue();
				final Integer totalCount = datasetParser.getOptionValue(totalCountOption);
				final Integer caseCount = datasetParser.getOptionValue(caseCountOption);
				final Integer controlCount = datasetParser.getOptionValue(controlCountOption);

				if (continuousEndpoints) {
					dataset.continuousEndpointsStandardDeviation.setValue(
							datasetParser.getOptionValue(continuousEndpointsStandardDeviationOption),
							SnpGenDocument.kDefaultContinuousEndpointsStandardDeviation);

					dataset.totalCount.setValue(totalCount, SnpGenDocument.kDefaultTotalCount);
					if ((caseCount != null) || (controlCount != null)) {
						throw new IllegalArgumentException(
								"For continuous datasets these should not be specified: --caseCount --controlCount --caseProportion");
					}
				} else {
					if (caseCount != null) {
						if (controlCount == null) {
							throw new IllegalArgumentException("Case count passed in but not control count");
						}
						dataset.totalCount.setValue(caseCount + controlCount);
						dataset.caseProportion.setValue(caseCount / (double) dataset.totalCount.getInteger());
					} else if (controlCount != null) {
						throw new IllegalArgumentException("control count passed in but not case count");
					} else {
						dataset.totalCount.setValue(totalCount, SnpGenDocument.kDefaultTotalCount);
						dataset.caseProportion.setValue(SnpGenDocument.kDefaultDatasetCaseProportion);
					}
					dataset.continuousEndpointsStandardDeviation.setValue(datasetParser
							.getOptionValue(continuousEndpointsStandardDeviationOption));
				}
				fileName = datasetParser.getOptionValue(datasetOutputFileOption);
				if (fileName == null) {
					dataset.outputFile = null;
				} else {
					dataset.outputFile = new File(fileName);
				}
			}
			if (datasetOptionList.size() >= 1) {
				firstDataset = datasetList.get(0);
			}

			final Vector<CmdLineParserSrc> modelOptionList = parser.getOptionValues(modelOption);
			for (final CmdLineParserSrc modelParser : modelOptionList) {
				if (modelParser.getRemainingArgs().length > 0) {
					throw new IllegalArgumentException("Unexpected argument passed into the --model: "
							+ Arrays.toString(modelParser.getRemainingArgs()));
				}
				final Vector<Double> attributeAlleleFrequencyList = modelParser.getOptionValues(modelAttributeOption);

				final String modelFilePrefix = modelParser.getOptionValue(modelOutputFileOption);
				String modelName = "";
				File modelFile = null;
				if (modelFilePrefix != null) {
					// If modelFilePrefix has leading directories strip them off to get model name
					modelFile = new File(modelFilePrefix);
					modelName = modelFile.getName();
				}

				final DocModel model = addNewDocModel(attributeAlleleFrequencyList.size(), modelName, null, null);
				model.file = modelFile;
				for (int i = 0; i < attributeAlleleFrequencyList.size(); ++i) {
					model.attributeAlleleFrequencyArray[i].setValue(attributeAlleleFrequencyList.elementAt(i).doubleValue());
				}
				final Double heritability = modelParser.getOptionValue(modelHeritabilityOption);
				if (heritability == null) {
					throw new MissingOptionException(modelHeritabilityOption.toString());
				} else {
					model.heritability.setValue(heritability.doubleValue());
				}
				final Double prevalence = modelParser.getOptionValue(modelPrevalenceOption);
				if (prevalence == null) {
					model.prevalence.setValue((Double) null);
				} else {
					model.prevalence.setValue(prevalence.doubleValue());
				}
				final Boolean useOddsRatio = modelParser.getOptionValue(modelOddsRatioOption);
				if (useOddsRatio == null) {
					model.useOddsRatio.setValue((Boolean) null);
				} else {
					model.useOddsRatio.setValue(useOddsRatio.booleanValue());
				}
			}

			// Total number of models is modelOptionList size + modelInputFiles size
			final int totalModels = modelOptionList.size() + modelInputFileCount;
			// Each dataset must have modelFractions set for each model
			// Models are ordered as follows: all new models followed by model input files
			if (modelFractions == null) {
				modelFractions = new double[totalModels];
				for (int modelCtr = 0; modelCtr < totalModels; ++modelCtr) {
					modelFractions[modelCtr] = 1.0d / totalModels;
				}
			} else if (modelFractions.length != totalModels) {
				throw new IllegalArgumentException("# of models and # of dataset model fractions do not match! There are " + totalModels
						+ " total models (" + modelOptionList.size() + " from new models and " + modelInputFileCount
						+ " from model files) but --" + modelWeightOption.longForm + " has " + modelFractions.length
						+ " model fractions: "
						+ Arrays.toString(modelFractions));
			}

			if (showHelp) {
				SnpGenDocument.printOptionHelp(parser);
			}
		}

		runDocument = (!showHelp && !outShowGui);
		return outShowGui;
	}

	public void removeDocModel(final int inWhichModel) {
		final DocModel model = modelList.get(inWhichModel);
		modelList.remove(inWhichModel);
		for (final DocListener l : listeners) {
			l.modelRemoved(this, model, inWhichModel);
		}
	}

	public void setNextModelNumber(final int nextModelNumber) {
		this.nextModelNumber = nextModelNumber;
	}

	// Store the document into the specified file
	public void store(final File inFile) {
		assert false : "SnpGenDocument.store() not implemented";
	}

	public void updateDocModel(final int inWhichModel, final DocModel inModel) {
		modelList.set(inWhichModel, inModel);
		for (final DocListener l : listeners) {
			l.modelUpdated(this, inModel, inWhichModel);
		}
	}

	// Example arguments:
	// -r 17 -M "-n 'Model1' -h 0.1 -a 0.25 -a 0.5 -f 0.99" -M
	// "-n 'Model2' -h 0.1 -a 0.3 -a 0.3 -f 0.01" -o new.txt -q 3 -p 1000 -t
	// 50000 -D "-a 10 -s 10000 -w 10000 -r 1 -o combined.txt"
	// -r 17 -i new_models.txt -f .3 -f .7 -D
	// "-a 10 -s 10000 -w 10000 -r 1 -o combined_new.txt"
	// -M "-n 'Model1' -h 0.01 -a 0.25 -a 0.5" -o new.txt -q 3 -p 1000 -t 50000
	// -D "-a 4 -s 100 -w 100 -r 1 -o combined.txt" -v pred_data.txt
	// -M "-n 'Model1' -h 0.01 -a 0.25 -a 0.5" -M
	// "-n 'Model2' -h 0.02 -a 0.25 -a 0.5" -o /Users/jfisher/_SnpGen/tables.txt
	// -q 3 -p 1000 -t 50000
	// -i /Users/jfisher/_SnpGen/tables.txt -D
	// "-a 20 -s 100 -w 100 -r 100 -o /Users/jfisher/_SnpGen/20_200" -D
	// "-a 40 -s 100 -w 100 -r 100 -o /Users/jfisher/_SnpGen/40_200"
	// -M "-n 'Model1' -h 0.01 -a 0.25 -a 0.5" -M
	// "-n 'Model2' -h 0.02 -a 0.25 -a 0.5" -o /Users/jfisher/_SnpGen/tables.txt
	// -q 3 -p 1000 -t 50000 -D
	// "-a 20 -s 100 -w 100 -r 100 -o /Users/jfisher/_SnpGen/20_200" -D
	// "-a 40 -s 100 -w 100 -r 100 -o /Users/jfisher/_SnpGen/40_200"
	// -M "-n 'Model1' -h 0.05 -a 0.25 -a 0.5 -a 0.33 -a 0.1" -o
	// output/tables_2_05.txt -q 3 -p 100 -t 50000 -D
	// "-a 20 -s 200 -w 200 -r 50 -o output/4_05_20_400" -D
	// "-a 40 -s 200 -w 200 -r 50 -o output/4_05_40_400" -D
	// "-a 60 -s 200 -w 200 -r 50 -o output/4_05_60_400"
	// -M "-n 'Model1' -h 0.05 -a 0.25 -a 0.5 -a 0.33" -o output/tables_2_05.txt
	// -q 50 -p 100 -t 50000 -D
	// "-n 0.01 -x 0.5 -a 20 -s 200 -w 200 -r 1 -o output/3_05_20_400" -D
	// "-a 30 -s 200 -w 200 -r 1 -o output/3_05_30_400" -D
	// "-a 40 -s 200 -w 200 -r 1 -o output/3_05_40_400"
	// -M "-n 'Model1' -h 0.05 -a 0.25 -a 0.5 -a 0.33" -i old_tables.txt -o
	// output/tables_2_05.txt -q 3 -p 100 -t 50000 -D
	// "-a 20 -s 200 -w 200 -r 50 -o output/3_05_20_400" -D
	// "-a 40 -s 200 -w 200 -r 50 -o output/3_05_40_400" -D
	// "-a 60 -s 200 -w 200 -r 50 -o output/3_05_60_400"

	public Exception verifyAllNeededParameters() {
		Exception outEx = null;

		outEx = verifyModelParameters();
		if (outEx == null) {
			outEx = verifyDatasetParameters();
		}
		return outEx;
	}

	public Exception verifyAllNeededParameters_OLD() {
		Exception outEx = null;

		TESTS: {
			for (int i = 0; i < datasetList.size(); ++i) {
				if ((outEx = datasetList.get(i).verifyAllNeededParameters()) != null) {
					break TESTS;
				}
			}
			for (int i = 0; i < modelList.size(); ++i) {
				if ((outEx = modelList.get(i).verifyAllNeededParameters()) != null) {
					break TESTS;
				}
			}
		}
		return outEx;
	}

	public Exception verifyDatasetParameters() {
		Exception outEx = null;

		for (int i = 0; i < datasetList.size(); ++i) {
			if ((outEx = datasetList.get(i).verifyAllNeededParameters()) != null) {
				break;
			}
		}
		return outEx;
	}

	public Exception verifyModelParameters() {
		Exception outEx = null;

		for (int i = 0; i < modelList.size(); ++i) {
			if ((outEx = modelList.get(i).verifyAllNeededParameters()) != null) {
				break;
			}
		}
		return outEx;
	}

	private void clear() {
	}

	private void createDocument() {
		// For now we just clear the current document; in the future create new document here
		clear();
	}

	public static String getDefaultAttributeNameBase() {
		return SnpGenDocument.kDefaultAttributeNameBase;
	}

	private static void printOptionHelp(final CmdLineParserSrc parser) {
		System.err
		.println("Usage: java -jar gametes.jar <program arguments>\nIf there are no arguments, the graphical user interface will be opened otherwise running in batch mode. Complete list of program arguments:\n\n"
				+ parser.getUsage("\n"));
	}

	public static class DocBoolean extends DocMember<Boolean> {
		public DocBoolean() {
			super();
		}

		public DocBoolean(final Boolean inValue) {
			super(inValue);
		}

		public DocBoolean(final DocBoolean inValue) {
			super(inValue);
		}

		public Boolean getBoolean() {
			return getValue();
		}

		@Override
		public Boolean objectToType(final Object inValue) {
			return Boolean.valueOf(inValue.toString());
		}

		public void setBoolean(final Boolean inValue) {
			setValue(inValue);
		}

		public void setBoolean(final DocBoolean inValue) {
			setValue(inValue);
		}

	}

	public static class DocDataset {
		public DocDouble alleleFrequencyMin;
		public DocDouble alleleFrequencyMax;
		public DocInteger totalAttributeCount;
		public DocDouble missingValueRate;
		public DocDouble caseProportion;
		public DocInteger replicateCount;
		public File outputFile;
		public DocBoolean createContinuousEndpoints;
		public DocDouble continuousEndpointsStandardDeviation;
		public DocInteger totalCount;
		public DocMIXED_MODEL_DATASET_TYPE multipleModelDatasetType;

		public DocDataset(final SnpGenDocument inDoc) {
			alleleFrequencyMin = new DocDouble(SnpGenDocument.kDefaultFrequencyMin);
			alleleFrequencyMax = new DocDouble(SnpGenDocument.kDefaultFrequencyMax);
			totalAttributeCount = new DocInteger(SnpGenDocument.kDefaultAtrributeCount);
			missingValueRate = new DocDouble(SnpGenDocument.kDefaultMissingValueRate);
			totalCount = new DocInteger(SnpGenDocument.kDefaultTotalCount);
			caseProportion = new DocDouble(SnpGenDocument.kDefaultDatasetCaseProportion);
			replicateCount = new DocInteger(SnpGenDocument.kDefaultReplicateCount);
			createContinuousEndpoints = new DocBoolean(Boolean.FALSE);
			continuousEndpointsStandardDeviation = new DocDouble(SnpGenDocument.kDefaultContinuousEndpointsStandardDeviation);
			multipleModelDatasetType = new DocMIXED_MODEL_DATASET_TYPE(SnpGenDocument.kDefaultMultipleModelDatasetType);
		}

		public int getCaseCount() {
			final int caseCount = (int) Math.round(caseProportion.value * totalCount.value);
			return caseCount;
		}

		public int getControlCount() {
			final int controlCount = totalCount.value - getCaseCount();
			return controlCount;
		}

		public Exception verifyAllNeededParameters() {
			Exception outEx = null;

			TESTS: {
				if (totalCount.value <= 0) {
					outEx = new InputException("Dataset has no samples");
					break TESTS;
				}
				if (totalAttributeCount.value <= 0) {
					outEx = new InputException("Dataset has no attributes");
					break TESTS;
				}
				if (createContinuousEndpoints.value == Boolean.TRUE) {
					if (continuousEndpointsStandardDeviation.getValue() == null) {
						outEx = new InputException("No continuousEndpointsStandardDeviation specified");
						break TESTS;
					}
				} else {
					if (caseProportion.getValue() == null) {
						outEx = new InputException("caseProportion (or case control count) not specified for binary class");
						break TESTS;
					}
				}
			}
			return outEx;
		}
	}

	public static class DocDouble extends DocMember<Double> {
		public DocDouble() {
			super();
		}

		public DocDouble(final DocDouble inValue) {
			super(inValue);
		}

		public DocDouble(final Double inValue) {
			super(inValue);
		}

		public Double getDouble() {
			return getValue();
		}

		@Override
		public Double objectToType(final Object inValue) {
			final Double returnObject = Double.parseDouble(inValue.toString());
			return returnObject;
		}
	}

	public static class DocInteger extends DocMember<Integer> {

		public DocInteger() {
			super();
		}

		public DocInteger(final DocInteger inValue) {
			super(inValue);
		}

		public DocInteger(final Integer inValue) {
			super(inValue);
		}

		public Integer getInteger() {
			return getValue();
		}

		@Override
		public Integer objectToType(final Object inValue) {
			try {
				final Number number = NumberFormat.getIntegerInstance().parse(inValue.toString());
				return Integer.valueOf(number.intValue());
			} catch (final ParseException e) {
				return null;
			}
		}

		public void setInteger(final DocInteger inValue) {
			setValue(inValue);
		}

		public void setInteger(final Integer inValue) {
			setValue(inValue);
		}

	}

	public interface DocListener {
		public void attributeCountChanged(SnpGenDocument inDoc, SnpGenDocument.DocModel inModel, int whichModel, int inNewAttributeCount);

		public void datasetAdded(SnpGenDocument inDoc, SnpGenDocument.DocDataset inModel, int whichModel);

		public void modelAdded(SnpGenDocument inDoc, SnpGenDocument.DocModel inModel, int whichModel);

		public void modelRemoved(SnpGenDocument inDoc, SnpGenDocument.DocModel inModel, int whichModel);

		public void modelUpdated(SnpGenDocument inDoc, SnpGenDocument.DocModel inModel, int whichModel);
	}

	public static abstract class DocMember<T> {
		public T value;

		public DocMember() {
		}

		public DocMember(final DocMember<T> inValue) {
			setValue(inValue);
		}

		public DocMember(final T inValue) {
			setValue(inValue);
		}

		public T getValue() {
			return value;
		}

		public abstract T objectToType(Object inValue);

		public void setValue(final DocMember<T> inValue) {
			this.value = inValue.getValue();
		}

		public void setValue(final T inValue) {
			value = inValue;
		}

		public void setValue(final T inValue, final T defaultValue) {
			setValue(inValue == null ? defaultValue : inValue);
		}

		public void setValueFromString(final String inValue) {
			setValue(objectToType(inValue));
		}

		@Override
		public String toString() {
			return getValue() == null ? "null" : getValue().toString();
		}
	}

	public static class DocMIXED_MODEL_DATASET_TYPE extends DocMember<MIXED_MODEL_DATASET_TYPE> {
		public DocMIXED_MODEL_DATASET_TYPE() {
			super();
		}

		public DocMIXED_MODEL_DATASET_TYPE(final DocMIXED_MODEL_DATASET_TYPE inValue) {
			super(inValue);
		}

		public DocMIXED_MODEL_DATASET_TYPE(final MIXED_MODEL_DATASET_TYPE inValue) {
			super(inValue);
		}

		@Override
		public MIXED_MODEL_DATASET_TYPE objectToType(final Object inValue) {
			throw new UnsupportedOperationException();
		}

	}

	public static class DocModel {
		public DocString modelId;
		public DocInteger attributeCount;
		public DocDouble heritability;
		public DocDouble prevalence;
		public DocDouble fraction;
		public DocBoolean useOddsRatio;
		public File file;
		public DocString[] attributeNameArray;
		public DocDouble[] attributeAlleleFrequencyArray;
		private SnpGenDocument parentDoc;
		private PenetranceTable[] penetranceTables;
		private int quantileCountInModel;

		public DocModel(final DocModel inDocModel) {
			this(inDocModel.attributeCount.getInteger());
			inDocModel.copyTo(this);
		}

		public DocModel(final int inAttributeCount) {
			modelId = new DocString();
			attributeCount = new DocInteger(inAttributeCount);
			heritability = new DocDouble();
			prevalence = new DocDouble();
			fraction = new DocDouble();
			useOddsRatio = new DocBoolean();
			attributeNameArray = new DocString[inAttributeCount];
			attributeAlleleFrequencyArray = new DocDouble[inAttributeCount];
			for (int i = 0; i < inAttributeCount; ++i) {
				attributeNameArray[i] = new DocString();
				attributeAlleleFrequencyArray[i] = new DocDouble();
			}
			penetranceTables = new PenetranceTable[0];
			fraction.setValue(1.0);
		}

		public DocModel(final SnpGenDocument inDoc, final int inAttributeCount) {
			this(inAttributeCount);
			setParentDoc(inDoc);
		}

		public DocModel copyTo(final DocModel ioDocModel) {
			final int attributeCount = this.attributeCount.getInteger();
			ioDocModel.modelId.setString(modelId);
			ioDocModel.attributeCount.setInteger(this.attributeCount);
			ioDocModel.heritability.setValue(heritability);
			ioDocModel.prevalence.setValue(prevalence);
			ioDocModel.fraction.setValue(fraction);
			ioDocModel.useOddsRatio.setBoolean(useOddsRatio);
			ioDocModel.setParentDoc(getParentDoc());
			ioDocModel.file = file;
			for (int i = 0; i < attributeCount; ++i) {
				ioDocModel.attributeNameArray[i] = new DocString(attributeNameArray[i].getString());
			}
			for (int i = 0; i < attributeCount; ++i) {
				ioDocModel.attributeAlleleFrequencyArray[i] = new DocDouble(attributeAlleleFrequencyArray[i].getDouble());
			}
			ioDocModel.penetranceTables = new PenetranceTable[penetranceTables.length];
			for (int i = 0; i < penetranceTables.length; ++i) {
				try {
					ioDocModel.penetranceTables[i] = (PenetranceTable) penetranceTables[i].clone();
				} catch (final CloneNotSupportedException e) {
					e.printStackTrace();
				}
			}
			return ioDocModel;
		}

		public double[] getAlleleFrequencies() {
			final double[] outAlleleFrequencies = new double[attributeAlleleFrequencyArray.length];
			for (int i = 0; i < attributeAlleleFrequencyArray.length; ++i) {
				outAlleleFrequencies[i] = attributeAlleleFrequencyArray[i].getDouble();
			}
			return outAlleleFrequencies;
		}

		public String[] getAttributeNames() {
			final String[] outAttributeNames = new String[attributeNameArray.length];
			for (int i = 0; i < attributeNameArray.length; ++i) {
				outAttributeNames[i] = attributeNameArray[i].getString();
			}
			return outAttributeNames;
		}

		public SnpGenDocument getParentDoc() {
			return parentDoc;
		}

		public PenetranceTable[] getPenetranceTables() {
			return penetranceTables;
		}

		public int getQuantileCountInModel() {
			return quantileCountInModel;
		}

		public boolean getUseOddsRatio() {
			final Boolean or = useOddsRatio.getBoolean();
			return ((or != null) && or.booleanValue());
		}

		public boolean needsTables(final int inDesiredTableCount) {
			boolean buildTables = false;
			final PenetranceTable[] tables = getPenetranceTables();
			if ((tables == null) || (tables.length < inDesiredTableCount)) {
				buildTables = true;
			} else {
				for (int i = 0; i < inDesiredTableCount; ++i) {
					if (tables[i] == null) {
						buildTables = true;
						break;
					}
				}
			}
			return buildTables;
		}

		public void resetAttributeCount(final int inAttributeCount) {
			attributeCount.setValue(inAttributeCount);
			final DocString[] oldNameArray = attributeNameArray;
			final DocDouble[] oldFrequencyArray = attributeAlleleFrequencyArray;
			attributeNameArray = new DocString[inAttributeCount];
			attributeAlleleFrequencyArray = new DocDouble[inAttributeCount];

			assert oldNameArray.length == oldFrequencyArray.length;
			System.arraycopy(oldNameArray, 0, attributeNameArray, 0, Math.min(oldNameArray.length, inAttributeCount));
			for (int i = oldNameArray.length; i < inAttributeCount; ++i) {
				attributeNameArray[i] = new DocString();
			}
			System.arraycopy(oldFrequencyArray, 0, attributeAlleleFrequencyArray, 0, Math.min(oldFrequencyArray.length, inAttributeCount));
			for (int i = oldFrequencyArray.length; i < inAttributeCount; ++i) {
				attributeAlleleFrequencyArray[i] = new DocDouble();
			}

			if (getParentDoc() != null) {
				final Integer whichModel = getParentDoc().findModel(this);
				assert whichModel != null;
				for (final DocListener l : getParentDoc().listeners) {
					l.attributeCountChanged(getParentDoc(), this, whichModel.intValue(), inAttributeCount);
				}
			}
		}

		public void setParentDoc(final SnpGenDocument parentDoc) {
			this.parentDoc = parentDoc;
		}

		public void setPenetranceTables(final PenetranceTable[] penetranceTables) {
			this.penetranceTables = penetranceTables;
			setQuantileCountInModel(penetranceTables.length);
		}

		public void setQuantileCountInModel(final int quantileCountInModel) {
			this.quantileCountInModel = quantileCountInModel;
		}

		public Exception verifyAllNeededParameters() {
			Exception outEx = null;

			TESTS: {
				if (modelId.getString() == null) {
					outEx = new InputException("Missing a model ID");
					break TESTS;
				}
				if (attributeCount.getInteger() == null) {
					outEx = new InputException("Missing an attribute-count");
					break TESTS;
				}
				if (heritability.getDouble() == null) {
					outEx = new InputException("Missing a heritability");
					break TESTS;
				}
				for (final DocString s : attributeNameArray) {
					if ((s.getString() == null) || (s.getString().length() == 0)) {
						outEx = new InputException("Missing an attribute name");
						break TESTS;
					}
				}
				for (final DocDouble s : attributeAlleleFrequencyArray) {
					if (s.getDouble() == null) {
						outEx = new InputException("Missing a minor-allele frequency");
						break TESTS;
					}
				}
			}
			return outEx;
		}
	}

	public static class DocString extends DocMember<String> {
		public DocString() {
			super();
		}

		public DocString(final DocString inString) {
			super(inString);
		}

		public DocString(final String inString) {
			super(inString);
		}

		public String getString() {
			return getValue();
		}

		@Override
		public String objectToType(final Object inValue) {
			return inValue.toString();
		}

		public void setString(final DocString value) {
			setValue(value);
		}

		public void setString(final String value) {
			setValue(value);
		}
	}

	/**
	 * Thrown when the parsed command-line is missing a non-optional option.
	 * <code>getMessage()</code> returns an error string suitable for reporting
	 * the error to the user (in English).
	 */
	public static class MissingOptionException extends Exception {
		private static final long serialVersionUID = 1L;
		private String optionName = null;

		MissingOptionException(final String optionName) {
			this(optionName, "Missing option '" + optionName + "'");
		}

		MissingOptionException(final String optionName, final String msg) {
			super(msg);
			this.optionName = optionName;
		}

		/**
		 * @return the name of the option that was unknown (e.g. "-u")
		 */
		public String getOptionName() {
			return optionName;
		}
	}

	public enum MIXED_MODEL_DATASET_TYPE {
		heterogeneous, hierarchical
	}

}
