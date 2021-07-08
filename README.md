# Gametes Version 2.2

Genetic Architecture Model Emulator for Testing and Evaluating Software (GAMETES) is an algorithm for the generation of complex single nucleotide polymorphism (SNP) models for simulated association studies. 

GAMETES is designed to generate epistatic models which we refer to as pure and strict. These models constitute the worst-case in terms of detecting disease associations, since such associations may only be observed if all n loci are included in the disease model. The user-friendly GAMETES software rapidly and precisely generates epistatic multi-locus models, and using these models, can also generate simulated datasets exhibiting epistasis.

Version 2.2 adds the ability to generate heterogeneous datasets by applying multiple independent models to different subsets of the simulated data. Further additional features include the facility to create additive datasets by applying multiple independent models to the entire dataset, as well as functionality for the design of continuous endpoints. Additionally, we have added a custom model generation feature, so that users may directly specify and examine the properties of any 2 or 3 locus SNP model. Simple Mendelian models may also be generated with this feature.


## Installation

In order to install GAMETES, download the .zip file containing the GAMETES source code from https://github.com/UrbsLab/GAMETES/tree/v2.2. Unzip the file, and navigate into the "/GAMETES/dist/" directory. The .jar file "gametes_2.2_dev.jar" can be called from the command-line, or it can be direclty opened to access the software through the GUI.



## Usage

To run the GAMETES User Interface, simply click on the file gametes_2.2_dev.jar, located within "/GAMETES/dist/". To run GAMETES from the command-line, navigate to the "/GAMETES/dist/" directory from your Terminal and call the .jar file as follows:
```console
user:~$ java -jar gametes_2.2_dev.jar <OPTIONS>
```

To obtain a full list of commands for command line operation, type:
```console
user:~$ java -jar gametes_2.2_dev.jar -h
```

Below is the list of command line operations possible through GAMETES:
```console
Usage: java -jar gametes.jar <program arguments>
If there are no arguments, the User Interface will be opened. Here is a complete list of program arguments:

{-M,--model} Command to generate model(s) with specified model constraints
    {-h,--heritability} double Specifies the heritability of a given model.
    {-p,--caseProportion} double Specifies the caseProportion of a given model.
    {-d,--useOddsRatio} <true if present, false otherwise> Normally, the EDM difficulty model difficulty estimate is used to rank models for selection.  This parameter over-rides the default in favor of the COR difficulty estimate.
    {-a,--attributeAlleleFrequency} double Specifies the minor allele frequency of a given attribute in a model.  The number of times this parameter is specified determines the number of attributes that are included in the model.  E.g. 2-locus, 3-locus, 4-locus models.
    {-o,--modelOutputFile} string  Output file name/path.  This parameter is used for -M to specify how model files are saved, and how they will be named.

{-D,--dataset} Command to generate dataset(s) with specified dataset constraints.
    {-n,--alleleFrequencyMin} double Minimum minor allele frequency for randomly generated, non-predictive attributes in datasets (default value: 0.01).
    {-x,--alleleFrequencyMax} double Maximum minor allele frequency for randomly generated, non-predictive attributes in datasets (default value: 0.5).
    {-a,--totalAttributeCount} integer Total number of attributes to be generated in simulated dataset(s) (default value: 100).
    {-t,--totalCount} integer (continuous data only) How many samples to generate for each dataset (default value: 800).
    {-s,--caseCount} integer (discrete data only) Number of case instances in simulated dataset(s). Cases have class = '1' (default value: 400).
    {-w,--controlCount} integer (discrete data only) Number of control instances in simulated dataset(s). Controls have class = '0' (default value: 400).
    {-r,--replicateCount} integer Total number of replicate datasets generated from given model(s) to be randomly generated (default value: 100).
    {-o,--datasetOutputFile} string Output file name/path.  This parameter is used for -D to specify how dataset files are saved, and how they will be named.
    {-c,--continuous} <true if present, false otherwise> Directs algorithm to generate datasets with continuous-valued endpoints rather than binary discrete datasets (default value: FALSE).
    {-h,--mixedModelDatasetType} [heterogeneous, hierarchical] if there are multiple models use heterogeneous or hierarchical (default value: hierarchical).
    {-b,--heteroLabel} <true if present, false otherwise> Produce output datasets that include model labels in addition to normal output when working with heterogeneous data (default value: FALSE).
    {-d,--standardDeviation} double The standard deviation around model penetrance values used to simulated continuous-valued endpoints.  Larger standard deviation values should yield noisier datasets, with a signal that is more difficult to detect. (default value: 0.2).

{-i,--modelInputFile} string Path/Name of input model file used for generating dataset(s).
{-w,--modelWeight} double For each model, the relative weight compared to other models. If not specified all models will have equal weight. The weight/totalWeight determines the model fraction. The fraction is used in heterogeneous datasets to determine the # of rows generated from each model. In continuous hierarchical models, the fraction controls the relative contribution to the continuous endpoint. For discrete hierarchical models, weight is ignored. The order of the fractions is 1) new models and then 2) input models. 
{-v,--predictiveInputFile} string a file containing snps data corresponding to predictive attributes. Rows correspond to number of instances while columns correspond to attributes. The last column of the input file corresponds to classification (case or control). There should not be a header in the file.
{-z,--noiseInputFile} string a file containing snps data corresponding to noisy attributes. Rows correspond to number of instances while columns correspond to attributes. There should not be a header in the file.
{-q,--rasQuantileCount} integer Number of quantiles (i.e. number of models selected across a difficulty-ranked distribution) that will be saved to the model file. (default value: 3).
{-p,--rasPopulationCount} integer Number of models to be generated, from which representative 'quantile'-models will be selected and saved. (default value: 1000).
{-t,--rasTryCount} integer Number of algorithmic attempts to generate a random model to satisfy parameter -p, before algorithm quits. (default value: 100000).
{-r,--randomSeed} integer Seed for random number generator used to simulate models and datasets. If specified, repeated runs will generate identical outputs. In this way, a colleague could recreate your datasets without needing to transfer the actual files.
{-h,--help} <true if present, false otherwise> What you are reading now
```

