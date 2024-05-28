package org.epistasis.snpgen.ui;

/*
 * Portions copyright (c) 1995, 2008, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle or the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener; //property change stuff
import java.text.NumberFormat;
import java.text.ParseException;

import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.DefaultTableModel;

import org.epistasis.snpgen.document.SnpGenDocument;
import org.epistasis.snpgen.ui.SnpGenMainWindow.Action;

//import org.epistasis.snpgen.ui.SnpGenMainWindow.GenerateAction;

class GenerateModelDialog extends JDialog implements ActionListener, PropertyChangeListener, FocusListener, ChangeListener {
	private static final long serialVersionUID = 1L;
	private final int firstAttributeNumber;
	private boolean saved;
	NumberFormat integerNumberFormat;
	NumberFormat doubleNumberFormat;
	private JRadioButton edmButton;
	private JRadioButton oddsButton;
	private JFormattedTextField quantileCountTextField;
	private JFormattedTextField quantilePopulationTextField;
	private JFormattedTextField attributeCountTextField;
	private JFormattedTextField heritabilityTextField;
	private JCheckBox prevalenceCheckBox;
	private JFormattedTextField prevalenceTextField;
	DefaultTableModel tableModel;

	public GenerateModelDialog(final Frame aFrame, final int inNextModelNumber, final int inNextSnpNumber) {
		super(aFrame, true);
		firstAttributeNumber = inNextSnpNumber;
		integerNumberFormat = NumberFormat.getIntegerInstance();
		doubleNumberFormat = NumberFormat.getNumberInstance();

		setContentPane(createContentPane());

		// Ensure the text field always gets the first focus.
		addComponentListener(new ComponentAdapter() {
			@Override
			public void componentShown(final ComponentEvent ce) {
				// textField.requestFocusInWindow();
			}
		});

		// Register an event handler that puts the text into the option pane.
		// textField.addActionListener(this);

		// Register an event handler that reacts to option pane state changes.
		// optionPane.addPropertyChangeListener(this);
	}

	/** This method handles events for the text field. */
	@Override
	public void actionPerformed(final ActionEvent e) {
		// optionPane.setValue(btnString1);
	}

	/** This method clears the dialog and hides it. */
	public void clearAndHide() {
		// textField.setText(null);
		setVisible(false);
	}

	public Container createContentPane() {
		JPanel contentPane; // The content pane of the window
		JPanel parameterPane; // The top pane
		JScrollPane tableScrollPane; // The bottom pane

		contentPane = new JPanel();
		contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.Y_AXIS));
		contentPane.setOpaque(true);

		parameterPane = new JPanel();
		parameterPane.setLayout(new BoxLayout(parameterPane, BoxLayout.Y_AXIS));
		parameterPane.setAlignmentX(Component.CENTER_ALIGNMENT);

		final JPanel attributePane = new JPanel();
		attributePane.setLayout(new BoxLayout(attributePane, BoxLayout.X_AXIS));

		final LabelledTextField attributeCountPane = new LabelledTextField("Number of attributes", 4, integerNumberFormat);
		attributeCountTextField = attributeCountPane.textField;
		attributeCountTextField.addFocusListener(this);
		attributePane.add(attributeCountPane);

		final LabelledTextField heritabilityPane = new LabelledTextField("Heritability", 4, doubleNumberFormat);
		heritabilityTextField = heritabilityPane.textField;
		heritabilityPane.textField.setValue(0.2);
		attributePane.add(heritabilityPane);

		final JPanel prevalencePane = new JPanel();
		prevalenceCheckBox = new JCheckBox();
		prevalenceCheckBox.addChangeListener(this);
		final LabelledTextField prevalenceLabelledField = new LabelledTextField("Prevalence", 4, doubleNumberFormat);
		prevalenceTextField = prevalenceLabelledField.textField;
		prevalenceTextField.setEnabled(prevalenceCheckBox.isSelected());
		// prevalencePane.textField.setValue("0.1");
		prevalencePane.add(prevalenceCheckBox);
		prevalencePane.add(prevalenceLabelledField);
		attributePane.add(prevalencePane);

		parameterPane.add(attributePane);

		final JPanel variantPane = new JPanel();
		variantPane.setLayout(new BoxLayout(variantPane, BoxLayout.X_AXIS));
		variantPane.setAlignmentY(Component.CENTER_ALIGNMENT);

		// Create the radio buttons.
		edmButton = new JRadioButton("EDM");
		edmButton.setSelected(true);
		oddsButton = new JRadioButton("Odds ratio");

		// Group the radio buttons.
		final ButtonGroup group = new ButtonGroup();
		group.add(edmButton);
		group.add(oddsButton);

		JPanel radioControlPanel;
		radioControlPanel = new JPanel();
		radioControlPanel.add(new JLabel("Quantiles:"));
		radioControlPanel.add(edmButton);
		radioControlPanel.add(oddsButton);

		variantPane.add(radioControlPanel);

		final LabelledTextField countPane = new LabelledTextField("Quantile count", 8, integerNumberFormat);
		quantileCountTextField = countPane.textField;
		// quantileCountTextField.setText(SnpGenDocument.kDefaultRasQuantileCountString);
		countPane.textField.setValue(2);
		variantPane.add(countPane);

		final LabelledTextField populationPane = new LabelledTextField("Quantile population size", 8, integerNumberFormat);
		quantilePopulationTextField = populationPane.textField;
		quantilePopulationTextField.setText(SnpGenDocument.kDefaultRasPopulationCount.toString());
		// populationPane.textField.setValue("100");
		variantPane.add(populationPane);

		parameterPane.add(variantPane);

		tableModel = new DefaultTableModel();
		// JTable table = new JTable(tableModel);

		tableModel.addColumn("SNP");
		tableModel.addColumn("Minor allele frequency");
		final ModelCreationTable table = new ModelCreationTable(tableModel);

		final int attributeCount = 2;
		// Create two rows in the table:
		pushDefaultTableRow();
		pushDefaultTableRow();

		tableScrollPane = new JScrollPane(table);
		table.setFillsViewportHeight(true);

		attributeCountPane.textField.setText(((Integer) attributeCount).toString());

		contentPane.add(parameterPane, BorderLayout.CENTER);
		contentPane.add(tableScrollPane, BorderLayout.CENTER);

		// Create the command section at the bottom of the frame:
		JPanel commandPanel;
		commandPanel = new JPanel();
		commandPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
		final JButton saveButton = new JButton(new SaveModelAction());
		commandPanel.add(saveButton, BorderLayout.CENTER);
		final JButton cancelButton = new JButton(new CancelAction());
		commandPanel.add(cancelButton, BorderLayout.CENTER);
		contentPane.add(commandPanel);

		return contentPane;
	}

	@Override
	public void focusGained(final FocusEvent e) {

	}

	@Override
	public void focusLost(final FocusEvent e) {
		final Component where = e.getComponent();
		if (where == attributeCountTextField) {
			int attrCount = 0;
			try {
				attributeCountTextField.commitEdit();
			} catch (final ParseException pe) {
				// Ignore the exception and just use attributeCountTextField's
				// best guess, below.
			}
			try {
				// attrCount =
				// Integer.parseInt(attributeCountTextField.getValue().toString());
				attrCount = integerNumberFormat.parse(attributeCountTextField.getValue().toString()).intValue();
			} catch (final ParseException pe) {
				// TODO: Should never get here, because the field is a
				// JFormattedTextField with a NumberFormat.getIntegerInstance().
			}
			resetAttributeCount(attrCount);
		}
	}

	public int getAttributeCount() {
		return tableModel.getRowCount();
	}

	public int getAttributeCountFieldValue() {
		int attrCount = 0;
		try {
			// attrCount = Integer.parseInt(attributeCountTextField.getText());
			attrCount = integerNumberFormat.parse(attributeCountTextField.getText()).intValue();
		} catch (final ParseException pe) {
			// TODO: Should never get here, because the field is a
			// JFormattedTextField with a NumberFormat.getIntegerInstance().
		}
		return attrCount;
	}

	public double[] getAttributeMinorAlleleFrequencies() {
		final int attributeCount = tableModel.getRowCount();
		final double[] outMafs = new double[attributeCount];
		for (int i = 0; i < attributeCount; ++i) {
			try {
				outMafs[i] = Double.parseDouble(tableModel.getValueAt(i, 1).toString());
			} catch (final NumberFormatException nfe) {
				outMafs[i] = 0F;
			}
		}
		return outMafs;
	}

	public String[] getAttributeNames() {
		final int attributeCount = tableModel.getRowCount();
		final String[] outNames = new String[attributeCount];
		for (int i = 0; i < attributeCount; ++i) {
			outNames[i] = tableModel.getValueAt(i, 0).toString();
		}
		return outNames;
	}

	public double getHeritability() {
		double herit = 0;
		try {
			// herit = Double.parseDouble(heritabilityTextField.getText());
			herit = doubleNumberFormat.parse(heritabilityTextField.getText()).doubleValue();
		} catch (final ParseException pe) {
			// TODO: Should never get here, because the field is a
			// JFormattedTextField with a NumberFormat.getIntegerInstance().
		}
		return herit;
	}

	public Double getPrevalence() {
		Double prevalence = null;
		if (prevalenceCheckBox.isSelected()) {
			try {
				prevalence = doubleNumberFormat.parse(prevalenceTextField.getText()).doubleValue();
			} catch (final ParseException pe) {
				// TODO: Should never get here, because the field is a
				// JFormattedTextField with a NumberFormat.getIntegerInstance().
			}
		}
		return prevalence;
	}

	public int getQuantileCountFieldValue() {
		int quantileCount = 0;
		try {
			// quantileCount =
			// Integer.parseInt(quantileCountTextField.getText());
			quantileCount = integerNumberFormat.parse(quantileCountTextField.getText()).intValue();
		} catch (final ParseException pe) {
			// TODO: Should never get here, because the field is a
			// JFormattedTextField with a NumberFormat.getIntegerInstance().
		}
		return quantileCount;
	}

	public int getQuantilePopulationFieldValue() {
		int quantilePopulation = 0;
		try {
			// quantilePopulation =
			// Integer.parseInt(quantilePopulationTextField.getText());
			quantilePopulation = integerNumberFormat.parse(quantilePopulationTextField.getText()).intValue();
		} catch (final ParseException pe) {
			// TODO: Should never get here, because the field is a
			// JFormattedTextField with a NumberFormat.getIntegerInstance().
		}
		return quantilePopulation;
	}

	public boolean getUseOddsRatio() {
		boolean useOddsRatio;
		useOddsRatio = oddsButton.isSelected();
		return useOddsRatio;
	}

	public boolean isSaved() {
		return saved;
	}

	/** This method reacts to state changes in the option pane. */
	@Override
	public void propertyChange(final PropertyChangeEvent e) {
		// String prop = e.getPropertyName();
		//
		// if (isVisible()
		// && (e.getSource() == optionPane)
		// && (JOptionPane.VALUE_PROPERTY.equals(prop) ||
		// JOptionPane.INPUT_VALUE_PROPERTY.equals(prop))) {
		// Object value = optionPane.getValue();
		//
		// if (value == JOptionPane.UNINITIALIZED_VALUE) {
		// //ignore reset
		// return;
		// }
		//
		// //Reset the JOptionPane's value.
		// //If you don't do this, then if the user
		// //presses the same button next time, no
		// //property change event will be fired.
		// optionPane.setValue(
		// JOptionPane.UNINITIALIZED_VALUE);
		//
		// if (btnString1.equals(value)) {
		// typedText = textField.getText();
		// String ucText = typedText.toUpperCase();
		// if (magicWord.equals(ucText)) {
		// //we're done; clear and dismiss the dialog
		// clearAndHide();
		// } else {
		// //text was invalid
		// textField.selectAll();
		// JOptionPane.showMessageDialog(
		// GenerateModelDialog.this,
		// "Sorry, \"" + typedText + "\" "
		// + "isn't a valid response.\n"
		// + "Please enter "
		// + magicWord + ".",
		// "Try again",
		// JOptionPane.ERROR_MESSAGE);
		// typedText = null;
		// textField.requestFocusInWindow();
		// }
		// } else { //user closed dialog or clicked cancel
		// // dd.setLabel("It's OK.  We won't force you to type " + magicWord +
		// ".");
		// typedText = null;
		// clearAndHide();
		// }
		// }
	}

	@Override
	public void stateChanged(final ChangeEvent e) {
		if (e.getSource().equals(prevalenceCheckBox)) {
			prevalenceTextField.setEnabled(prevalenceCheckBox.isSelected());
		}
	}

	protected void cancel() {
		saved = false;
		setVisible(false);
	}

	protected void confirm() {
		saved = true;
		setVisible(false);
	}

	private String getNextDefaultAttributeName() {
		int outAttributeNumber = firstAttributeNumber; // first guess
		for (int i = 0; i < tableModel.getRowCount(); ++i) {
			String attrName = (String) tableModel.getValueAt(i, 0);
			attrName = attrName.toLowerCase();
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
		return "P" + (outAttributeNumber);
	}

	private double getNextDefaultMaf() {
		return 0.2F;
	}

	private void popTableRow() {
		tableModel.removeRow(tableModel.getRowCount() - 1);
	}

	private void pushDefaultTableRow() {
		tableModel.addRow(new Object[] { getNextDefaultAttributeName(), getNextDefaultMaf() });
	}

	private void resetAttributeCount(final int inAttributeCount) {
		while (tableModel.getRowCount() > inAttributeCount) {
			popTableRow();
		}
		while (tableModel.getRowCount() < inAttributeCount) {
			pushDefaultTableRow();
		}
	}

	public class CancelAction extends Action {
		private static final long serialVersionUID = 1L;

		public CancelAction() {
			super("Cancel", null, "Candel the model", KeyEvent.VK_G, KeyStroke.getKeyStroke(KeyEvent.VK_G, ActionEvent.ALT_MASK));
		}

		@Override
		public void actionPerformed(final ActionEvent e) {
			cancel();
		}
	}

	public class SaveModelAction extends Action {
		private static final long serialVersionUID = 1L;

		public SaveModelAction() {
			super("Save", null, "Save the model", KeyEvent.VK_G, KeyStroke.getKeyStroke(KeyEvent.VK_G, ActionEvent.ALT_MASK));
		}

		@Override
		public void actionPerformed(final ActionEvent e) {
			confirm();
		}
	}

	private static class LabelledTextField extends JPanel {
		private static final long serialVersionUID = 1L;
		public JLabel label;
		public JFormattedTextField textField;

		public LabelledTextField(final String inLabel, final int inColumnCount, final NumberFormat inFormat) {
			super();
			// setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
			// setAlignmentY(Component.CENTER_ALIGNMENT);
			label = new JLabel(inLabel);
			label.setOpaque(true);
			add(label, BorderLayout.WEST);
			textField = new JFormattedTextField(inFormat);
			textField.setColumns(inColumnCount);
			textField.setOpaque(true);
			add(textField, BorderLayout.WEST);
		}
	}
}