#-------------------------------------------------------------------------------
# Name:            GAMETES_Archive_Maker.py
# Author:          Ryan Urbanowicz 
# Created:         2/18/2014
# Description: Designed to make single models at a time, and make models and datasets separately.
#                Assumes use of EDM and not COR.
#GAMETES_use options: model, data, hetdata
#
# Status: Functional Finalized Format

#-------------------------------------------------------------------------------
#!/usr/bin/env python
import sys
import os
import getpass
import argparse
import collections
import random
import traceback

#change standard out to be unbuffered so no need to flush.
sys.stdout = os.fdopen(sys.stdout.fileno(), 'w', 0)

class Enum(set):
    """ http://stackoverflow.com/questions/3248851/pythons-enum-equivalent?rq=1 """
    def __getattr__(self, name):
        if name in self:
            return name
        raise AttributeError

Action = Enum(['model', 'data', 'het'])

def arrayToString(someArray, joinDelimiter='_'):
    return joinDelimiter.join([str(x) for x in someArray])
def parseArray(paramValue):
    myResult = eval(str(paramValue))
    
    if isinstance(myResult, collections.Iterable):
	myResult = [item for item in myResult]
    else:
	myResult = [myResult]
    print('parseArray returning \'' + str(myResult) + '\'')
    return myResult

def parseBool(paramValue):
    myResult = str(paramValue).lower() in ("yes", "true", "t", "1", "on")
    if not myResult:
	if not str(paramValue).lower() in ("no", "false", "f", "0", "off"):
	    raise RuntimeError('Value passed in as a boolean parameter was not recognized. Passed in value: ' + str(paramValue))
    return myResult