The following are a series of examples for use of GAMETES to generate models and datasets.

### **Basic model generation**
Here, we generate a single model using GAMETES. It has a heritability of 0.2, a case proportion of 30% compared to a control proprtion of 70%, two significant alleles driving the phenotype (one with a minor allele frequency (MAF) of 0.3 and the other with an MAF of 0.2). The model is saved as "basicModel". In attempting to generate the model, GAMETES will create a total of 1000 models, and then pick two of the models selected across the difficulty-ranked distrubtion of these models. It will perform 100,000 attempts to generate our desired random model before the algorithm quits.
```console
user:~$ java -jar gametes_2.2_dev.jar -M "-h 0.2 -p 0.3 -a 0.3 -a 0.2 -o basicModel" -q 2 -p 1000 -t 100000
```

### **Creating multiple models**
We can create multiple models at once using the GAMETES software, by including them in a chain within our arguments. In this example, we generate two models, one named "basicModel1," with a heritability of 0.1, a case proportion of 50%, and a single influential attribute with minor allele frequency of 0.3. The other model, named "basicModel2," will have a heritability of 0.03, a case proportion of 50%, and a single influential attribute with minor allele frequency of 0.5. For both models, we specify a quantile count of 1, meaning that we return a single model in each case.
```console
user:~$ java -jar gametes_2.2_dev.jar -M "-h 0.1 -p 0.5 -a 0.3 -o basicModel1" -M "-h 0.03 -p 0.5 -a 0.5 -o basicModel2" -q 1
```

### **Creating a model and dataset concurrently**
GAMETES also allows users to generate models and datasets at the same time. In this example, we generate a single model with a heritability of 0.1, a case proportion of 50%, and a single influential attribute with minor allele frequency of 0.3. The datasets that are generated from this model will have a minimum minor allele frequency of 0.01, a maximum minor allele frequency of 0.5, 100 attributes, 500 cases, and 500 controls. There will be 10 replicates, and they will be saved in a folder titled "myData." An important point to note here is that the number of dataset directories will match the number of quantiles for the model.
```console
user:~$ java -jar gametes_2.2_dev.jar -M " -h 0.2 -p 0.3 -a 0.3 -o modelForData" -q 1 -D "-n 0.01 -x 0.5 -a 100 -s 500 -w 500 -r 10 -o myData"
```

