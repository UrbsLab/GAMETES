package org.epistasis.snpgen.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.beans.Beans;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.epistasis.snpgen.document.SnpGenDocument;
import org.epistasis.snpgen.document.SnpGenDocument.DocBoolean;
import org.epistasis.snpgen.document.SnpGenDocument.DocDouble;
import org.epistasis.snpgen.document.SnpGenDocument.DocInteger;
import org.epistasis.snpgen.document.SnpGenDocument.DocMember;
import org.epistasis.snpgen.document.SnpGenDocument.DocModel;
import org.epistasis.snpgen.document.SnpGenDocument.DocString;
import org.epistasis.snpgen.document.SnpGenDocument.MIXED_MODEL_DATASET_TYPE;
import org.epistasis.snpgen.exception.InputException;
import org.epistasis.snpgen.exception.ProcessingException;
import org.epistasis.snpgen.simulator.PenetranceTable;
import org.epistasis.snpgen.simulator.SnpGenSimulator;

/* MenuDemo.java requires images/middle.gif. */

public class SnpGenMainWindow implements ModelTable.UpdateListener
{
	// Attributes
    private int nextModelNumber = 1;
    JTextArea output;
    ModelTable modelTable;
    JFrame frame;
    String newline = "\n";
    final JFileChooser fileChooser = new JFileChooser();
    File file;
    GuiDocumentLink documentLink;
    SnpGenSimulator simulator;
    JLabel quantileCountFieldMainWindow;
    int desiredQuantileCount;
    DatasetReplicateCountPanel datasetControlPanel;
    JButton editButton;
    JButton deleteButton;
    JButton generateButton;
    JRadioButton additiveButton;
    JRadioButton heteroButton;
    JCheckBox heteroLabelsCheckbox; 
    boolean activate;
    public EndpointTypePanel endpointTypePanel = null;

    public SnpGenMainWindow(final SnpGenDocument inSnpGenDocument) {
        file = null;
        simulator = new SnpGenSimulator();
        documentLink = new GuiDocumentLink();
        if (Beans.isDesignTime()) {
            documentLink.setDocument(new SnpGenDocument(false));
        } else {
            documentLink.setDocument(inSnpGenDocument);
        }
    }

    //---------------------------------------------------------------------------------------------------------------

    // FUNCTIONS
    
    /* Function: addBackedModel
     * Input: Container inContainer, DocModel inDocModel
     * Output: BackedModelUi outModelUi
     * ----------------------------------------------------------------
     * Description: 
     */
    public BackedModelUi addBackedModel(final Container inContainer, final DocModel inDocModel) {
        final BackedModelUi outModelUi = new BackedModelUi();
        outModelUi.setOpaque(true);

        outModelUi.attributeCount = inDocModel.attributeCount.getInteger();
        outModelUi.modelIdUi = addBackedString(outModelUi, inDocModel.modelId, 3, null);
        outModelUi.attributeCountUi = addBackedInteger(outModelUi, inDocModel.attributeCount, 3, null);
        outModelUi.heritabilityUi = addBackedDouble(outModelUi, inDocModel.heritability, 3, null);
        outModelUi.attributeNameUiList = new ArrayList<BackedTextField>();
        outModelUi.attributeAlleleFrequencyUiList = new ArrayList<BackedTextField>();
        outModelUi.add(outModelUi.attributeNamePanel = new JPanel());
        outModelUi.add(outModelUi.attributeAlleleFrequencyPanel = new JPanel());
        outModelUi.attributeNamePanel.setLayout(new BoxLayout(outModelUi.attributeNamePanel, BoxLayout.Y_AXIS));
        outModelUi.attributeAlleleFrequencyPanel
                .setLayout(new BoxLayout(outModelUi.attributeAlleleFrequencyPanel, BoxLayout.Y_AXIS));
        for (int i = 0; i < outModelUi.attributeCount; ++i) {
            outModelUi.attributeNameUiList
                    .add(addBackedString(outModelUi.attributeNamePanel, inDocModel.attributeNameArray[i], 3, null));
            outModelUi.attributeAlleleFrequencyUiList.add(addBackedDouble(outModelUi.attributeAlleleFrequencyPanel,
                    inDocModel.attributeAlleleFrequencyArray[i], 3, null));
        }
        inContainer.add(outModelUi);
        return outModelUi;
    }