#from http://stackoverflow.com/questions/752308/split-array-into-smaller-arrays/752562#752562
def split_list(alist, wanted_parts=1):
    length = len(alist)
    return [ alist[i*length // wanted_parts: (i+1)*length // wanted_parts] 
             for i in range(wanted_parts) ]


def main(args):
    try:
	parser = argparse.ArgumentParser(description="""usage: %prog [options] dataset(s)
	      tool to facilitate creation of many simulated datasets with GAMETES software.""")
	#CAREFUL -- short versions of args such as '-m' for --mdr can cause problems if there are other options starting with the same letter. If a user passed
	#'-max=1' (by mistake, meaning --max=1) that would be interpreted as '-m ax=1' so mdr would be set to 'ax=1' and max would not be set
	parser.add_argument('--gametes', help='file path to the gametes jar file [default: %(default)s]', default='gametes.jar')
	parser.add_argument('--heritability',  help='(multiple allowed) [default: %(default)s]', nargs='*', type=float, default=[0.001,0.01,0.025,0.05,0.1,0.2,0.3,0.4])
	parser.add_argument('--mixedModelDatasetType', help='either heterogeneous or hierarchical [default: %(default)s]', choices=['heterogeneous', 'hierarchical'], default='heterogeneous')
	parser.add_argument('--numModelsPerDataset', help='(multiple allowed) list of [default: %(default)s]', nargs='*', type=int, default=[1,2,3,4])
	parser.add_argument('--numSnpsPerModel', help='(multiple allowed) list of [default: %(default)s]', nargs='*', type=int, default=[2])
	parser.add_argument('--maf', help='minor allele frequency for all model alleles [default: %(default)s]', nargs='*',type=float, default=[0.2,0.4])
	parser.add_argument('--modelPrevalence', help='Specifies the prevalence of a given model.', type=float, default=None)
	parser.add_argument('--useOddsRatio', help='Normally, the EDM difficulty model difficulty estimate is used to rank models for selection.  This parameter overrides the default in favor of the COR difficulty estimate. [default: %(default)s]', type=parseBool,
	                    default='false')
    
	parser.add_argument('--totalAttributeCount', help='(multiple allowed) total number of attributes [default: %(default)s]', nargs='*', type=int, default=[100,1000])
	parser.add_argument('--replicateCount', help='Total number of replicate datasets generated from given model(s) to be randomly generated. [default: %(default)s]', type=int, default=100)
	parser.add_argument('--datasetPrevalence', help='A fraction between 0 and 1 that determines the case control ratio.', nargs='*', type=float, default=[0.5])
	parser.add_argument('--totalCount', help='(multiple allowed) How many samples to generate for each dataset. totalCount * prevalence = # of cases. [default: %(default)s]',  nargs='*', type=int, default=[2000,8000])
	parser.add_argument('--alleleFrequencyMin', help='Minimum minor allele frequency for randomly generated, non-predictive attributes in datasets. [default: %(default)s]', type=float, default=0.01)
	parser.add_argument('--alleleFrequencyMax', help='Maximum minor allele frequency for randomly generated, non-predictive attributes in datasets. [default: %(default)s]', type=float, default=0.5)
	parser.add_argument('--continuous', help='if true, datasets will have continuous endpoints [default: %(default)s]', type=parseBool,
	                    default='false')
	parser.add_argument('--standardDeviation', help='(multiple allowed) The standard deviation around model penetrance values used to simulated continuous-valued endpoints.  Larger standard deviation values should yield noisier datasets, with a signal that is more difficult to detect. [default: %(default)s]', nargs='*', type=float, default=[0.1,0.3,0.5])
	parser.add_argument('--rangeMinimum', help='Minimum and maximum determine the range that model penetrance values are mapped to. Because of statistical sampling, based on the magnitude of the standard deviation, some points will be outside this range. [default: %(default)s]', type=float, default=0.0)
	parser.add_argument('--rangeMaximum', help='Minimum and maximum determine the range that model penetrance values are mapped to. Because of statistical sampling, based on the magnitude of the standard deviation, some points will be outside this range. [default: %(default)s]', type=float, default=1.0)
	parser.add_argument('--rasQuantileCount', help='Number of quantiles (i.e. number of models selected across a difficulty-ranked distribution) that will be saved to the model file. [default: %(default)s]', type=int, default=1)
	parser.add_argument('--rasPopulationCount', help='Number of models to be generated, from which representative quantile-models will be selected and saved. [default: %(default)s]', type=int, default=1000)
	parser.add_argument('--rasTryCount', help='Number of algorithmic attempts to generate a random model to satisfy parameter -p, before algorithm quits. [default: %(default)s]', type=int, default=50000)
	parser.add_argument('--randomSeed', 
	                  help='random seed for the runs. If "-1" one will be generated. [default: %(default)s]', type=int, default=0)
	parser.add_argument('--email', help='email address to notify if job has trouble [default: %(default)s]', default=getpass.getuser())
	parser.add_argument('--time', help='maximum walltime a job can use. Specify the time in the format HH:MM:SS. Hours can be more than 24. [default: %(default)s]', default='120:00:00')
	parser.add_argument('--doNotSubmit', help='Only collate results.  [default: %(default)s]', type=parseBool, default='false')
	parser.add_argument('--resubmitJobsWithErrors', help='if the error file contains errors size resubmit the job [default: %(default)s]', type=parseBool, default='false')
	parser.add_argument('--resubmitJobsWithoutOutput', help='if the error file does not exist resubmit the job [default: %(default)s]', type=parseBool, default='false')
	parser.add_argument('--queue', dest='queue', help='queue to submit jobs to [default: %(default)s]', default='')
	parser.add_argument('--qsubCmd', help='command needed to submit a job to cluster. Trick: if set to "bash" this will run on the local machine rather than submitting to the cluster [default: %(default)s]', default='qsub')
    
	options = parser.parse_args(args)
	if options.randomSeed == -1:
	    options.randomSeed = random.randint(1000000,2000000)
    
	print("Options: " + '\n'.join(str(options).split(',')))
	
	print("GAMETES: Generating Models and Datasets")

	jobCounter = 0
	pathArray = []
	def makePathArrayFolder():
	    #print('makePathArrayFolder called with ' + str(pathArray))
	    pathArrayDir = '/'.join(pathArray)
	    if not os.path.exists(pathArrayDir):
		os.makedirs(pathArrayDir)
	       # print('made dir: ' + pathArrayDir)
	    return pathArrayDir
	
	for numModelsPerDataset in options.numModelsPerDataset:
	    for numSnpsPerModel in options.numSnpsPerModel:
		pathArray.append(str(numModelsPerDataset) + 'x' + str(numSnpsPerModel) + '-way_' + options.mixedModelDatasetType)
			
		for h in options.heritability:
		    pathArray.append('her-' + str(h))

		    for maf in options.maf: 
			pathArray.append('maf-' + str(maf))
	    
			for popSize in options.totalCount:
			    pathArray.append('pop-' + str(popSize))

			    for numAttributes in options.totalAttributeCount:
				pathArray.append('attribs-' + str(numAttributes))

				standardDeviationOrPrevalenceArray = []
				if options.continuous:
				    standardDeviationOrPrevalenceArray = options.standardDeviation
				else:
				    standardDeviationOrPrevalenceArray = options.datasetPrevalence
	    
				for standardDeviationOrPrevalence in standardDeviationOrPrevalenceArray:
				    if options.continuous:
					pathArray.append('phenotype-continuous_sd-' + str(standardDeviationOrPrevalence))
				    else:
					pathArray.append('phenotype-discrete_prevalence-' + str(standardDeviationOrPrevalence))
	    
				    folderPath = makePathArrayFolder()
				    jobName = '_'.join(pathArray)
				    jobName += '_seed-' + str(options.randomSeed)
				    #if options.useOddsRatio:
					#jobName += '_oddsRatio'
				    #else:
					#jobName += '_EDM'
				    
				
				    #MAKE CLUSTER JOBS###################################################################
				    pbsFileName = folderPath + '/' + jobName +'.pbs'
				    outputFileName = folderPath + '/' + jobName +'.o'
				    if os.path.isfile(pbsFileName):
					outputExists = os.path.isfile(outputFileName)
					print('Skipping ' + pbsFileName + ' because already exists. Does output file exist? ' + str(outputExists))
				    else:
					pbsFile = open(pbsFileName, 'w')
					pbsFile.write('#!/bin/bash -l\n') #NEW
					#pbsFile.write('#PBS -A Moore\n') #NEW
					if options.queue:
					    pbsFile.write('#PBS -q ' + options.queue + '\n')
					pbsFile.write('#PBS -N ' + jobName + '\n')
					pbsFile.write('#PBS -l walltime=10:00:00\n')
					pbsFile.write('#PBS -l nodes=1:ppn=1\n')
					pbsFile.write('#PBS -l feature=noib\n')
					pbsFile.write('#PBS -M ' + getpass.getuser() + '\n')
					pbsFile.write('#PBS -o localhost:' + os.path.abspath(outputFileName) + '\n')
					pbsFile.write('#PBS -e localhost:' + os.path.abspath(folderPath) + '/' + jobName + '.e\n')
					#make sure people in my group can read my output files
					pbsFile.write('#PBS -W umask=022\n')
					pbsFile.write('cd $PBS_O_WORKDIR\n')
			    
					#FIRST SPECIFY MODEL(s)		    
					model = ' --model "'
					
					model += ' --heritability '+str(h)
					
					if options.modelPrevalence:
					    model += ' -p '+str(options.modelPrevalence)
			    
					model += (' -a '+str(maf)) * numSnpsPerModel
					    
					if options.useOddsRatio:
					    model += ' --useOddsRatio'
					    
					#FINISH Model section
					model += '"'
					
					command = 'java -jar ' + options.gametes
					
					command += model * numModelsPerDataset
					
					#NOW SPECIFY DATASET
					command += ' --dataset "--datasetOutputFile ' +  folderPath + '/' + jobName
					command += ' --mixedModelDatasetType ' + str(options.mixedModelDatasetType)
					command += ' --alleleFrequencyMin ' + str(options.alleleFrequencyMin)
					command += ' --alleleFrequencyMax ' + str(options.alleleFrequencyMax)
					command += ' --totalCount ' + str(popSize)
					command += ' --totalAttributeCount ' + str(numAttributes)
					command += ' --replicateCount ' + str(options.replicateCount)
					
					
					if options.continuous:
					    command += ' --continuous'
					    command += ' --standardDeviation ' + str(standardDeviationOrPrevalence)
					    command += ' --rangeMinimum ' + str(options.rangeMinimum)
					    command += ' --rangeMaximum ' + str(options.rangeMaximum)
					else:
					    command += ' --prevalence ' + str(standardDeviationOrPrevalence)
					    
					#FINISH Dataset section
					command += '"'
			    
					command += ' --rasQuantileCount ' + str(options.rasQuantileCount)
					command += ' --rasPopulationCount ' + str(options.rasPopulationCount)
					command += ' --rasTryCount ' + str(options.rasTryCount)
					
					command += ' --randomSeed '+str(options.randomSeed)
			    
					pbsFile.write(command + '\n')
					pbsFile.close()
					jobCounter +=1
					print("Submitting job #" + str(jobCounter) + ': ' + jobName)
					print("Command: " + command)
					os.system(options.qsubCmd + ' ' + pbsFileName)
					#####################################################################################  
				    pathArray.pop() #remove endpoint type
				#return -1
				pathArray.pop() #remove numAttributes
			    pathArray.pop() #remove popSize
			pathArray.pop() #remove maf
		    pathArray.pop() #remove heritability
		pathArray.pop() #remove numModelsPerDataset x numSnpsPerModel
	print(str(jobCounter)+ " jobs submitted.")  
	return 0
    except Exception as err:
	if str(err) == '0':
	    return 0
	else:
	    sys.stderr.write('ERROR: %s\n' % str(err))
	    traceback.print_exc(file=sys.stdout)
	    #parser.print_usage()
	    return 1


if __name__=="__main__":
    sys.exit(main(sys.argv[1:]))