### **Creating a dataset based upon a loaded model**
If the user has already created a model previously, it can be loaded to generate a dataset instead of having to be re-made. In this example, we load the model "myModel" from our GAMETES directory by calling "myModel_Models.txt, and then use it to create a new dataset directory called "dataLoadedFromModel" with a minimum MAF of 0.01, a maximum MAF of 0.5, 20 attributes, 50 cases, 50 controls, and 3 replicates.
```console
user:~$ java -jar gametes_2.2_dev.jar -i "./modelForData_Models.txt" -D "-n 0.01 -x 0.5 -a 20 -s 50 -w 50 -r 3 -o dataLoadedFromModel"
```

New functionality in GAMETES v2.2 includes additive datasets, heterogenous datasets, both with and without model labels, and continuous endpoints. The following examples illustrate how to use these new features. 


### **Additive Datasets**
The default behavior when providing multiple models to generate a single dataset will be the creation of additive, or hierarchical, data. In this case, we specify two models. The first one, saved as "model1Additive," has a heritability of 0.01, a case proportion of 0.5, and two significant alleles with MAF of 0.3 and 0.1 respectively. The second model, saved as "model2Additive," has a heritability of 0.03, a case proportion of 0.5, and three significant alleles each with an MAF of 0.5. The contribution of our models is weighted as well, so that the first model contributes 75% effect to the data, while the second model contributes 25%. These weight values can be expressed as full numbers or as decimals (i.e., to get a 3:1 weight, we could say 75/25, 3/1, .75/.25, etc.). The dataset generated from our two models is hierarchical, with a minimum minor allele frequency of 0.01, a maximum minor allele frequency of 0.5, 20 attributes, 800 cases, 800 controls, and a single replicate. This dataset is saved into an output folder named "additiveData."
```console
user:~$ java -jar gametes_2.2_dev.jar -M "-h 0.1 -p 0.5 -a 0.3 -a 0.1 -o model1Additive" -w 75 -M "-h 0.03 -p 0.5 -a 0.5 -a 0.5 -a 0.5 -o model2Additive" -w 25 -q 1 -D "-h hierarchical -n 0.01 -x 0.5 -a 20 -s 800 -w 800 -r 1 -o additiveData"
```

### **Heterogenous Datasets**
To generate a heterogeneous dataset, we can use the same format of command that we used for our additive output, and then simply change the "-h" argument.
```console
user:~$ java -jar gametes_2.2_dev.jar -M "-h 0.1 -p 0.5 -a 0.3 -a 0.1 -o model1Het" -w 75  -M "-h 0.03 -p 0.5 -a 0.5 -a 0.5 -a 0.5 -o model2Het" -w 25 -q 1 -D "-h heterogeneous -n 0.01 -x 0.5 -a 20 -s 800 -w 800 -r 1 -o heterogeneousData"
```

### **Heterogenous Datasets (with model labels)**
To generate a heterogeneous dataset that includes labels corresponding to which model represents the main source of signal for each sample in output, we use the same heterogeneous command as before, and then include the "-b" tag.
```console
user:~$ java -jar gametes_2.2_dev.jar -M "-h 0.1 -p 0.5 -a 0.3 -a 0.1 -o model1HetWithLabel" -w 75  -M "-h 0.03 -p 0.5 -a 0.5 -a 0.5 -a 0.5 -o model2HetWithLabel" -w 25 -q 1 -D "-h heterogeneous -b -n 0.01 -x 0.5 -a 20 -s 800 -w 800 -r 1 -o heterogeneousDataWithLabels"
```

### **Continuous Endpoints**
To generate continous endpointed data, we can make use of the "-c," "-d," and "-t" tags. "-c" specifies that we want continuous output, "-d" will specify a standard deviation for our continuous outputs (in this case 0.5), and "-t" will specify the number of samples in our output (in this case, 100).
```console
user:~$ java -jar gametes_2.2_dev.jar -M "-h 0.1 -p 0.5 -a 0.3 -a 0.1 -o model1Cont" -w 75 -M "-h 0.03 -p 0.5 -a 0.5 -a 0.5 -a 0.5 -o model2Cont" -w 25 -q 1 -D "-c -d 0.5 -t 100 -n 0.01 -x 0.5 -a 20 -r 1 -o contEndpointData"
```

