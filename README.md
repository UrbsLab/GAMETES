# Gametes Version 2.2

Genetic Architecture Model Emulator for Testing and Evaluating Software (GAMETES) is an algorithm for the generation of complex single nucleotide polymorphism (SNP) models for simulated association studies. 

GAMETES is designed to generate epistatic models which we refer to as pure and strict. These models constitute the worst-case in terms of detecting disease associations, since such associations may only be observed if all n-loci are included in the disease model. The user-friendly GAMETES software rapidly and precisely generates epistatic multi-locus models, and can generate simulated datasets based on these models. 

Version 2.2 adds the ability to generate heterogeneous datasets by applying multiple independent models to different subsets of the simulated data. Furthermore, you can now add features such as continuous endpoints and additive datasets. Additionally, we have added a custom model generation feature, so that users may directly specify and examine the properties of any 2 or 3 locus SNP model. Simple Mendelian models may also be generated with this feature.


## Installation

Download the .zip file containing the GAMETES source code. Unzip the file and locate the .jar file to use the software through the GUI.

We also offer command line functionality. To use the software from the command line, simply cd into the folder containing your source code.

You can code a wrapper script calling GAMETES from the command line. This is most useful if your goal is to generate an archive of genetic models and simulated datasets. 



## Usage

To run the GAMETES user interface, simply click on the file gametes_2.2_dev.jar, located under /GAMETES/dist/

To obtain a list of commands for command line operation, type:

```console
user:~$ java -jar gametes_2.2_dev.jar -h
```

Below is the list of command line operations possible through GAMETES:
```console
Usage: java -jar gametes.jar <program arguments>
If there are no arguments, the graphical user interface will be opened otherwise running in batch mode. Complete list of program arguments:

{-M,--model} Command to generate model(s) with specified model constraints
    {-h,--heritability} double Specifies the heritability of a given model.
    {-p,--caseProportion} double Specifies the caseProportion of a given model.
    {-d,--useOddsRatio} <true if present, false otherwise> Normally, the EDM difficulty model difficulty estimate is used to rank models for selection.  This parameter over-rides the default in favor of the COR difficulty estimate.
    {-a,--attributeAlleleFrequency} double Specifies the minor allele frequency of a given attribute in a model.  The number of times this parameter is specified determines the number of attributes that are included in the model.  E.g. 2-locus, 3-locus, 4-locus models.
    {-o,--modelOutputFile} string  Output file name/path.  This parameter is used for -M to specify how model files are saved, and how they will be named..

{-D,--dataset} Command to generate dataset(s) with specified dataset constraints.
    {-n,--alleleFrequencyMin} double Minimum minor allele frequency for randomly generated, non-predictive attributes in datasets.
    {-x,--alleleFrequencyMax} double Maximum minor allele frequency for randomly generated, non-predictive attributes in datasets.
    {-a,--totalAttributeCount} integer Total number of attributes to be generated in simulated dataset(s).
    {-m,--missingValueRate} double ???
    {-t,--totalCount} integer (continuous data only) How many samples to generate for each dataset.
    {-s,--caseCount} integer (discrete data only) Number of case instances in simulated dataset(s).  Cases have class = '1'.
    {-w,--controlCount} integer (discrete data only) Number of control instances in simulated dataset(s).  Controls have class = '0'.
    {-r,--replicateCount} integer Total number of replicate datasets generated from given model(s) to be randomly generated.
    {-o,--datasetOutputFile} string Output file name/path.  This parameter is used for -D to specify how dataset files are saved, and how they will be named.
    {-c,--continuous} <true if present, false otherwise> Directs algorithm to generate datasets with continuous-valued endpoints rather than binary discrete datasets.
    {-h,--mixedModelDatasetType} [heterogeneous, hierarchical] if there are multiple models use heterogeneous or hierarchical
    {-b,--heteroLabel} <true if present, false otherwise> Produce output datasets that include model labels in addition to normal output when working with heterogeneous data.
    {-d,--standardDeviation} double The standard deviation around model penetrance values used to simulated continuous-valued endpoints.  Larger standard deviation values should yield noisier datasets, with a signal that is more difficult to detect.

{-i,--modelInputFile} string Path/Name of input model file used for generating dataset(s).
{-w,--modelWeight} double For each model, the relative weight compared to other models. If not specified all models will have equal weight. The weight/totalWeight determines the model fraction. The fraction is used in heterogeneous datasets to determine the # of rows generated from each model. In continuous hierarchical models, the fraction controls the relative contribution to the continuous endpoint. For discrete hierarchical models, weight is ignored. The order of the fractions is 1) new models and then 2) input models. 
{-v,--predictiveInputFile} string ???
{-z,--noiseInputFile} string a file containing snps data. The number of rows will determine your dataset output size.
{-q,--rasQuantileCount} integer Number of quantiles (i.e. number of models selected across a difficulty-ranked distribution) that will be saved to the model file.
{-p,--rasPopulationCount} integer Number of models to be generated, from which representative 'quantile'-models will be selected and saved.
{-t,--rasTryCount} integer Number of algorithmic attempts to generate a random model to satisfy parameter -p, before algorithm quits.
{-r,--randomSeed} integer Seed for random number generator used to simulate models and datasets. If specified, repeated runs will generate identical outputs. In this way, a colleague could recreate your datasets without needing to transfer the actual files.
{-h,--help} <true if present, false otherwise> What you are reading now
```

