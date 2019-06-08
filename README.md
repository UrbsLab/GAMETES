# Gametes Version 2.2

Genetic Architecture Model Emulator for Testing and Evaluating Software (GAMETES) is an algorithm for the generation of complex single nucleotide polymorphism (SNP) models for simulated association studies. 

GAMETES is designed to generate epistatic models which we refer to as pure and strict. These models constitute the worst-case in terms of detecting disease associations, since such associations may only be observed if all n-loci are included in the disease model. The user-friendly GAMETES software rapidly and precisely generates epistatic multi-locus models, and can generate simulated datasets based on these models. 

Version 2.2 adds the ability to generate heterogeneous datasets by applying multiple independent models to different subsets of the simulated data. Furthermore, you can now add features such as continuous endpoints and additive datasets. Additionally, we have added a custom model generation feature, so that users may directly specify and examine the properties of any 2 or 3 locus SNP model. Simple Mendelian models may also be generated with this feature.

## Installation

Download the .zip file containing the GAMETES source code. Unzip the file and locate the .jar file to use the software through the GUI.

We also offer command line functionality. To use the software from the command line, simply cd into the folder containing your source code.

 You can code a wrapper script calling GAMETES from the command line. This is most useful if your goal is to generate an archive of genetic models and simulated datasets. 




## Usage

To obtain a list of commands for command line operation, type:

```console
user:~$ java -jar GAMETES_2.2.jar -h
```

An example of generating a model file and associated datasets looks as follows: 

```console
user:~$ java -jar GAMETES_2.2.jar -M " -h 0.2 -p 0.3 -a 0.3 -a 0.2 -o myModel.txt" -q 2 -p 1000 -t 100000 -D " -n 0.01 -x 0.5 -a 100 -s 500 -w 500 -r 100 -o myData"
```

New functionality includes continuous endpoints, additive datasets, and heterogenous datasets.

**Continuous Endpoints**
```console
user:~$ java -jar GAMETES_2.2.jar -M " -h 0.1 -p 0.5 -a 0.3 -a 0.1 -o myModel.txt" -w 75 -M " -h 0.03 -p 0.5 -a 0.5 -a 0.5 -a 0.5 -o otherModel.txt" -w 25 -q 1 -p 100000 -t 100000000 -D " -c -n 0.01 -x 0.5 -a 100 -d 0.5 -t 2000 -r 30 -o contEndpointData" 
```

**Additive Datasets**
```console
user:~$ java -jar GAMETES_2.2.jar -M " -h 0.1 -p 0.5 -a 0.3 -a 0.1 -o myModel.txt" -w 75 -M " -h 0.03 -p 0.5 -a 0.5 -a 0.5 -a 0.5 -o otherModel.txt" -w 25 -D " -h hierarchical -n 0.01 -x 0.5 -a 100 -s 500 -w 500 -r 50 -o additiveData" 
```

**Heterogenous Datasets**
```console
user:~$ java -jar GAMETES_2.2.jar -M " -h 0.1 -p 0.5 -a 0.3 -a 0.1 -o myModel.txt" -w 75  -M " -h 0.03 -p 0.5 -a 0.5 -a 0.5 -a 0.5 -o otherModel.txt" -w 25 -q 2 -p 1000 -t 100000 -D "-n 0.01 -x 0.5 -a 100 -s 500 -w 500 -r 100 -o myHetData" 
```

For detailed explanation and more examples, see the User Guide.

## Organization of Source Code
**Directory**


    • bin
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



For GUI modifications, see *Model Generation*. 

For editing model dialog functionality within the GUI, see *EditModelDialog.java*. 

For generating model dialog functionality, see *GenerateModelDialog.java*. 

For model creation and model tables, see *ModelCreationTable.java* and *ModelTable.java*. 

For modifying the penetrance table pane, see *PenetranceTablePane.java*. 

For command line functionality, see *snpgen* -> *document*. 

For updating test cases, see the *scripts*.



## Contributing
Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change. In particular, we welcome changes to update the GUI or changes to improve the code's modularity.

We are especially looking to add GUI functionality for choosing between hierarchical and heterogeneous datasets, as this functionality currently exists only in the command line. 

Please make sure to update tests as appropriate.

**Building a .jar file:**

• Import the Gametes zip as an existing archive file into Eclipse (see tips for debugging in Eclipse below)

• Right click on the build.xml file, then run as "ant build"

• Go to your Eclipse Workplace (Finder) -> gametes-svn -> distr: here is the new .jar file, titled gametes_2.2_dev.jar



**Some tips and tricks for editing and debugging:**

•	Ensure that you have Java 8 and the latest version of Eclipse

•	Ensure that you are using JDK 1.8 (Preferences -> Install JREs)

•	Be sure to import “existing projects into workspace” and select the .zip file and its contents

•	Update build.xml to read the JDK version you are using (build.xml, line 34)


## License
[MIT](https://choosealicense.com/licenses/mit/)