### **Generating Datasets from Loaded Models**
Previously generated models can be loaded into GAMETES for the creation of output data through the use of the "-i" tag. Here, we provide two examples of loading in pre-existing model files, first for the generation of heterogeneous output, and second for the generation of additive output.
```console
user:~$ java -jar gametes_2.2_dev.jar -i "../src/myModel_Models.txt" -i "../src/otherModel_Models.txt" -q 1 -D "-h heterogeneous -r 1 -o hetDataFromLoadedModels"

user:~$ java -jar gametes_2.2_dev.jar -i "../src/myModel_Models.txt" -i "../src/otherModel_Models.txt" -q 1 -D "-h hierarchical -r 1 -o additiveDataFromLoadedModels"
```

For more examples of commands, refer to our User Guide.


## Organization of Source Code
**Directory**

    • build [includes all corresponding classes for the .java files]
    • dist [contains the .jar file as well as documentation for the program]
    • gametes_users_guide [user guide information for the software]
    • launch [a series of configurations that can be run for the generation of various types of datasets]
    • lib [present for the creation of the .jar file]
    • resources [present for the creation of the .jar file]
    • scripts [Python scripts for testing]
    • src [houses all the .java source files]
        • org
            • epistasis
                • snpgen
                        • document [handles functionality for command-line parsing]
                            • CmdLineParserSrc.java [includes functions for how to parse user arguments from the command-line]
                            • SnpGenDocument.java [specifies default attributes of GAMETES models and datasets, and includes functionality for how to update these values]
                        • exception [houses input and processing exceptions]
                            • InputException.java [class for input exceptions]
                            • ProcessingException.java [class for processing exceptions]
                        • simulator [houses functionality for GAMETES model/data generation]
                            • PenetranceTable.java [class for penetrance table construction]
                            • SnpGenSimulator.java [class to perform creation of GAMETES models and datasets given user input]
                        • ui [user interface]
                            • EditModelDialog.java [functions for editing model details in the UI]
                            • GenerateModelDialog.java [functions for creating and saving models in the UI]
                            • ModelCreationTable.java [functions to show models in the UI once they're created]
                            • ModelTable.java [functions for overall model behavior in the UI]
                            • ModelUpdater.java [interface for models to be updated in the UI]
                            • PenetranceTablePane.java [functions for setting up models through the penetrance table in the UI]
                            • SnpGenMainWindow.java [includes layout for the UI and steps for initialization of simulation given user input]

For modifications to the GUI, refer to *SnpGenMainWindow.java* under the directory "src/org/epistasis/snpgen/ui/"

To incorporate new commandline functions into GAMETES, refer to *SnpGenDocument.java* under the directory "src/org/epistasis/snpgen/document/"

To add to the functionality of GAMETES, refer to *SnpGenSimulator.java* under the directory "src/org/epistasis/snpgen/simulator/"


## Contributing
Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change. In particular, we welcome changes to update the GUI or changes to improve the code's modularity.

Please make sure to update tests as appropriate.


**How to import GAMETES into Eclipse:**

• Download the appropriate branch of GAMETES from GitHub.

• Delete all existing .jar files in the GAMETES directory.

• Launch Eclipse.

• In Eclipse, click “File > Import".

• Within the import box that opens up, open the “General” tab and click “Projects from Folder or Archive”. Then click “Next".

• Click the “Directory” button next to the “Import source:” selection area, and navigate to the “src” folder of the GAMETES folder on your computer. Click “Open".

• “src” should be selected because it’s the only folder you’ve picked. Click “Finish".

• The GAMETES src folder should now be in your Package Explorer, ready to edit.

• Open “src/org.epistasis.snpgen.ui/SnpGenMainWindow.java”. Clicking the green run arrow at the top of Eclipse for this file will successfully open up the GAMETES user interface.



**How to build a .jar file:**

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

•	Update build.xml to read the JDK version you are using (build.xml, line 34)


## License
[MIT](https://choosealicense.com/licenses/mit/)