An example of generating a model file and associated datasets looks as follows: 

```console
user:~$ java -jar gametes_2.2_dev.jar -M " -h 0.2 -p 0.3 -a 0.3 -a 0.2 -o myModel.txt" -q 2 -p 1000 -t 100000 -D " -n 0.01 -x 0.5 -a 100 -s 500 -w 500 -r 100 -o myData"
```

New functionality in GAMETES version 2 includes continuous endpoints, additive datasets, and heterogenous datasets  with and without model labels. The following examples illustrate how to use them. 

**Continuous Endpoints**
```console
user:~$ java -jar gametes_2.2_dev.jar -M " -h 0.1 -p 0.5 -a 0.3 -a 0.1 -o myModel.txt" -w 75 -M " -h 0.03 -p 0.5 -a 0.5 -a 0.5 -a 0.5 -o otherModel.txt" -w 25 -q 1 -p 100000 -t 100000000 -D " -c -n 0.01 -x 0.5 -a 100 -d 0.5 -t 2000 -r 30 -o contEndpointData" 
```

**Additive Datasets**
```console
user:~$ java -jar gametes_2.2_dev.jar -M " -h 0.1 -p 0.5 -a 0.3 -a 0.1 -o myModel.txt" -w 75 -M " -h 0.03 -p 0.5 -a 0.5 -a 0.5 -a 0.5 -o otherModel.txt" -w 25 -D " -h hierarchical -n 0.01 -x 0.5 -a 100 -s 500 -w 500 -r 50 -o additiveData" 
```

**Heterogenous Datasets**
```console
user:~$ java -jar gametes_2.2_dev.jar -M " -h 0.1 -p 0.5 -a 0.3 -a 0.1 -o myModel.txt" -w 75  -M " -h 0.03 -p 0.5 -a 0.5 -a 0.5 -a 0.5 -o otherModel.txt" -w 25 -q 2 -p 1000 -t 100000 -D "-n 0.01 -x 0.5 -a 100 -s 500 -w 500 -r 100 -o heterogeneousData" 
```

**Heterogenous Datasets (with model labels)**
```console
user:~$ java -jar gametes_2.2_dev.jar -M " -h 0.1 -p 0.5 -a 0.3 -a 0.1 -o myModel.txt" -w 75  -M " -h 0.03 -p 0.5 -a 0.5 -a 0.5 -a 0.5 -o otherModel.txt" -w 25 -q 2 -p 1000 -t 100000 -D "-h heterogeneous -n 0.01 -x 0.5 -a 100 -s 500 -w 500 -r 100 -b -o heterogeneousDataWithLabels"
```

For detailed explanation and more examples, see the User Guide.


## Organization of Source Code
**Directory**

    • build [corresponding classes for the .java files]
    • launch
    • lib
    • resources
    • scripts [Python scripts for testing]
    • src [houses all the .java files]
        • org
            • epistasis
                • snpgen
                        • document [handles functionality for command line and CL parsing]
                        • exception [houses input and processing exceptions]
                        • simulator [houses functionality for PenetranceTable, SnpGenSimulator]
                        • ui [user interface]
                                • Main
                                • Model Generation [GUI functionality]

For modifications to the GUI, refer to *SnpGenMainWindow.java* under the directory "src/org/epistasis/snpgen/ui/"

To incorporate new commandline functions into GAMETES, refer to *SnpGenDocument.java* under the directory "src/org/epistasis/snpgen/document/"

To add to the functionality of GAMETES, refer to *SnpGenSimulator.java* under the directory "src/org/epistasis/snpgen/simulator/"


## Contributing
Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change. In particular, we welcome changes to update the GUI or changes to improve the code's modularity.

Please make sure to update tests as appropriate.

**Importing GAMETES into Eclipse:**

• Download the appropriate branch of GAMETES from GitHub

• Delete all existing .jar files in the GAMETES directory

• Launch Eclipse

• In Eclipse, click “File > Import"

• Within the import box that opens up, open the “General” tab and click “Projects from Folder or Archive”. Then click “Next"

• Click the “Directory” button next to the “Import source:” selection area, and navigate to the “src” folder of the GAMETES folder on your computer. Click “Open"

• “src” should be selected because it’s the only folder you’ve picked. Click “Finish"

• The GAMETES src folder should now be in your Package Explorer, ready to edit

• Open “src/org.epistasis.snpgen.ui/SnpGenMainWindow.java”. Clicking the green run arrow at the top of Eclipse for this file will successfully open up the GAMETES user interface


**Building a .jar file:**

• Delete any existing .jar files from your GAMETES folder

• Open up Terminal (or your equivalent commandline editor)

• Check to make sure that you have "ant" installed by typing "ant -version". If you don't have ant, you can install it by calling "brew install ant""

• Once ant is installed, navigate into the GAMETES directory

• Create a "lib" folder within the GAMETES folder if it doesn't already exist

• Type "ant compile"

• Type "ant jar"

• The new .jar file, titled "gametes_2.2_dev.jar" should now exist within the "dist" folder of your GAMETES directory


**Some tips and tricks for editing and debugging:**

•	Ensure that you have Java 8 and the latest version of Eclipse

•	Ensure that you are using JDK 1.8 (Preferences -> Install JREs)

•	Be sure to import “existing projects into workspace” and select the .zip file and its contents

•	Update build.xml to read the JDK version you are using (build.xml, line 34)


## License
[MIT](https://choosealicense.com/licenses/mit/)