    /* Function: createContentPane
     * Input: N/A
     * Output: Container contentPane
     * ----------------------------------------------------------------
     * Description: Create the full content pane using JPanels to include
     *  model and dataset information
     */
    public Container createContentPane() {
        JPanel contentPane; // The content pane of the window
        JPanel datasetPane; // The left tab
        JPanel modelPane; // The right tab

        // Initialize and set the layout of the full contentPane
        contentPane = new JPanel();
        contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.Y_AXIS));
        contentPane.setOpaque(true);

        // Initialize and set the layout of the modelPane
        modelPane = new JPanel();
        modelPane.setBorder(BorderFactory.createTitledBorder("Model Construction"));
        fillInModelPane(modelPane);

        // Initialize the dataset pane
        datasetPane = new DatasetPanel();

        // Add both the modelPane and the datasetPane
        contentPane.add(modelPane, BorderLayout.CENTER);
        contentPane.add(datasetPane, BorderLayout.CENTER);

        // Create the command section at the bottom of the frame:
        JPanel commandPanel;
        commandPanel = new JPanel();
        commandPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        generateButton = new JButton(new GenerateAction(this));
        commandPanel.add(generateButton, BorderLayout.CENTER);
        contentPane.add(commandPanel);

        return contentPane;
    }

    /* Function: createMenuBar
     * Input: N/A
     * Output: JMenuBar menuBar
     * ----------------------------------------------------------------
     * Description: Create the menu bar
     */
    public JMenuBar createMenuBar() {
        JMenuBar menuBar;
        JMenu menu;
        // Create the menu bar.
        menuBar = new JMenuBar();

        // Build the file menu.
        menu = new JMenu("File");
        menu.setMnemonic(KeyEvent.VK_F);
        menu.getAccessibleContext().setAccessibleDescription("The file menu");
        menuBar.add(menu);
        menu.add(new JMenuItem(new NewAction(this)));
        menu.add(new JMenuItem(new OpenAction(this)));
        menu.add(new JMenuItem(new SaveAction(this)));
        menu.add(new JMenuItem(new SaveAsAction(this)));

        return menuBar;
    }

    /* Function: updateQuantileCountField
     * Input: int inCount
     * Output: N/A
     * ----------------------------------------------------------------
     * Description: update quantile counts in the datasetControlPanel,
     *  the quantileCountFieldMainWindow, and the documentLink's rasQuantileCount
     */
    public void updateQuantileCountField(final int inCount) {
    	// Set the desired quantile count to be the input inCount
        desiredQuantileCount = inCount;
        
        // Set the value of the documents rasQuantileCount to be the inCount
        documentLink.getDocument().rasQuantileCount.setValue(inCount);
        
        // Set the text of the quantileCountFieldMainWindow
        if (inCount > 0) {
            quantileCountFieldMainWindow.setText(Integer.toString(inCount));
        } else {
            quantileCountFieldMainWindow.setText("--");
        }
        
        // Update the datasetCountField for the datasetControlPanel
        datasetControlPanel.updateDatasetCountField();
    }

    /* Function: updateSelection
     * Input: N/A
     * Output: N/A
     * ----------------------------------------------------------------
     * Description: Get the common quantile count of the models, and update
     *  the overall quantile count as well as the button enablement
     */
    @Override
    public void updateSelection() {
        final Integer commonQuantileCount = getCommonQuantileCount();
        updateQuantileCount(commonQuantileCount);
        updateButtonEnablement(commonQuantileCount);
    }

    /* Function: addComponent
     * Input: Class inClass, Container inContainer, DocMember inBackingField, int inColumnCount, String inLabel
     * Output: JComponent SnpGenMainWindow.addComponent
     * ----------------------------------------------------------------
     * Description: Return the added component based upon the input
     */
    // WARNING: If you change the relationship of inClass to the class of the
    // output JComponent, be sure to update the createBacked<whatever> methods above.
    protected JComponent addComponent(final Class<?> inClass, final Container inContainer,
            final DocMember<?> inBackingField, final int inColumnCount, final String inLabel) {
    	// Get the outComponent
        JComponent outComp = null;
        outComp = null;
        
        // If there is a backing field, determine the type of inClass and use it to specify the outComponent
        if (inBackingField != null) {
            if (inClass.equals(JCheckBox.class)) {
                outComp = new BackedCheckBox((DocBoolean) inBackingField);
            } else if (inClass.equals(JTextField.class)) {
                outComp = new BackedTextField(inColumnCount, inBackingField);
            } else if (inClass.equals(JLabel.class)) {
                outComp = new BackedLabel(inBackingField);
            }
            documentLink.addBackedComponent((BackedComponent) outComp);
        }
        return SnpGenMainWindow.addComponent(outComp, inClass, inContainer, inColumnCount, inLabel);
    }

    /* Function: addBackedBoolean
     * Input: Container inContainer, DocBoolean inBackingField, int inColumnCount, String inLabel
     * Output: BackedCheckBox addComponent
     * ----------------------------------------------------------------
     * Description: Return the added component based upon the input
     */
    // WARNING: If you change the relationship of inClass to the class of the
    // output JComponent, be sure to update the createBacked<whatever> methods above.
    private BackedCheckBox addBackedBoolean(final Container inContainer, final DocBoolean inBackingField,
            final int inColumnCount, final String inLabel) {
        return (BackedCheckBox) addComponent(JCheckBox.class, inContainer, inBackingField, inColumnCount, inLabel);
    }

    /* Function: addBackedDouble
     * Input: Container inContainer, DocDouble inBackingField, int inColumnCount, String inLabel
     * Output: BackedTextField addComponent
     * ----------------------------------------------------------------
     * Description: Return the added component based upon the input
     */
    private BackedTextField addBackedDouble(final Container inContainer, final DocDouble inBackingField,
            final int inColumnCount, final String inLabel) {
        return (BackedTextField) addComponent(JTextField.class, inContainer, inBackingField, inColumnCount, inLabel);
    }

    /* Function: addBackedInteger
     * Input: Container inContainer, DocInteger inBackingField, intinColumnCount, String inLabel
     * Output: BackedTextField addComponent
     * ----------------------------------------------------------------
     * Description: Return the added component based upon the input
     */
    private BackedTextField addBackedInteger(final Container inContainer, final DocInteger inBackingField,
            final int inColumnCount, final String inLabel) {
        return (BackedTextField) addComponent(JTextField.class, inContainer, inBackingField, inColumnCount, inLabel);
    }

    /* Function: addBackedLabel
     * Input: Container inContainer, DocMember<> inBackingField, int inColumnCount, String inLabel
     * Output: BackedLabel addComponent
     * ----------------------------------------------------------------
     * Description: Return the added component based upon the input
     */
    private BackedLabel addBackedLabel(final Container inContainer, final DocMember<?> inBackingField,
            final int inColumnCount, final String inLabel) {
        return (BackedLabel) addComponent(JLabel.class, inContainer, inBackingField, inColumnCount, inLabel);
    }

    /* Function: addBackedString
     * Input: Container inContainer, DocString inBackingField, int inColumnCount, String inLabel
     * Output: BackedTextField addComponent
     * ----------------------------------------------------------------
     * Description: Return the added component based upon the input
     */
    private BackedTextField addBackedString(final Container inContainer, final DocString inBackingField,
            final int inColumnCount, final String inLabel) {
        return (BackedTextField) addComponent(JTextField.class, inContainer, inBackingField, inColumnCount, inLabel);
    }

    /* Function: checkButtonEnablement
     * Input: N/A
     * Output: N/A
     * ----------------------------------------------------------------
     * Description: Get the common quantile count and update button
     *  enablement based upon the quantile count
     */
    private void checkButtonEnablement() {
        final Integer commonQuantileCount = getCommonQuantileCount();
        updateButtonEnablement(commonQuantileCount);
    }

    /* Function: chooseFile
     * Input: Component inParent, String inTitle
     * Output: File outFile
     * ----------------------------------------------------------------
     * Description: Identify the appropriate file given our input filename
     */
    private File chooseFile(final Component inParent, final String inTitle) {
    	// Initialize the outFile
        File outFile = null;

        // If there is an input title, set the dialog title of the file chooser
        if (inTitle != null) {
            fileChooser.setDialogTitle(inTitle);
        }
        
        // Get the file corresponding to our input title/inParent
        final int returnVal = fileChooser.showSaveDialog(inParent);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            outFile = fileChooser.getSelectedFile();
        }
        return outFile;
    }

    /* Function: cleanUpModelName
     * Input: String modelName
     * Output: String modelName
     * ----------------------------------------------------------------
     * Description: strip down the name of the model for consistency
     */
    private String cleanUpModelName(String modelName) {
        final int dot = modelName.indexOf(".");
        if (dot >= 0) {
            modelName = modelName.substring(0, dot);
        }

        if (modelName.toLowerCase().endsWith("_models")) {
            modelName = modelName.substring(0, modelName.length() - "_models".length());
        }
        return modelName;
    }

    /* Function: createAndShowGUI
     * Input: N/A
     * Output: N/A
     * ----------------------------------------------------------------
     * Description: Convert the documentLink to a GUI, and then create
     *  a new JFRAME, showing the GUI
     */
    private void createAndShowGUI() {
        try {
        	// Convert the documentLink to a GUI
            documentLink.documentToGui();
        } catch (final IllegalAccessException iea) {
        }

        // Initialize a new JFrame and update information based upon the input
        frame = new JFrame("GAMETES 2.2 dev");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setContentPane(createContentPane());
        updateSelection();
        frame.setSize(720, 900);
        frame.setVisible(true);
    }

    
    /* Function: createSnpGenDocument
     * Input: N/A
     * Output: N/A
     * ----------------------------------------------------------------
     * Description: 
     */
    private void createSnpGenDocument() throws IllegalAccessException {
        documentLink.setDocument(new SnpGenDocument(true));
        documentLink.documentToGui();
    }

    /* Function: createSnpModel
     * Input: N/A
     * Output: N/A
     * ----------------------------------------------------------------
     * Description: 
     */
    private void createSnpModel() throws IOException {
        final SnpGenDocument document = getDocument();
        final int firstAttributeNumber = document.getNextPredictiveAttributeNumber();
        final String[] snpTitles = new String[] { "P" + firstAttributeNumber, "P" + (firstAttributeNumber + 1),
                "P" + (firstAttributeNumber + 2), };
        final int locusCount = 2;
        final DocModel model = new DocModel(locusCount);
        model.modelId.setString("Model " + nextModelNumber++);
        for (int i = 0; i < locusCount; ++i) {
            model.attributeAlleleFrequencyArray[i].setValue(0.2);
            model.attributeNameArray[i].setString(snpTitles[i]);
        }
        editSnpModel(-1, model, false, snpTitles[2]);
    }

    /* Function: editSnpModel
     * Input: N/A
     * Output: N/A
     * ----------------------------------------------------------------
     * Description: 
     */
    private void editSnpModel() throws IOException {
        final SnpGenDocument document = getDocument();
        final int[] selections = modelTable.getWhichSelectedModels();
        final int whichModel = selections[0];
        final DocModel model = document.modelList.get(whichModel);
        editSnpModel(whichModel, model);
    }

    private void editSnpModel(final int whichModel, final DocModel inModel) throws IOException {
        final SnpGenDocument document = getDocument();
        final int firstAttributeNumber = document.getNextPredictiveAttributeNumber();
        final String spareSnpTitle = "P" + (firstAttributeNumber);
        editSnpModel(whichModel, inModel, true, spareSnpTitle);
    }

    private void editSnpModel(final int whichModel, final DocModel inModel, final boolean inEnableSaveAs,
            final String inSpareSnpTitle) throws IOException
    {
        final SnpGenDocument document = getDocument();
        DocModel model = new DocModel(inModel);
        model.setParentDoc(null);
        model.setQuantileCountInModel(1); 
        final String[] snpMajorAlleles = new String[] { "A", "B", "C" };
        final String[] snpMinorAlleles = new String[] { "a", "b", "c" };
        final EditModelDialog editModelDialog = new EditModelDialog(model, frame, false /* inEnableSaveAs */,
                snpMajorAlleles, snpMinorAlleles, inSpareSnpTitle);
        editModelDialog.pack();
        editModelDialog.setLocationRelativeTo(frame);
        editModelDialog.setVisible(true);
        if (editModelDialog.isSaved()) {
            {
                model.file = chooseFile(frame, "Location for model files");
            }
            if (model.file != null) {
                String modelFilename = model.file.getName();
                final String modelName = cleanUpModelName(modelFilename);

                String extension = ".txt";
                final int dot = modelFilename.indexOf(".");
                if (dot >= 0) {
                    extension = modelFilename.substring(dot);
                    modelFilename = modelFilename.substring(0, dot);
                }
                if (!modelFilename.toLowerCase().endsWith("_models")) {
                    modelFilename = modelFilename + "_Models" + extension;
                    model.file = new File(model.file.getParent(), modelFilename);
                }

                model.modelId.setString(modelName);
                simulator.writeModelTables(model, model.file, null, true);
            }
            model = editModelDialog.getModel();
            if (whichModel < 0) {
                document.addDocModel(model);
            } else {
                document.updateDocModel(whichModel, model);
            }
            model.setParentDoc(document);
            checkButtonEnablement();
        }
    }


    /* Function: fillInModelPane
     * Input: JPanel inModelPane
     * Output: N/A
     * ----------------------------------------------------------------
     * Description: 
     */
    private void fillInModelPane(final JPanel inModelPane) {
        inModelPane.setLayout(new BoxLayout(inModelPane, BoxLayout.Y_AXIS));

        // Create the model-creation pane:
        JPanel modelCreationPane;
        modelCreationPane = new JPanel();
        modelCreationPane.setAlignmentX(Component.CENTER_ALIGNMENT);
        final JButton generateModelButton = new JButton(new GenerateModelAction(this));
        modelCreationPane.add(generateModelButton, BorderLayout.CENTER);
        final JButton createButton = new JButton(new CreateModelAction(this));
        modelCreationPane.add(createButton, BorderLayout.CENTER);
        final JButton loadButton = new JButton(new LoadModelAction(this));
        modelCreationPane.add(loadButton, BorderLayout.CENTER);
        editButton = new JButton(new EditModelAction(this));
        editButton.setEnabled(false);
        modelCreationPane.add(editButton, BorderLayout.CENTER);
        deleteButton = new JButton(new RemoveModelAction(this));
        deleteButton.setEnabled(false);
        modelCreationPane.add(deleteButton, BorderLayout.CENTER);
        inModelPane.add(modelCreationPane);

        // Create the model-parameter pane:
        JPanel modelParameters;
        modelParameters = new JPanel();
        modelParameters.setBorder(BorderFactory.createLineBorder(Color.black));
        modelParameters.setAlignmentX(Component.CENTER_ALIGNMENT);
        // addBackedModel(modelParameters,
        // documentLink.getDocument().addDocModel(1));
        modelParameters.setLayout(new BoxLayout(modelParameters, BoxLayout.Y_AXIS));

        // initExampleModels();
        modelTable = ModelTable.createModelTable(documentLink.getDocument());
        modelTable.addUpdateListener(this);
        final JScrollPane scrollPane = new JScrollPane(modelTable);
        modelTable.setFillsViewportHeight(true);
        modelParameters.add(scrollPane);
        // modelParameters.add(table);

        inModelPane.add(modelParameters);

        // Create the model-footer pane:
        JPanel modelFooterPane;
        modelFooterPane = new JPanel();
        modelFooterPane.setAlignmentX(Component.CENTER_ALIGNMENT);
        quantileCountFieldMainWindow = (JLabel) addComponent(JLabel.class, modelFooterPane, null, 0,
                "Number of EDM Quantiles:");
        quantileCountFieldMainWindow.setText("0");

        inModelPane.add(modelFooterPane);
    }

    /* Function: generateDatasets
     * Input: N/A
     * Output: N/A
     * ----------------------------------------------------------------
     * Description: Function to generate datasets given use of the
     *   GAMETES GUI.
     */
    private void generateDatasets() {
        Exception paramError;
        try {
        	// Get information from the GUI and save it into the document
            documentLink.guiToDocument();
            
            // Get the document corresponding to the current SnpGenMainWindow
            final SnpGenDocument document = documentLink.getDocument();
            
            // Make sure all needed parameters are present
            if ((paramError = document.verifyAllNeededParameters()) != null) {
                throw paramError;
            }
            
            // Save out the output file locations for our datasets
            final File outputFile = chooseFile(frame, "Location for generated datasets");
            if (outputFile != null) {
                for (final SnpGenDocument.DocDataset dataset : document.datasetList) {
                    dataset.outputFile = outputFile;
                }

                final ProgressDialog progressor = new ProgressDialog("Saving datasets...");
                // simulator.setProgressHandler(progressor);
                
                // Set the document of the simulator and get the models from our input
                simulator.setDocument(document);
                final ArrayList<SnpGenDocument.DocModel> selectedModels = getSelectedModels();
                                   
                // Combine the model tables into quantiles and generate datasets
                // simulator.combineModelTablesIntoQuantiles(document.modelList, document.inputFiles, document.inputFileFractions);
                simulator.combineModelTablesIntoQuantiles(selectedModels, document.modelInputFiles);
                final SwingWorker<Exception, Void> worker = new SwingWorker<Exception, Void>() {
                    @Override
                    public Exception doInBackground() {
                        Exception outException = null;
                        try {
                            simulator.generateDatasets(progressor);
                            progressor.setVisible(false);
                        } catch (final Exception ex) {
                            ex.printStackTrace();
                            outException = ex;
                        }
                        return outException;
                    }
                };
                synchronized (worker) {
                    worker.execute();
                    progressor.setVisible(true);
                }
                final Exception except = worker.get();
                if (except != null) {
                    throw except;
                }
                // simulator.generateDatasets(doc, null);
                // progressor.setVisible(false);
            }
        } catch (final Exception ex) {
            handleException(ex);
        }
    }
                 

    /* Function: generateSnpModel
     * Input: N/A
     * Output: N/A
     * ----------------------------------------------------------------
     * Description: 
     */
    private void generateSnpModel() {
        final SnpGenDocument document = getDocument();
        final int modelNumber = nextModelNumber++;
        final GenerateModelDialog modelDialog = new GenerateModelDialog(frame, modelNumber,
                document.getNextPredictiveAttributeNumber());
        // Since the new model will replace the old one, the new model's
        // predictive attribute names should always start at 1:
        // GenerateModelDialog modelDialog = new GenerateModelDialog(frame,
        // getDocument().getNextModelNumber(), 1);
        modelDialog.pack();
        modelDialog.setLocationRelativeTo(frame);
        modelDialog.setVisible(true);
        if (modelDialog.isSaved()) {
            final int desiredPopulationCount = modelDialog.getQuantilePopulationFieldValue();
            boolean generatedModels = false;
            try {
                final File outputFile = chooseFile(frame, "Location for model files");
                if (outputFile != null) {
                    final int attributeCount = modelDialog.getAttributeCount();
                    final String[] attributeNames = modelDialog.getAttributeNames();
                    final double[] attributeMafs = modelDialog.getAttributeMinorAlleleFrequencies();
                    // removeSnpModel();
                    // String modelName = /*"Model 1"*/ "Model " + modelNumber;
                    String modelName = outputFile.getName();
                    modelName = cleanUpModelName(modelName);
                    final DocModel model = document.addNewDocModel(attributeCount, modelName, attributeNames,
                            attributeMafs);
                    model.file = outputFile;
                    checkButtonEnablement();
                    model.heritability.setValue(modelDialog.getHeritability());
                    model.prevalence.setValue(modelDialog.getPrevalence());
                    model.useOddsRatio.setValue(modelDialog.getUseOddsRatio());
                    model.fraction.setValue(new Double(1));
                    final int quantileCount = modelDialog.getQuantileCountFieldValue();
                    model.setQuantileCountInModel(quantileCount);
                    // updateQuantileCountField(quantileCount);
                    long tryCountLong = Math.max(desiredPopulationCount * 100L, 100000L);
                    tryCountLong = Math.min(tryCountLong, Integer.MAX_VALUE);
                    final int tablesToTryCount = (int) tryCountLong;
                    document.rasPopulationCount.setValue(desiredPopulationCount);
                    document.rasTryCount.setValue(tablesToTryCount);

                    final ProgressDialog progressor = new ProgressDialog("Generating models...");

                    simulator.setDocument(document);
                    final SwingWorker<Exception, Void> worker = new SwingWorker<Exception, Void>() {
                        @Override
                        public Exception doInBackground() {
                            Exception outException = null;
                            try {
                                if (progressor != null) {
                                    progressor.setMaximum(tablesToTryCount);
                                }

                                final ArrayList<DocModel> modelList = new ArrayList<DocModel>();
                                modelList.add(model);
                                final double[][] allTableScores = simulator.generateTablesForModels(modelList,
                                        model.getQuantileCountInModel(), desiredPopulationCount, tablesToTryCount,
                                        progressor);
                                simulator.writeTablesAndScoresToFile(modelList, allTableScores,
                                        model.getQuantileCountInModel());
                                // progressor.setVisible(false);
                            } catch (final Exception ex) {
                                outException = ex;
                            }
                            progressor.setVisible(false);
                            return outException;
                        }
                    };
                    synchronized (worker) {
                        worker.execute();
                        progressor.setVisible(true);
                    }
                    final Exception except = worker.get();
                    if (except != null) {
                        throw except;
                    }
                    generatedModels = true;
                }
            } catch (final Exception ex) {
                handleException(ex);
                generatedModels = false;
            }
            if (generatedModels && (simulator.getTablePopulationCountFound() < desiredPopulationCount)) {
                showErrorMessage("Warning", "You asked for a population of " + desiredPopulationCount
                        + " models, but I only found " + simulator.getTablePopulationCountFound());
            }
        }
    }

    
    /* Function: getCommonQuantileCount
     * Input: N/A
     * Output: Integer quantileCount
     * ----------------------------------------------------------------
     * Description: If all of the selected models have the same 
     *  quantile count, then return that number. Otherwise, return null
     */
    private Integer getCommonQuantileCount() {
        Integer quantileCount = null;
        final ArrayList<SnpGenDocument.DocModel> models = getSelectedModels();
        for (final SnpGenDocument.DocModel model : models) {
            if (quantileCount == null) {
                quantileCount = model.getQuantileCountInModel();
            } else {
                if (quantileCount != model.getQuantileCountInModel()) {
                    quantileCount = null;
                    break;
                }
            }
        }
        return quantileCount;
    }

    /* Function: getDocument
     * Input: N/A
     * Output: documentLink.getDocument()
     * ----------------------------------------------------------------
     * Description: Return the document of the documentLink
     */
    private SnpGenDocument getDocument() {
        return documentLink.getDocument();
    }
    
    /* Function: getSelectModels
     * Input: N/A
     * Output: ArrayList<SnpGenDocument.DocModel> outModels
     * ----------------------------------------------------------------
     * Description: 
     */
    // If all of the selected models have the same quantile-count then return that number, else return null.
    private ArrayList<SnpGenDocument.DocModel> getSelectedModels() {
        final ArrayList<SnpGenDocument.DocModel> outModels = new ArrayList<SnpGenDocument.DocModel>();
        final int[] selections = modelTable.getWhichSelectedModels();
        if (selections.length > 0) {
            final SnpGenDocument document = getDocument();
            double totalWeight = 0;
            final double[] modelWeights = new double[selections.length];
            for (final int s : selections) {
                final DocModel model = document.getModel(s);
                outModels.add(model);
                final double modelWeight = model.fraction.value;
                //totalWeight = modelWeight;
                totalWeight = totalWeight + modelWeight;
                modelWeights[outModels.size() - 1] = modelWeight;
            }
            
            document.modelFractions = new double[modelWeights.length];
            for (int modelIndex = 0; modelIndex < modelWeights.length; ++modelIndex) {
                document.modelFractions[modelIndex] = modelWeights[modelIndex] / totalWeight;
            }
        }
        return outModels;
    }

    /* Function: handleExpection
     * Input: Exception inEx
     * Output: Exception
     * ----------------------------------------------------------------
     * Description: Return the appropriate error message based upon the type
     *  of exception
     */
    private Exception handleException(final Exception inEx) {
        if (inEx instanceof InputException) {
            showErrorMessage("Input error", inEx.getLocalizedMessage());
            inEx.printStackTrace();
        } else if (inEx instanceof ProcessingException) {
            showErrorMessage("Error in processing", inEx.getLocalizedMessage());
            inEx.printStackTrace();
        } else {
            final String msg = inEx.getMessage();
            System.out.println(msg);
            inEx.printStackTrace();
        }
        return null; // could return an exception that still needs to be handled
    }

    /* Function: loadDocument
     * Input: File inFile, GuiDocumentLink inLink
     * Output: N/A
     * ----------------------------------------------------------------
     * Description: Load the file specified by the instance variable "file" into the UI
     */
    private void loadDocument(final File inFile, final GuiDocumentLink inLink) throws IllegalAccessException {
        inLink.getDocument().load(inFile);
        inLink.documentToGui();
    }

    /* Function: loadModelFile
     * Input: N/A
     * Output: 
     * ----------------------------------------------------------------
     * Description: 
     */
    private void loadModelFile() {
        try {
            final int returnVal = fileChooser.showOpenDialog(frame);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                final File inputFile = fileChooser.getSelectedFile();
                final PenetranceTable[] tables = simulator.fetchTables(inputFile);
                final PenetranceTable firstTable = tables[0];
                final String[] attributeNames = firstTable.getAttributeNames();
                final double[] attributeMafs = firstTable.getMinorAlleleFrequencies();
                String modelName = inputFile.getName();
                modelName = cleanUpModelName(modelName);
                final DocModel model = documentLink.getDocument().addNewDocModel(attributeNames.length, modelName,
                        attributeNames, attributeMafs);
                model.file = inputFile;
                model.setPenetranceTables(tables);
                checkButtonEnablement();
                model.heritability.setValue(firstTable.getActualHeritability());
            }
        } catch (final Exception ex) {
            handleException(ex);
        }
    }

    /* Function: openDocument
     * Input: N/A
     * Output: N/A
     * ----------------------------------------------------------------
     * Description: Get the file corresponding to the current open dialog
     *  load it
     */
    private void openDocument() throws IllegalAccessException {
        final int returnVal = fileChooser.showOpenDialog(frame);

        if (returnVal == JFileChooser.APPROVE_OPTION) {
            file = fileChooser.getSelectedFile();
            loadDocument(file, documentLink);
        }
    }

    /* Function: removeSnpModel
     * Input: Exception inEx
     * Output: 
     * ----------------------------------------------------------------
     * Description: 
     */
    private void removeSnpModel() {
        final SnpGenDocument document = getDocument();
        final int[] selections = modelTable.getWhichSelectedModels();
        // Iterate the models from the end, because otherwise each model we remove will screw up whichModel:
        for (int i = selections.length - 1; i >= 0; --i) {
            final int whichModel = selections[i];
            modelTable.setSelected(whichModel, false);
            if (document.getModelCount() > 0) {
                document.removeDocModel(whichModel);
            }
        }
        checkButtonEnablement();
    }

    /* Function: saveDocument
     * Input: N/A
     * Output: N/A
     * ----------------------------------------------------------------
     * Description: Save the current file 
     */
    private void saveDocument() throws IllegalAccessException {
        if (file == null) {
            saveDocumentAs();
        } else {
            storeDocument(file, documentLink);
        }
    }

    /* Function: saveDocumentAs
     * Input: N/A
     * Output: N/A
     * ----------------------------------------------------------------
     * Description: Save the document from the selected file
     */
    private void saveDocumentAs() throws IllegalAccessException {
        final int returnVal = fileChooser.showSaveDialog(frame);

        if (returnVal == JFileChooser.APPROVE_OPTION) {
            file = fileChooser.getSelectedFile();
            storeDocument(file, documentLink);
        }
    }

    /* Function: showErrorMessage
     * Input: String inTitle, String inMessage
     * Output: N/A
     * ----------------------------------------------------------------
     * Description: Print out the error message and show it using the JOptionPane 
     */
    private void showErrorMessage(final String inTitle, final String inMessage) {
        System.out.println(inMessage);
        JOptionPane.showMessageDialog(frame, inMessage, inTitle, JOptionPane.ERROR_MESSAGE);
    }

    /* Function: storeDocument
     * Input: File inFile, GuiDocumentLink inLink
     * Output: N/A
     * ----------------------------------------------------------------
     * Description: Convert the inLink to a document and store it at the
     *   file location
     */
    // Store the UI into the file specified by the instance variable "file"
    private void storeDocument(final File inFile, final GuiDocumentLink inLink) throws IllegalAccessException {
    	// Given the GuiDocumentLink, convert it to a document
        inLink.guiToDocument();
        
        // Get the document for the GuiDocumentLink, and store it at the inFile
        inLink.getDocument().store(inFile);
    }

    /* Function: handleExpection
     * Input: Exception inEx
     * Output: 
     * ----------------------------------------------------------------
     * Description: 
     */
    private void updateButtonEnablement(final Integer commonQuantileCount) {
        final SnpGenDocument document = getDocument();
        if ((document.getModelCount() <= 0) || (document.firstDataset.totalCount.value <= 0)) {
            editButton.setEnabled(false);
            deleteButton.setEnabled(false);
            generateButton.setEnabled(false);
        } else {
            final int[] selections = modelTable.getWhichSelectedModels();
            deleteButton.setEnabled(selections.length > 0);
            generateButton.setEnabled(commonQuantileCount != null);

            boolean editEnabled;
            if (selections.length != 1) {
                editEnabled = false;
                if (selections.length > 1) {
                    additiveButton.setEnabled(true);
                    heteroButton.setEnabled(true);
                } else {
                    additiveButton.setEnabled(false);
                    heteroButton.setEnabled(false);
                }
            } else {
                additiveButton.setEnabled(false);
                heteroButton.setEnabled(false);
                
                editEnabled = true; // first guess
                final SnpGenDocument.DocModel model = document.getModel(selections[0]);
                final Integer attributeCount = model.attributeCount.getInteger();
                if ((attributeCount == null) || !((attributeCount == 2) || (attributeCount == 3))) {
                    editEnabled = false;
                }
                final Integer quantileCount = model.getQuantileCountInModel();
                if (quantileCount != 1) {
                    editEnabled = false;
                }
            }
            editButton.setEnabled(editEnabled);
            activate = true;
            
        }
    }

    /* Function: updateQuantileCount
     * Input: Integer commonQuantileCount
     * Output: N/A
     * ----------------------------------------------------------------
     * Description: Update the quantileCountField based upon the common
     *  quantile count of our models
     */
    private void updateQuantileCount(final Integer commonQuantileCount) {
    	// Get the common quantile count of our input models
        final Integer quantileCount = getCommonQuantileCount();
        
        // If there is no quantileCount, update its field to 0
        if (quantileCount == null) {
            updateQuantileCountField(0);
        // Otherwise, update the field to the common quantile ocunt
        } else {
            updateQuantileCountField(quantileCount);
        }
    }

    /* Function: viewDataFile
     * Input: N/A
     * Output: N/A
     * ----------------------------------------------------------------
     * Description: Does nothing
     */
    private void viewDataFile() {
    }

    
    /* Function: main
     * Input: N/A
     * Output: N/A
     * ----------------------------------------------------------------
     * Description: Reads input arguments and performs GAMETES simulation
     */
    public static void main(final String[] args) {
    	// Initialize a new SnpGenDocument
        final SnpGenDocument doc = new SnpGenDocument(false);

        // Determine if the input document has arguments or not (i.e., if this commandline or GUI input?)
        boolean showGui = false;
        try {
            showGui = doc.parseArguments(args);
        } catch (final Exception e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
        
        // Boolean that tells us if the software is being run with normal arguments (no help and no GUI)
        final boolean runDocument = doc.runDocument;
        
        // If we're going into the GUI
        if (showGui) {
            System.out.println("Going into GUI!");
            // Initialize an empty dataset to contain required info
            doc.createFirstDataset();
            // Go into the createAndShowGui function (gets input from the UI and performs GAMETES)
            SnpGenMainWindow.createAndShowGui(doc);
        }

        // If we're not going into the GUI and we're not calling help, we run GAMETES based upon our input
        if (runDocument) {
            SnpGenMainWindow.runDocument(doc);
        }
    }

    /* Function: addComponent
     * Input: JComponent inComp, Class inClass, Container inContainer, int inColumnCount, String inLabel
     * Output: JComponent outComp
     * ----------------------------------------------------------------
     * Description: Adds an extra component when including labels for models or datasets
     * TODO: figure out exactly what this function does...
     */
    // WARNING: If you change the relationship of inClass to the class of the
    // output JComponent, be sure to update the createBacked<whatever> methods above.
    static protected JComponent addComponent(final Class<?> inClass, final Container inContainer,
            final int inColumnCount, final String inLabel) {
        return SnpGenMainWindow.addComponent(null, inClass, inContainer, inColumnCount, inLabel);
    }

    // WARNING: If you change the relationship of inClass to the class of the
    // output JComponent, be sure to update the createBacked<whatever> methods above.
    static protected JComponent addComponent(final JComponent inComp, final Class<?> inClass,
            final Container inContainer, final int inColumnCount, final String inLabel){
        JLabel label = null;
        JComponent outComp = null;
        JComponent holder;
        JPanel panel;

        if (inComp == null) {
            if (inClass.equals(JCheckBox.class)) {
                outComp = new JCheckBox();
            }
            if (inClass.equals(JTextField.class)) {
                outComp = new JTextField(inColumnCount);
            }
            if (inClass.equals(JLabel.class)) {
                outComp = new JLabel();
            }
        } else {
            outComp = inComp;
        }

        if (inLabel != null) {
            label = new JLabel(inLabel);
            label.setOpaque(true);
        }

        outComp.setOpaque(true);
        holder = outComp;
        if (inLabel != null) {
            panel = new JPanel();
            panel.add(label, BorderLayout.WEST);
            panel.add(outComp, BorderLayout.WEST);
            holder = panel;
        }
        if (inContainer != null) {
            inContainer.add(holder, BorderLayout.WEST);
        }
        return outComp;
    }

    
    /**
     * @wbp.parser.entryPoint
     */
    /* Function: createAndShowGui
     * Input: SnpGenDocument inSnpGenDocument
     * Output: N/A
     * ----------------------------------------------------------------
     * Description: Function to run GAMETES given input from the UI.
     *  Take the input SnpGenDocument, convert it to an
     *  object of type SnpGenMainWindow, and run the "createAndShowGUI"
     *  function
     */
    private static void createAndShowGui(final SnpGenDocument inSnpGenDocument){
    	// Create an object of class SnpGenMainWindow given our input document
        final SnpGenMainWindow snpGen = new SnpGenMainWindow(inSnpGenDocument);   
        
        // Schedule a job for the event-dispatching thread: creating and showing this application's GUI.
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run(){
            	// Call createAndShowGUI for the SnpGenMainWindow object
                snpGen.createAndShowGUI();
            }
        });
    }
    
    /* Function: runDocument
     * Input: SnpGenDocument inDocument
     * Output: N/A
     * ----------------------------------------------------------------
     * Description: Function to run GAMETES given input arguments.
     *  We verify that we have the required parameters to run GAMETES,
     *  then we create a new SnpGenSimulator whose document is our
     *  input document. Getting the quantile count and list of models
     *  from this document, we generate model tables, convert them
     *  into quantiles, and produce resulting datasets.
     */
    private static void runDocument(final SnpGenDocument inDocument) {
        Exception paramError;
        try {
        	// If we don't have all the needed parameters, throw an error
            if ((paramError = inDocument.verifyAllNeededParameters()) != null) {
                throw paramError;
            }

            // Initialize a new SnpGenSimulator
            final SnpGenSimulator simulator = new SnpGenSimulator();
            
            // Set the simulator's SnpGenDocument to be "inDocument"
            simulator.setDocument(inDocument);
            
            // Get the desired quantile count and the list of models from the inDocument
            final int desiredQuantileCount = inDocument.rasQuantileCount.getInteger();
            final ArrayList<DocModel> modelList = inDocument.modelList;
            
            // Generate models based upon the model list and the quantile count
            final double[][] allTableScores = simulator.generateTablesForModels(modelList, desiredQuantileCount,
                    inDocument.rasPopulationCount.getInteger(), inDocument.rasTryCount.getInteger(), null);
            
            // Write tables and scores, combine the tables into quantiles, and generate the appropriate datasets
            simulator.writeTablesAndScoresToFile(modelList, allTableScores, desiredQuantileCount);
            simulator.combineModelTablesIntoQuantiles(modelList, inDocument.modelInputFiles);
            simulator.generateDatasets(null);
            
        } catch (final Exception ex) {
            System.err.println(ex.getMessage());
            ex.printStackTrace();
        }
    }

    
    //---------------------------------------------------------------------------------------------------------------

    
    // CLASSES
    public static abstract class Action extends AbstractAction {
        private static final long serialVersionUID = 1L;

        public Action(final String text, final ImageIcon icon) {
            super(text, icon);
        }

        public Action(final String text, final ImageIcon icon, final String desc, final Integer mnemonic,
                final KeyStroke accelerator) {
            this(text, icon);
            putValue(javax.swing.Action.SHORT_DESCRIPTION, desc);
            putValue(javax.swing.Action.MNEMONIC_KEY, mnemonic);
            putValue(javax.swing.Action.ACCELERATOR_KEY, accelerator);
        }
    }

    public class CaseControlPanel extends JPanel implements FocusListener, ChangeListener {
        private static final long serialVersionUID = 1L;
        BackedTextField caseCountField;
        BackedTextField controlCountField;
        BackedCheckBox fairAndBalancedCheckBox;
        boolean isBalanced;
        BackedLabel sampleSizeField;
        BackedLabel caseProportionField;

        public CaseControlPanel() {
            create();
        }

        @Override
        public void focusGained(final FocusEvent e) {

        }

        @Override
        public void focusLost(final FocusEvent e) {
            try {
                final int caseCount = Integer.parseInt(caseCountField.getText());
                if (getDocument().caseControlRatioBalanced.getBoolean()) {
                    controlCountField.setText(String.valueOf(caseCount));
                }
                final int controlCount = Integer.parseInt(controlCountField.getText());
                final int totalCount = caseCount + controlCount;
                getDocument().firstDataset.totalCount.setValue(totalCount);
                getDocument().firstDataset.caseProportion.setValue(caseCount / (double) totalCount);
                updateGuiToData();
            } catch (final NumberFormatException ex) {

            }
        }

        @Override
        public void stateChanged(final ChangeEvent e) {
            if (e.getSource().equals(fairAndBalancedCheckBox)) {
                updateFairAndBalancedCheckBox();
            }
        }

        public void updateGuiToData() {
            try {
                caseCountField.setText(String.valueOf(getDocument().firstDataset.getCaseCount()));
                controlCountField.setText(String.valueOf(getDocument().firstDataset.getControlCount()));
                sampleSizeField.setText(
                        SnpGenDocument.kDecimalFormatCommaInteger.format(getDocument().firstDataset.totalCount.value));
                caseProportionField.setText(SnpGenDocument.kDecimalFormatFourDecimals
                        .format(getDocument().firstDataset.caseProportion.value));
            } catch (final NumberFormatException ex) {
                sampleSizeField.setText("");
                caseProportionField.setText("");
            }
        }

        private void create() {
            setAlignmentY(Component.TOP_ALIGNMENT);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            fairAndBalancedCheckBox = addBackedBoolean(this, getDocument().caseControlRatioBalanced, 3,
                    "Balanced case/control ratio"); // Whether
            fairAndBalancedCheckBox.addChangeListener(this);
            isBalanced = false;

            if ((getDocument().caseControlRatioBalanced != null)
                    && (getDocument().caseControlRatioBalanced.getBoolean() != null)) {
                isBalanced = getDocument().caseControlRatioBalanced.getBoolean();
            }

            final JPanel caseControlParameters = new JPanel();
            caseCountField = addBackedInteger(caseControlParameters, new DocInteger(), 3, "Number of cases");
            caseCountField.setText(SnpGenDocument.kDefaultCaseCount.toString());
            caseCountField.addFocusListener(this);
            controlCountField = addBackedInteger(caseControlParameters, new DocInteger(), 3, "Number of controls");
            controlCountField.setText(SnpGenDocument.kDefaultControlCount.toString());
            controlCountField.addFocusListener(this);
            add(caseControlParameters, BorderLayout.CENTER);
            sampleSizeField = addBackedLabel(this, getDocument().firstDataset.totalCount, 3, "Total sample size:");
            caseProportionField = addBackedLabel(this, getDocument().firstDataset.caseProportion, 3,
                    "Case proportion:");
            updateGuiToData();
        }

        private void updateFairAndBalancedCheckBox() {
            try {
                fairAndBalancedCheckBox.store();
            } catch (final IllegalAccessException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            isBalanced = getDocument().caseControlRatioBalanced.getBoolean().booleanValue();
            if (isBalanced) {
                final int currentCaseCount = (int) (getDocument().firstDataset.caseProportion.value
                        * getDocument().firstDataset.totalCount.value);
                getDocument().firstDataset.totalCount.setValue(2 * currentCaseCount);
                getDocument().firstDataset.caseProportion.setValue(0.5);
            }
            controlCountField.setEnabled(!isBalanced);
            updateGuiToData();
        }
    }
    
    public static class CreateModelAction extends SnpGenAction {
        private static final long serialVersionUID = 1L;

        public CreateModelAction(final SnpGenMainWindow inWindow) {
            super(inWindow, "Create Model", null, "Create a model and edit it", KeyEvent.VK_C,
                    KeyStroke.getKeyStroke(KeyEvent.VK_C, ActionEvent.ALT_MASK));
        }

        @Override
        public void actionPerformed(final ActionEvent e) {
            try {
                mSnpGen.createSnpModel();
            } catch (final IOException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
        }
    }

    // Define the overall Dataset Construction Panel, with non-predictive attributes, dataset properties, and
    //    replicate count panel info
    public class DatasetPanel extends JPanel {

        private static final long serialVersionUID = 1L;

        public DatasetPanel() {
            setBorder(BorderFactory.createTitledBorder("Dataset Construction"));
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            
            add(new NonPredictiveAttributesPanel());

            // Initialize the EndpointTypePanel first and THEN add the object
            EndpointTypePanel endPointTypePanelYeet = new EndpointTypePanel();
            add(endPointTypePanelYeet);
            //add(new EndpointTypePanel());
            

            datasetControlPanel = new DatasetReplicateCountPanel();
            add(datasetControlPanel, BorderLayout.CENTER);
        }
    }

    // Define the bottom dataset replication count panel
    public class DatasetReplicateCountPanel extends JPanel implements FocusListener {
        private static final long serialVersionUID = 1L;
        BackedTextField replicateCountField;
        JLabel datasetCountField;

        public DatasetReplicateCountPanel() {
            create();
        }

        @Override
        public void focusGained(final FocusEvent e) {
        }

        @Override
        public void focusLost(final FocusEvent e) {
            updateDatasetCountField();
        }

        public void updateDatasetCountField() {
            if (desiredQuantileCount > 0) {
                final Integer size = (Integer.parseInt(replicateCountField.getText()) * desiredQuantileCount);
                datasetCountField.setText(size.toString());
            } else {
                datasetCountField.setText("--");
            }
        }

        private void create() {
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));

            replicateCountField = addBackedInteger(this, getDocument().firstDataset.replicateCount, 3,
                    "Number of replicates"); // The number of random seeds
            replicateCountField.setText(SnpGenDocument.kDefaultReplicateCount.toString());
            replicateCountField.addFocusListener(this);

            // The total number of datasets
            datasetCountField = (JLabel) addComponent(JLabel.class, this, null, 0, "Total number of datasets:");
            updateDatasetCountField();
        }
    }

    
    public static class EditModelAction extends SnpGenAction {
        private static final long serialVersionUID = 1L;

        public EditModelAction(final SnpGenMainWindow inWindow) {
            super(inWindow, "Edit Model", null, "Edit an existing model", KeyEvent.VK_E,
                    KeyStroke.getKeyStroke(KeyEvent.VK_E, ActionEvent.ALT_MASK));
        }

        @Override
        public void actionPerformed(final ActionEvent e) {
            try {
                mSnpGen.editSnpModel();
            } catch (final IOException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
        }
    }

    // Dataset Properties Panel
    public class EndpointTypePanel extends JPanel {
        private static final long serialVersionUID = 1L;
        
        JRadioButton binaryClassButton;
        JRadioButton quantitativeTraitButton;
        
        // Initialize the panels that correspond to binary and quantitative data
        final CardLayout binaryCardLayout = new CardLayout();
        final JPanel binaryCards = new JPanel(binaryCardLayout);
        FixedSampleNumberCaseControlPanel fixedSampleNumberCaseControlPanel;
        CaseControlPanel caseControlPanel;
        QuantitativePanel quantitativePanel;

        // Define the endpoint type panel
        public EndpointTypePanel() {
            // Define our border and its layout, corresponding to the dataset properties
            setBorder(BorderFactory.createTitledBorder("Dataset Properties"));
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            
            // Define panel corresponding to choices for binary trait vs quantitative traits
            JPanel endPointButtonChoicesPanel;
            endPointButtonChoicesPanel = new JPanel();

            // Define panel corresponding to choices for additive data vs heterogeneous data
            JPanel comboDataButtonChoicesPanel;
            comboDataButtonChoicesPanel = new JPanel();
            
            // Create the binary class/quantitative trait buttons
            binaryClassButton = new JRadioButton("Binary Class");
            quantitativeTraitButton = new JRadioButton("Quantitative Trait");
            binaryClassButton.setSelected(true);
            binaryClassButton.setEnabled(true);
            quantitativeTraitButton.setEnabled(true);
            
            // Create the additive and hetero data radio buttons
            additiveButton = new JRadioButton("Additive Data");
            heteroButton = new JRadioButton("Heterogenous Data");
            heteroLabelsCheckbox = new JCheckBox("Add model labels for Heterogeneous Data"); 
            additiveButton.setSelected(true);
            additiveButton.setEnabled(false);
            heteroButton.setEnabled(false);
            heteroLabelsCheckbox.setEnabled(false);
            
            // Group the endPoint radio buttons together
            final ButtonGroup endPointGroup = new ButtonGroup();
            endPointGroup.add(binaryClassButton);
            endPointGroup.add(quantitativeTraitButton);
            
            // Group the comboData radio buttons together
            final ButtonGroup comboDataGroup = new ButtonGroup();
            comboDataGroup.add(additiveButton);
            comboDataGroup.add(heteroButton);
            
            // Add our buttons to our panels and add our panels to the layout
            endPointButtonChoicesPanel.add(binaryClassButton, BorderLayout.WEST);
            endPointButtonChoicesPanel.add(quantitativeTraitButton, BorderLayout.EAST);
            comboDataButtonChoicesPanel.add(additiveButton, BorderLayout.WEST);
            comboDataButtonChoicesPanel.add(heteroButton, BorderLayout.EAST);
            comboDataButtonChoicesPanel.add(heteroLabelsCheckbox, BorderLayout.SOUTH);
            
            add(comboDataButtonChoicesPanel, BorderLayout.CENTER);
            add(endPointButtonChoicesPanel, BorderLayout.CENTER);
            
            // Define an endpointCardLayout, as well as a JPanel for it
            final CardLayout endpointCardLayout = new CardLayout();
            final JPanel endpointTypeCards = new JPanel(endpointCardLayout);

            // Define the panel that corresponds to allowing users to specify both cases and controls
            caseControlPanel = new CaseControlPanel();
            binaryCards.add(caseControlPanel, SnpGenMainWindow.FIXED_OR_VARIABLE_NUMBER_OF_SAMPLES.VARIABLE.toString());
            
            // Define the panel that corresponds to fixed control given x number of cases
            fixedSampleNumberCaseControlPanel = new FixedSampleNumberCaseControlPanel();
            binaryCards.add(fixedSampleNumberCaseControlPanel, SnpGenMainWindow.FIXED_OR_VARIABLE_NUMBER_OF_SAMPLES.FIXED.toString());
           
            setFixedOrVariableNumberOfSamples(SnpGenMainWindow.FIXED_OR_VARIABLE_NUMBER_OF_SAMPLES.VARIABLE);
            endpointTypeCards.add(binaryCards, SnpGenMainWindow.ENDPOINT_TYPES.BINARY_CLASS.toString());

            // Define the panel that allows users to just do a quantitative trait, w sample number and std dev
            quantitativePanel = new QuantitativePanel();
            endpointTypeCards.add(quantitativePanel, SnpGenMainWindow.ENDPOINT_TYPES.QUANTITATIVE_TRAIT.toString());
            add(endpointTypeCards, BorderLayout.SOUTH);

            // Register listeners for the radio buttons          
            // Include an ActionListener to report the output from the binaryClass button
            binaryClassButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(final ActionEvent e) {
                    // If the binary class button is picked, show the relevant values and update the GUI based upon the
                    //    current data
                    endpointCardLayout.show(endpointTypeCards, SnpGenMainWindow.ENDPOINT_TYPES.BINARY_CLASS.toString());
                    getDocument().firstDataset.createContinuousEndpoints.setValue(Boolean.FALSE);
                    fixedSampleNumberCaseControlPanel.updateGuiToData();
                }
            });

            // Include an ActionListener to report the output from the quantitativeTrait button
            quantitativeTraitButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(final ActionEvent e) {
                    // If the quantitative trait button is picked, show the relevant values and update the GUI based upon
                    //    the current data
                    endpointCardLayout.show(endpointTypeCards, SnpGenMainWindow.ENDPOINT_TYPES.QUANTITATIVE_TRAIT.toString());
                    getDocument().firstDataset.createContinuousEndpoints.setValue(Boolean.TRUE);
                    quantitativePanel.updateGuiToData();
                }
            });
            
            // Include an ActionListener to update the document given the heteroButton button
            heteroButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(final ActionEvent e) {
                    heteroLabelsCheckbox.setEnabled(true);                    
                    getDocument().firstDataset.multipleModelDatasetType.setValue(MIXED_MODEL_DATASET_TYPE.heterogeneous);
                }
            });

            // Include an ActionListener to update the document given the additiveButton button
            additiveButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(final ActionEvent e) {
                    heteroLabelsCheckbox.setEnabled(false);                    
                    getDocument().firstDataset.multipleModelDatasetType.setValue(MIXED_MODEL_DATASET_TYPE.hierarchical);
                }
            });
            
            // Adding hetero checkbox
            heteroLabelsCheckbox.addActionListener(new ActionListener() {
            	@Override
                public void actionPerformed(final ActionEvent e) {            		
            		// Use the "activate" boolean to make sure the checkbox can be turned on or off
            		if(activate) {
                        getDocument().firstDataset.heterogeneousLabelBoolean.setValue(Boolean.TRUE);
                        activate = false;
            		} else {
            			getDocument().firstDataset.heterogeneousLabelBoolean.setValue(Boolean.FALSE);
                        activate = true;
            		}
                }
            });
        }

        void setFixedOrVariableNumberOfSamples(final SnpGenMainWindow.FIXED_OR_VARIABLE_NUMBER_OF_SAMPLES fixedOrVariableSize) {
            binaryCardLayout.show(binaryCards, fixedOrVariableSize.toString());
        }

        
        void updateGuiToData() {
            fixedSampleNumberCaseControlPanel.updateGuiToData();
            caseControlPanel.updateGuiToData();
            quantitativePanel.updateGuiToData();
        }
    }

    public class FixedSampleNumberCaseControlPanel extends JPanel {
        private static final long serialVersionUID = 1L;
        BackedLabel caseCountField;
        BackedLabel controlCountField;
        BackedLabel sampleSizeField;
        BackedLabel caseProportionField;
        JSlider proportionSlider = new JSlider(SwingConstants.HORIZONTAL, 1, 99, 50);

        public FixedSampleNumberCaseControlPanel() {
            setAlignmentY(Component.TOP_ALIGNMENT);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            
            add(new JLabel("Move slider to set case control counts."));
            add(proportionSlider);
            proportionSlider.addChangeListener(new javax.swing.event.ChangeListener() {
                @Override
                public void stateChanged(final ChangeEvent e) {
                    updateGuiToData();
                }
            });
            
            caseProportionField = addBackedLabel(this, getDocument().firstDataset.caseProportion, 3, "Case proportion:");
            caseCountField = addBackedLabel(this, new DocInteger(), 3, "Number of cases:");
            controlCountField = addBackedLabel(this, new DocInteger(), 3, "Number of controls:");
            proportionSlider.setValue((int) (SnpGenDocument.kDefaultDatasetCaseProportion * 100));
        }

        void updateGuiToData() {
            final int percent = proportionSlider.getValue();

            final double caseProportion = percent / 100.0;
            getDocument().firstDataset.caseProportion.setValue(caseProportion);
            caseProportionField.setText(SnpGenDocument.kDecimalFormatFourDecimals.format(caseProportion));
            final int totalCount = getDocument().firstDataset.totalCount.value;
            final long caseCount = Math.round(totalCount * caseProportion);
            caseCountField.setText(String.valueOf(caseCount));
            controlCountField.setText(String.valueOf(totalCount - caseCount));
        }
    }

    public static class GenerateAction extends SnpGenAction {
        private static final long serialVersionUID = 1L;

        public GenerateAction(final SnpGenMainWindow inWindow) {
            super(inWindow, "Generate Datasets...", null, "Generate simulated values", KeyEvent.VK_G,
                    KeyStroke.getKeyStroke(KeyEvent.VK_G, ActionEvent.ALT_MASK));
        }

        @Override
        public void actionPerformed(final ActionEvent e) {
            mSnpGen.generateDatasets();
        }
    }

    public static class GenerateModelAction extends SnpGenAction {
        private static final long serialVersionUID = 1L;

        public GenerateModelAction(final SnpGenMainWindow inWindow) {
            super(inWindow, "Generate Model", null, "Generate a simulatedmodel", KeyEvent.VK_G,
                    KeyStroke.getKeyStroke(KeyEvent.VK_G, ActionEvent.ALT_MASK));
        }

        @Override
        public void actionPerformed(final ActionEvent e) {
            mSnpGen.generateSnpModel();
        }
    }

    public static class LoadModelAction extends SnpGenAction {
        private static final long serialVersionUID = 1L;

        public LoadModelAction(final SnpGenMainWindow inWindow) {
            super(inWindow, "Load Model", null, "Load a model", KeyEvent.VK_L,
                    KeyStroke.getKeyStroke(KeyEvent.VK_L, ActionEvent.ALT_MASK));
        }

        @Override
        public void actionPerformed(final ActionEvent e) {
            mSnpGen.loadModelFile();
        }
    }

    public static class NewAction extends SnpGenAction {
        private static final long serialVersionUID = 1L;

        public NewAction(final SnpGenMainWindow SnpGen) {
            super(SnpGen, "New", null, "Create a new file", KeyEvent.VK_N,
                    KeyStroke.getKeyStroke(KeyEvent.VK_N, ActionEvent.ALT_MASK));
        }

        @Override
        public void actionPerformed(final ActionEvent e) {
            try {
                mSnpGen.createSnpGenDocument();
            } catch (final IllegalAccessException iea) {
          }
        }
    }

    public class NoiseGenerator extends JPanel {
        private static final long serialVersionUID = 1L;

        public NoiseGenerator(final SnpGenDocument inDocument) {
            BackedTextField field;

            setAlignmentX(Component.CENTER_ALIGNMENT);
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            field = addBackedInteger(this, getDocument().firstDataset.totalAttributeCount, 3,
                    "Total number of attributes"); // The number of attributes
            field.setText(SnpGenDocument.kDefaultAtrributeCount.toString());

            final JPanel mafPanel = new JPanel();
            final JLabel alleleFrequencyLabel = new JLabel("Minor-allele-frequency range");
            alleleFrequencyLabel.setOpaque(true);
            mafPanel.add(alleleFrequencyLabel, BorderLayout.WEST);
            // Minimum allele frequency
            field = addBackedDouble(mafPanel, getDocument().firstDataset.alleleFrequencyMin, 3, null);
            field.setText(SnpGenDocument.kDefaultFrequencyMin.toString());
            // Maximum allele frequency
            field = addBackedDouble(mafPanel, getDocument().firstDataset.alleleFrequencyMax, 3, null);
            field.setText(SnpGenDocument.kDefaultFrequencyMax.toString());
            add(mafPanel, BorderLayout.EAST);
        }
    }

    public class NoiseReader extends JPanel {
        private static final long serialVersionUID = 1L;
        JLabel filenameLabel;
        JLabel attributeCountLabel;
        JLabel instanceCountLabel;
        Integer instanceCount = null;

        public NoiseReader() {
            getDocument();

            setAlignmentY(Component.TOP_ALIGNMENT);
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));

            final JButton loadButton = new JButton(new LoadDataFileAction(SnpGenMainWindow.this));
            add(loadButton);

            filenameLabel = (JLabel) SnpGenMainWindow.addComponent(JLabel.class, this, 0, "File:");
            attributeCountLabel = (JLabel) SnpGenMainWindow.addComponent(JLabel.class, this, 0,
                    "Number of attributes:"); // The total number of attributes
            instanceCountLabel = (JLabel) SnpGenMainWindow.addComponent(JLabel.class, this, 0,
                    "Total number of instances:"); // The total number of instances
        }

        public void reset() {
            attributeCountLabel.setText("0");
            filenameLabel.setText("(none)");
            setInstanceCount(0);
            getDocument().noiseInputFile = null;

        }

        private void loadDataFile(final SnpGenMainWindow inWindow) {
            File file = null;

            final int returnVal = fileChooser.showOpenDialog(inWindow.frame);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                file = fileChooser.getSelectedFile();
                filenameLabel.setText(file.getName());
                getDocument().noiseInputFile = file;
                int[][] dataArray = null;
                try {
                    dataArray = SnpGenSimulator.parseDataInputFile(file, null);
                } catch (final InputException ie) {
                } catch (final IOException ioe) {
                }
                if (dataArray != null) {
                    attributeCountLabel.setText(((Integer) dataArray[0].length).toString());
                    setInstanceCount(dataArray.length);
                }
            }
            endpointTypePanel.updateGuiToData();
            updateSelection();
        }

        private void setInstanceCount(final Integer inInstanceCount) {
            instanceCount = inInstanceCount;
            getDocument().firstDataset.totalCount.setInteger(instanceCount);
            instanceCountLabel.setText(inInstanceCount.toString());
        }

        public class LoadDataFileAction extends SnpGenAction {
            private static final long serialVersionUID = 1L;

            public LoadDataFileAction(final SnpGenMainWindow inWindow) {
                super(inWindow, "Load SNP file", null, "Load a SNP file", KeyEvent.VK_L,
                        KeyStroke.getKeyStroke(KeyEvent.VK_L, ActionEvent.ALT_MASK));
            }

            @Override
            public void actionPerformed(final ActionEvent e) {
                loadDataFile(mSnpGen);
            }
        }

        public class ViewDataFileAction extends SnpGenAction {
            private static final long serialVersionUID = 1L;

            public ViewDataFileAction(final SnpGenMainWindow inWindow) {
                super(inWindow, "View SNP file", null, "View a SNP file", KeyEvent.VK_V,
                        KeyStroke.getKeyStroke(KeyEvent.VK_V, ActionEvent.ALT_MASK));
            }

            @Override
            public void actionPerformed(final ActionEvent e) {
                mSnpGen.viewDataFile();
            }
        }
    }

    // Non-predictive Attributes Panel
    public class NonPredictiveAttributesPanel extends JPanel implements ActionListener {
        private static final long serialVersionUID = 1L;
        JRadioButton generateNoiseButton;
        JRadioButton readNoiseButton;
        JRadioButton additiveDataButton;
        JRadioButton heteroDataButton;
        NoiseGenerator generator;
        NoiseReader reader;

        public NonPredictiveAttributesPanel() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createTitledBorder("Non-predictive Attributes"));
            setAlignmentX(Component.CENTER_ALIGNMENT);

            JPanel radioControlPanel;
            radioControlPanel = new JPanel();

            // Create the radio buttons.
            generateNoiseButton = new JRadioButton("Generate");
            generateNoiseButton.setSelected(true);
            readNoiseButton = new JRadioButton("Read from file");

            // Group the radio buttons.
            final ButtonGroup group = new ButtonGroup();
            group.add(generateNoiseButton);
            group.add(readNoiseButton);

            // Register a listener for the radio buttons.
            generateNoiseButton.addActionListener(this);
            readNoiseButton.addActionListener(this);

            radioControlPanel.add(generateNoiseButton);
            radioControlPanel.add(readNoiseButton);
            radioControlPanel.add(generateNoiseButton);

            add(radioControlPanel);

            generator = new NoiseGenerator(getDocument());
            generator.setVisible(true);
            add(generator);

            reader = new NoiseReader();
            reader.setVisible(false);
            add(reader);
        }

        // Given the input from "Read from file" vs. "Generate" in the Non-predictive Attributes section,
        //      perform a certain action. The input is "ActionEvent e"
        @Override
        public void actionPerformed(final ActionEvent e) {
            // If e correspond to "generate", then show the required info
            if (e.getSource().equals(generateNoiseButton)) {
                generator.setVisible(true);
                reader.setVisible(false);
                getDocument().firstDataset.totalCount
                        .setValue(SnpGenDocument.kDefaultCaseCount + SnpGenDocument.kDefaultControlCount);
                endpointTypePanel.setFixedOrVariableNumberOfSamples(FIXED_OR_VARIABLE_NUMBER_OF_SAMPLES.VARIABLE);
            }
            
            // Otherwise, if e corresponds to reading from file, show the correct info for it
            else if (e.getSource().equals(readNoiseButton)) {
                reader.reset();
                generator.setVisible(false);
                reader.setVisible(true);
                endpointTypePanel.setFixedOrVariableNumberOfSamples(FIXED_OR_VARIABLE_NUMBER_OF_SAMPLES.FIXED);
            }
            
            endpointTypePanel.updateGuiToData();
            updateSelection();
        }
    }

    public static class OpenAction extends SnpGenAction {
        private static final long serialVersionUID = 1L;

        public OpenAction(final SnpGenMainWindow SnpGen) {
            super(SnpGen, "Open", null, "Open an existing file", KeyEvent.VK_O,
                    KeyStroke.getKeyStroke(KeyEvent.VK_O, ActionEvent.ALT_MASK));
        }

        @Override
        public void actionPerformed(final ActionEvent e) {
            try {
                mSnpGen.openDocument();
            } catch (final IllegalAccessException iea) {
            }
        }
    }

    public class ProgressDialog implements SnpGenSimulator.ProgressHandler {
        JDialog dialog;
        JProgressBar progressBar;

        public ProgressDialog(final String inLabel) {
            dialog = new JDialog(frame, "Progress", true);
            progressBar = new JProgressBar(0, 500);
            dialog.getContentPane().add(BorderLayout.CENTER, progressBar);
            progressBar.setVisible(true);
            dialog.getContentPane().add(BorderLayout.NORTH, new JLabel(inLabel));
            dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            dialog.setSize(300, 75);
            dialog.setLocationRelativeTo(frame);
        }

        @Override
        public void setMaximum(final int inMax) {
            progressBar.setMaximum(inMax);
        }

        @Override
        public void setValue(final int inMax) {
            progressBar.setValue(inMax);
        }

        public void setVisible(final boolean inVisibility) {
            dialog.setVisible(inVisibility);
        }
    }

    public class QuantitativePanel extends JPanel implements FocusListener {
        private static final long serialVersionUID = 1L;
        BackedTextField totalCountField;
        BackedTextField standardDeviationField;

        public QuantitativePanel() {
            // TODO figure out how to get items aligned in vertical center
            totalCountField = addBackedInteger(this, getDocument().firstDataset.totalCount, 3,
                    "Total number of samples:");
            totalCountField.setText(SnpGenDocument.kDefaultTotalCount.toString());
            totalCountField.addFocusListener(this);
            standardDeviationField = addBackedDouble(this,
                    getDocument().firstDataset.continuousEndpointsStandardDeviation, 3, "Standard Deviation:");
            standardDeviationField.setText(SnpGenDocument.kDefaultContinuousEndpointsStandardDeviation.toString());
            standardDeviationField.addFocusListener(this);
        }

        @Override
        public void focusGained(final FocusEvent e) {
            // TODO Auto-generated method stub

        }

        @Override
        public void focusLost(final FocusEvent e) {
            try {
                final double standardDeviation = Double.parseDouble(standardDeviationField.getText());
                getDocument().firstDataset.continuousEndpointsStandardDeviation.setValue(standardDeviation);
                final int totalCount = Integer.parseInt(totalCountField.getText());
                getDocument().firstDataset.totalCount.setValue(totalCount);
            } catch (final NumberFormatException ex) {

            }
        }

        public void updateGuiToData() {
            totalCountField.setText(getDocument().firstDataset.totalCount.toString());
        }
    }

    public static class RemoveModelAction extends SnpGenAction {
        private static final long serialVersionUID = 1L;

        public RemoveModelAction(final SnpGenMainWindow inWindow) {
            super(inWindow, "Delete Model", null, "Delete a model", KeyEvent.VK_D,
                    KeyStroke.getKeyStroke(KeyEvent.VK_D, ActionEvent.ALT_MASK));
        }

        @Override
        public void actionPerformed(final ActionEvent e) {
            mSnpGen.removeSnpModel();
        }
    }

    public static class SaveAction extends SnpGenAction {
        private static final long serialVersionUID = 1L;

        public SaveAction(final SnpGenMainWindow SnpGen) {
            super(SnpGen, "Save", null, "Save the current file", KeyEvent.VK_S,
                    KeyStroke.getKeyStroke(KeyEvent.VK_O, ActionEvent.ALT_MASK));
        }

        @Override
        public void actionPerformed(final ActionEvent e) {
            try {
                mSnpGen.saveDocument();
            } catch (final IllegalAccessException iea) {
            }
        }
    }

    public static class SaveAsAction extends SnpGenAction {
        private static final long serialVersionUID = 1L;

        public SaveAsAction(final SnpGenMainWindow SnpGen) {
            super(SnpGen, "SaveAs", null, "Save the current file to a new location", KeyEvent.VK_S,
                    KeyStroke.getKeyStroke(KeyEvent.VK_O, ActionEvent.ALT_MASK | ActionEvent.SHIFT_MASK));
        }

        @Override
        public void actionPerformed(final ActionEvent e) {
            try {
                mSnpGen.saveDocumentAs();
            } catch (final IllegalAccessException iea) {
            }
        }
    }

    public static abstract class SnpGenAction extends Action {
        private static final long serialVersionUID = 1L;
        protected SnpGenMainWindow mSnpGen;

        public SnpGenAction(final SnpGenMainWindow SnpGen, final String text, final ImageIcon icon, final String desc,
                final Integer mnemonic, final KeyStroke accelerator) {
            super(text, icon, desc, mnemonic, accelerator);
            mSnpGen = SnpGen;
        }
    }

    private static class BackedCheckBox extends JCheckBox implements BackedComponent {

        private static final long serialVersionUID = 1L;
        private final DocBoolean docField;

        public BackedCheckBox(final DocBoolean inDocField) {
            super();
            assert inDocField != null : getClass().getName() + " cannot be passed a null DocMember";
            docField = inDocField;
        }

        @Override
        public void load() throws IllegalAccessException {
            setSelected(docField.getValue());
        }

        @Override
        public void store() throws IllegalAccessException {
            if (docField != null) {
                docField.setValue(isSelected());
            }
        }
    }

    private static interface BackedComponent {
        public void load() throws IllegalAccessException;

        public void store() throws IllegalAccessException;
    }

    private static class BackedLabel extends JLabel implements BackedComponent {

        private static final long serialVersionUID = 1L;
        private final DocMember<?> docField;

        public BackedLabel(final DocMember<?> inDocField) {
            super();
            assert inDocField != null : getClass().getName() + " cannot be passed a null DocMember";
            docField = inDocField;
        }

        @Override
        public void load() throws IllegalAccessException {
            setText(docField.toString());
        }

        @Override
        public void store() throws IllegalAccessException {
            docField.setValueFromString(getText());
        }
    }

    private static class BackedModelUi extends JPanel // implements
    // BackedComponent
    {
        private static final long serialVersionUID = 1L;
        public int attributeCount;
        public BackedTextField modelIdUi;
        public BackedTextField attributeCountUi;
        public BackedTextField heritabilityUi;
        public JPanel attributeNamePanel;
        public ArrayList<BackedTextField> attributeNameUiList;
        public JPanel attributeAlleleFrequencyPanel;
        public ArrayList<BackedTextField> attributeAlleleFrequencyUiList;

        public BackedModelUi() {
        }
    }

    private static class BackedTextField extends JTextField implements BackedComponent {

        private static final long serialVersionUID = 1L;
        private final DocMember<?> docField;

        public BackedTextField(final int inColumnCount, final DocMember<?> inDocField) {
            super(inColumnCount);
            assert inDocField != null : getClass().getName() + " cannot be passed a null DocMember";
            docField = inDocField;
        }

        @Override
        public void load() throws IllegalAccessException {
            setText(docField.toString());
        }

        @Override
        public void store() throws IllegalAccessException {
            docField.setValueFromString(getText());
        }
    }
    
    private static class GuiDocumentLink {
        private SnpGenDocument document;
        private final ArrayList<BackedComponent> backedComponents;

        public GuiDocumentLink() {
            backedComponents = new ArrayList<BackedComponent>();
        }

        public void addBackedComponent(final BackedComponent inBackedComponent) {
            backedComponents.add(inBackedComponent);
        }

        public void documentToGui() throws IllegalAccessException {
            for(final BackedComponent comp : backedComponents) {
                comp.load();
            }
        }

        public SnpGenDocument getDocument() {
            return document;
        }

        public void guiToDocument() throws IllegalAccessException {
            for (final BackedComponent comp : backedComponents) {
                // only store if the field is showing. This prevents caseProportion from being written into twice
                if (((JComponent) comp).isShowing()) {
                    comp.store();
                }
            }
        }

        public void setDocument(final SnpGenDocument document) {
            this.document = document;
        }
    }

    //---------------------------------------------------------------------------------------------------------------
    
    // STATIC ENUMS
    private static enum ENDPOINT_TYPES {
        QUANTITATIVE_TRAIT, BINARY_CLASS
    }

    private static enum DATA_TYPES{
        ADDITIVE, HETEROGENOUS
    }

    private static enum FIXED_OR_VARIABLE_NUMBER_OF_SAMPLES {
        VARIABLE, FIXED
    }

    private static enum noiseAttributeTypes{
        GENERATED, FROM_DISK
    }
}
