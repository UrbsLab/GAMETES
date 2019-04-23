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
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.text.NumberFormat;
import java.util.Arrays;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.KeyStroke;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.DefaultTableModel;

import org.epistasis.snpgen.document.SnpGenDocument.DocModel;
import org.epistasis.snpgen.document.SnpGenDocument.DocString;
import org.epistasis.snpgen.simulator.PenetranceTable;
import org.epistasis.snpgen.simulator.PenetranceTable.CellId;
import org.epistasis.snpgen.ui.PenetranceTablePane.Square;
import org.epistasis.snpgen.ui.SnpGenMainWindow.Action;

class EditModelDialog extends JDialog implements ModelUpdater, FocusListener, ChangeListener {
	private static final long serialVersionUID = 1L;
	private static final int maxLocusCount = 3;
	private static final int alleleCount = 3;

	private DocModel model;
	private boolean saved;
	private boolean saveToNewFile;
	NumberFormat integerNumberFormat;
	NumberFormat doubleNumberFormat;
	private JRadioButton locus2Button;
	private JRadioButton locus3Button;
	PenetranceTablePane tablePane;
	DefaultTableModel tableModel;

	String[] snpTitles;
	String[] snpMajorAlleles;
	String[] snpMinorAlleles;

	LabelledTextField[] mafTextFields;

	private int locusCount;

	LabelledLabel heritability;
	LabelledLabel prevalence;
	LabelledLabel edm;
	LabelledLabel oddsRatio;
	MarginalPenetranceSet marginalPenetranceSet;

	// For implementing ModelUpdater:
	Square[] squares;

	public EditModelDialog(final DocModel inModel, final Frame aFrame, final boolean inEnableSaveAs, final String[] inSnpMajorAlleles,
			final String[] inSnpMinorAlleles, final String inSpareSnpTitle) // inSpareSnpTitle
			// is
			// in
			// case
			// EditModelDialog
			// needs
			// to
			// go
			// from
			// 2-locus
			// to
			// 3-locus
	{
		super(aFrame, true);

		saveToNewFile = false;

		model = inModel;
		locusCount = inModel.attributeCount.getInteger();
		assert (locusCount == 2) || (locusCount == 3);

		snpTitles = new String[EditModelDialog.maxLocusCount];
		snpTitles[2] = inSpareSnpTitle; // default, in case locusCount < 3
		for (int i = 0; i < locusCount; ++i) {
			snpTitles[i] = model.attributeNameArray[i].getString();
		}
		snpMajorAlleles = Arrays.copyOf(inSnpMajorAlleles, EditModelDialog.alleleCount);
		snpMinorAlleles = Arrays.copyOf(inSnpMinorAlleles, EditModelDialog.alleleCount);

		integerNumberFormat = NumberFormat.getIntegerInstance();
		doubleNumberFormat = NumberFormat.getNumberInstance();

		PenetranceTable[] tables = model.getPenetranceTables();
		if ((tables == null) || (tables.length == 0)) {
			tables = new PenetranceTable[1];
			model.setPenetranceTables(tables);
		}
		if (tables[0] == null) {
			final PenetranceTable table = new PenetranceTable(3, locusCount);
			tables[0] = table;
		}
		final PenetranceTable table = tables[0];
		assert table.attributeCount == locusCount;
		table.setAttributeNames(Arrays.copyOf(snpTitles, locusCount));

		setContentPane(createContentPane(inEnableSaveAs));
		modelToPenetranceSquares();
		mafModelToUiAll();
		updateCalculatedFields();

		// //Ensure the text field always gets the first focus.
		// addComponentListener(new ComponentAdapter() {
		// public void componentShown(ComponentEvent ce) {
		// textField.requestFocusInWindow();
		// }
		// });
	}

	/** This method clears the dialog and hides it. */
	public void clearAndHide() {
		// textField.setText(null);
		setVisible(false);
	}

	public void clearModel() {
		clearPenetranceSquares();
		clearMafUi();
		penetranceSquaresToModel();
		mafUiToModelAll();
		updateCalculatedFields();
	}

	public void clearPenetranceSquares() {
		if (locusCount == 2) {
			clearOnePenetranceSquare(squares[0]);
		} else if (locusCount == 3) {
			for (int k = 0; k < 3; ++k) {
				clearOnePenetranceSquare(squares[k]);
			}
		}
	}

	public Container createContentPane(final boolean inEnableSaveAs) {
		JPanel contentPane; // The content pane of the window
		JPanel headerPane; // The top pane
		JPanel footerPane; // The top pane
		JPanel modelPane; // The bottom pane
		// JPanel tablePane; // The bottom-left pane
		JPanel parameterPane; // The bottom-right pane
		JPanel mafPane; // The MAF-parameter pane
		JPanel calculatedPane; // The calculated-parameter pane
		// JPanel penetrancePane; // The marginal-penetrance pane

		final Graphics graphics = getOwner().getGraphics();

		contentPane = new JPanel();
		contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.Y_AXIS));
		contentPane.setOpaque(true);

		headerPane = new JPanel();
		headerPane.setLayout(new BoxLayout(headerPane, BoxLayout.X_AXIS));
		headerPane.setAlignmentX(Component.CENTER_ALIGNMENT);

		// Create the radio buttons.
		locus2Button = new JRadioButton("2-locus");
		locus3Button = new JRadioButton("3-locus");
		if (locusCount == 3) {
			locus3Button.setSelected(true);
		} else {
			locus2Button.setSelected(true);
		}

		// Group the radio buttons.
		final ButtonGroup group = new ButtonGroup();
		group.add(locus2Button);
		group.add(locus3Button);

		// Register a listener for the radio buttons.
		locus2Button.addChangeListener(this);
		locus3Button.addChangeListener(this);

		headerPane.add(new JLabel("Model Order:"));
		headerPane.add(locus2Button);
		headerPane.add(locus3Button);

		contentPane.add(headerPane);

		modelPane = new JPanel();
		modelPane.setLayout(new BoxLayout(modelPane, BoxLayout.X_AXIS));
		modelPane.setAlignmentY(Component.CENTER_ALIGNMENT);

		contentPane.add(modelPane);

		// tablePane = new JPanel();
		// tablePane.setLayout(new BoxLayout(tablePane, BoxLayout.X_AXIS));
		// tablePane.setAlignmentY(Component.LEFT_ALIGNMENT);
		// modelPane.add(tablePane);

		tablePane = new PenetranceTablePane(this, graphics, snpTitles, snpMajorAlleles, snpMinorAlleles, locusCount);
		modelPane.add(tablePane);
		// modelPane.add(tablePane, BorderLayout.CENTER);

		parameterPane = new JPanel();
		parameterPane.setLayout(new BoxLayout(parameterPane, BoxLayout.Y_AXIS));
		parameterPane.setAlignmentX(Component.RIGHT_ALIGNMENT);

		mafPane = new JPanel();
		mafPane.setLayout(new BoxLayout(mafPane, BoxLayout.Y_AXIS));
		mafPane.setAlignmentX(Component.RIGHT_ALIGNMENT);

		final JPanel titlePane = new JPanel();
		titlePane.add(new JLabel("Minor-Allele Frequencies:"));
		mafPane.add(titlePane);

		mafTextFields = new LabelledTextField[EditModelDialog.maxLocusCount];
		for (int i = 0; i < EditModelDialog.maxLocusCount; ++i) {
			mafTextFields[i] = new LabelledTextField("MAF " + snpTitles[i] + ":", 4, doubleNumberFormat, this);
			if (i < locusCount) {
				mafTextFields[i].setVisible(true);
			} else {
				mafTextFields[i].setVisible(false);
			}
			mafPane.add(mafTextFields[i]);
		}

		Border inset = BorderFactory.createEmptyBorder(10, 10, 10, 10);
		Border line = BorderFactory.createLineBorder(Color.gray);
		Border compound = BorderFactory.createCompoundBorder(BorderFactory.createCompoundBorder(inset, line), inset);
		mafPane.setBorder(compound);

		parameterPane.add(mafPane);

		calculatedPane = new JPanel();
		calculatedPane.setLayout(new BoxLayout(calculatedPane, BoxLayout.Y_AXIS));
		calculatedPane.setAlignmentX(Component.RIGHT_ALIGNMENT);

		final JPanel calculatedSubPane = new JPanel();
		calculatedSubPane.setLayout(new BoxLayout(calculatedSubPane, BoxLayout.Y_AXIS));
		calculatedSubPane.setAlignmentX(Component.RIGHT_ALIGNMENT);

		heritability = new LabelledLabel("Heritability:");
		prevalence = new LabelledLabel("Prevalence:");
		edm = new LabelledLabel("EDM:");
		oddsRatio = new LabelledLabel("COR:");
		heritability.setVisible(true);
		prevalence.setVisible(true);
		edm.setVisible(true);
		oddsRatio.setVisible(true);
		calculatedSubPane.add(heritability);
		calculatedSubPane.add(prevalence);
		calculatedSubPane.add(edm);
		calculatedSubPane.add(oddsRatio);
		inset = BorderFactory.createEmptyBorder(10, 10, 10, 10);
		line = BorderFactory.createLineBorder(Color.gray);
		compound = BorderFactory.createCompoundBorder(BorderFactory.createCompoundBorder(inset, line), inset);
		calculatedSubPane.setBorder(compound);
		calculatedPane.add(calculatedSubPane);

		final int width = 192;
		final int height = calculatedSubPane.getPreferredSize().height;
		calculatedSubPane.setPreferredSize(new Dimension(width, height));

		marginalPenetranceSet = new MarginalPenetranceSet(graphics, snpTitles, snpMajorAlleles, snpMinorAlleles);
		marginalPenetranceSet.setLocusCount();
		marginalPenetranceSet.setVisible(true);
		calculatedPane.add(marginalPenetranceSet);

		parameterPane.add(calculatedPane);

		// penetrancePane = new JPanel();
		// penetrancePane.setLayout(new BoxLayout(penetrancePane,
		// BoxLayout.Y_AXIS));
		// penetrancePane.setAlignmentX(Component.RIGHT_ALIGNMENT);
		//
		// parameterPane.add(penetrancePane);

		modelPane.add(parameterPane);
		// modelPane.add(parameterPane, BorderLayout.LINE_END);

		footerPane = new JPanel();
		footerPane.setLayout(new BoxLayout(footerPane, BoxLayout.X_AXIS));
		footerPane.setAlignmentX(Component.CENTER_ALIGNMENT);

		// Create the Save and Cancel buttons.
		final JButton saveButton = new JButton(new SaveModelAction());
		footerPane.add(saveButton, BorderLayout.CENTER);
		if (inEnableSaveAs) {
			final JButton saveAsButton = new JButton(new SaveModelAsAction());
			footerPane.add(saveAsButton, BorderLayout.CENTER);
		}
		final JButton clearButton = new JButton(new ClearAction());
		footerPane.add(clearButton, BorderLayout.CENTER);
		final JButton cancelButton = new JButton(new CancelAction());
		footerPane.add(cancelButton, BorderLayout.CENTER);

		contentPane.add(footerPane);

		return contentPane;
	}

	@Override
	public void focusGained(final FocusEvent e) {

	}

	@Override
	public void focusLost(final FocusEvent e) {
		boolean isUpdatingComponent = false;
		for (int i = 0; i < EditModelDialog.alleleCount; ++i) {
			if (e.getComponent().equals(mafTextFields[i].textField)) {
				isUpdatingComponent = true;
				break;
			}
		}
		if (isUpdatingComponent) {
			updateModel();
		}
	}

	public boolean isSaved() {
		return saved;
	}

	public void mafModelToUiAll() {
		assert (locusCount == 2) || (locusCount == 3);
		if (locusCount == 2) {
			mafValueToUiOne(0, model.attributeAlleleFrequencyArray[0].getDouble());
			mafValueToUiOne(1, model.attributeAlleleFrequencyArray[1].getDouble());
		} else if (locusCount == 3) {
			mafValueToUiOne(0, model.attributeAlleleFrequencyArray[0].getDouble());
			mafValueToUiOne(1, model.attributeAlleleFrequencyArray[1].getDouble());
			mafValueToUiOne(2, model.attributeAlleleFrequencyArray[2].getDouble());
		}
	}

	public void mafUiToModelAll() {
		final double[] mafs = new double[locusCount];
		assert (locusCount == 2) || (locusCount == 3);
		if (locusCount == 2) {
			mafs[0] = (double) mafUiToModelOne(0);
			mafs[1] = (double) mafUiToModelOne(1);
			model.attributeAlleleFrequencyArray[0].setValue(mafs[0]);
			model.attributeAlleleFrequencyArray[1].setValue(mafs[1]);
		} else if (locusCount == 3) {
			mafs[0] = (double) mafUiToModelOne(0);
			mafs[1] = (double) mafUiToModelOne(1);
			mafs[2] = (double) mafUiToModelOne(2);
			model.attributeAlleleFrequencyArray[0].setValue(mafs[0]);
			model.attributeAlleleFrequencyArray[1].setValue(mafs[1]);
			model.attributeAlleleFrequencyArray[2].setValue(mafs[2]);
		}

		final PenetranceTable[] tables = model.getPenetranceTables();
		final PenetranceTable table = tables[0];
		table.setMinorAlleleFrequencies(mafs);
	}

	@Override
	public void modelToPenetranceSquares() {
		final PenetranceTable[] tables = model.getPenetranceTables();
		final PenetranceTable table = tables[0];
		assert table.attributeCount == locusCount;
		if (locusCount == 2) {
			final CellId cellId = new CellId(2);
			modelToOnePenetranceSquare(table, cellId, squares[0], 0);
		} else if (locusCount == 3) {
			final CellId cellId = new CellId(3);
			for (int k = 0; k < 3; ++k) {
				cellId.setIndex(2, k);
				modelToOnePenetranceSquare(table, cellId, squares[k], 0);
			}
		}
	}

	@Override
	public void penetranceSquaresToModel() {
		final PenetranceTable[] tables = model.getPenetranceTables();
		final PenetranceTable table = tables[0];
		assert table.attributeCount == locusCount;
		if (locusCount == 2) {
			final CellId cellId = new CellId(2);
			onePenetranceSquareToModel(squares[0], table, cellId, 0);
		} else if (locusCount == 3) {
			final CellId cellId = new CellId(3);
			for (int k = 0; k < 3; ++k) {
				cellId.setIndex(2, k);
				onePenetranceSquareToModel(squares[k], table, cellId, 0);
			}
		}
	}

	public boolean saveToNewFile() {
		return saveToNewFile;
	}

	public void setLocusCount(final int inLocusCount) {
		if (locusCount != inLocusCount) {
			locusCount = inLocusCount;
			if (tablePane != null) {
				tablePane.setLocusCount(inLocusCount);
			}
			if (locusCount == 3) {
				mafTextFields[2].textField.setText(doubleNumberFormat.format(0.2F));
				mafTextFields[2].setVisible(true);
			} else {
				mafTextFields[2].setVisible(false);
			}
			marginalPenetranceSet.setLocusCount();
			updateLocusCountInModel();
		}
	}

	// Implement ModelUpdater
	@Override
	public void setSquares(final Square[] squares) {
		this.squares = squares;
	}

	@Override
	public void stateChanged(final ChangeEvent e) {
		if (e.getSource().equals(locus2Button) || e.getSource().equals(locus3Button)) {
			if (locus2Button.isSelected()) {
				setLocusCount(2);
			} else if (locus3Button.isSelected()) {
				setLocusCount(3);
			}
		}
	}

	// In the PenetranceTable of the model, we use a CellId.
	// If locusCount == 2, cellId[0] specifies which row and cellId[1] specifies
	// which column.
	// If locusCount == 3, cellId[0] specifies which row, cellId[1] specifies
	// which column, and cellId[2] specifies which square.
	public void updateCalculatedFields() {
		final PenetranceTable[] tables = model.getPenetranceTables();
		final PenetranceTable table = tables[0];

		// int width1 = caseProportion.getWidth();
		// int sWidth1 = caseProportion.getPreferredSize().width;

		final double heritabilityValue = (double) table.calcAndSetHeritability();
		model.heritability.setValue(heritabilityValue);
		heritability.value.setText(formatNumber(heritabilityValue));

		final double prevalenceValue = (double) table.calcAndSetPrevalence();
		model.prevalence.setValue(prevalenceValue);
		prevalence.value.setText(formatNumber(prevalenceValue));

		edm.value.setText(formatNumber(table.calcAndSetEdm()));
		oddsRatio.value.setText(formatNumber(table.calcAndSetOddsRatio()));

		// int width2 = caseProportion.getWidth();
		// int sWidth2 = caseProportion.getPreferredSize().width;
		// System.out.println("caseProportion\twidth1=" + width1 + ", width2=" +
		// width2 + "\tsWidth1=" + sWidth1 + ", sWidth2=" + sWidth2);

		final double[][] marginalPenetrances = table.calcMarginalPrevalences();
		double[][] rearrangedMarginalPenetrances = null;
		if (locusCount == 2) {
			rearrangedMarginalPenetrances = new double[2][];
			rearrangedMarginalPenetrances[0] = marginalPenetrances[0];
			rearrangedMarginalPenetrances[1] = marginalPenetrances[1];
		} else if (locusCount == 3) {
			rearrangedMarginalPenetrances = new double[3][];
			rearrangedMarginalPenetrances[0] = marginalPenetrances[0];
			rearrangedMarginalPenetrances[1] = marginalPenetrances[1];
			rearrangedMarginalPenetrances[2] = marginalPenetrances[2];
		}
		marginalPenetranceSet.updateMarginalPenetrances(rearrangedMarginalPenetrances);
	}

	@Override
	public void updateModel() {
		penetranceSquaresToModel();
		mafUiToModelAll();
		updateCalculatedFields();
	}

	@Override
	public void updateModelUnlessInvalid() {
		final boolean valid = validateValuesInPenetranceSquares();
		if (valid) {
			updateModel();
		} else {
			modelToPenetranceSquares();
		}
	}

	public boolean validateValuesInPenetranceSquares() {
		boolean outValid = true;

		if (locusCount == 2) {
			outValid = validateValuesInOnePenetranceSquare(squares[0]);
		} else if (locusCount == 3) {
			for (int k = 0; k < 3; ++k) {
				outValid &= validateValuesInOnePenetranceSquare(squares[k]);
				if (!outValid) {
					break;
				}
			}
		}
		return outValid;
	}

	protected void cancel() {
		saved = false;
		setVisible(false);
	}

	protected void clear() {
		clearModel();
	}

	protected void confirm() {
		saved = true;
		setVisible(false);
	}

	DocModel getModel() {
		return model;
	}

	void setModel(final DocModel model) {
		this.model = model;
	}

	private void clearMafUi() {
		assert (locusCount == 2) || (locusCount == 3);
		if (locusCount == 2) {
			mafValueToUiOne(0, 0);
			mafValueToUiOne(1, 0);
		} else if (locusCount == 3) {
			mafValueToUiOne(0, 0);
			mafValueToUiOne(1, 0);
			mafValueToUiOne(2, 0);
		}
	}

	private void clearOnePenetranceSquare(final Square square) {
		for (int i = 0; i < 3; ++i) {
			for (int j = 0; j < 3; ++j) {
				square.cells[i][j].setText("0");
			}
		}
	}

	private String formatNumber(final double inNumber) {
		String outString;
		if (Double.isNaN(inNumber)) {
			outString = "-";
		} else {
			outString = doubleNumberFormat.format(inNumber);
		}
		return outString;
	}

	private double mafUiToModelOne(final int which) {
		double value = 0;
		final String text = mafTextFields[which].textField.getText();
		if ((text != null) && (text.length() > 0)) {
			try {
				value = Double.parseDouble(text);
			} catch (final NumberFormatException nfe) {
			}
		}
		return value;
	}

	private void mafValueToUiOne(final int which, final double inValue) {
		String text;
		text = doubleNumberFormat.format(inValue);
		mafTextFields[which].textField.setText(text);
	}

	private void mafValueToUiOne(final int which, final Double inValue) {
		String text;
		if (inValue == null) {
			text = doubleNumberFormat.format(0);
		} else {
			text = doubleNumberFormat.format(inValue);
		}
		mafTextFields[which].textField.setText(text);
	}

	// "Row i, column j" means that i specifies the y-coordinate and j specifies
	// the x-coordinate.
	private void modelToOnePenetranceSquare(final PenetranceTable table, final CellId cellId, final Square square,
			final int firstCellIdIndex) {
		for (int i = 0; i < 3; ++i) {
			cellId.setIndex(firstCellIdIndex + 1, i);
			for (int j = 0; j < 3; ++j) {
				cellId.setIndex(firstCellIdIndex, j);
				square.cells[i][j].setText(doubleNumberFormat.format(table.getPenetranceValue(cellId)));
			}
		}
	}

	// "Row i, column j" means that i specifies the y-coordinate and j specifies
	// the x-coordinate.
	private void onePenetranceSquareToModel(final Square square, final PenetranceTable table, final CellId cellId,
			final int firstCellIdIndex) {
		for (int i = 0; i < 3; ++i) {
			cellId.setIndex(firstCellIdIndex + 1, i);
			for (int j = 0; j < 3; ++j) {
				cellId.setIndex(firstCellIdIndex, j);
				double value = 0;
				try {
					value = Double.parseDouble(square.cells[i][j].getText());
				} catch (final NumberFormatException nfe) {
				}
				table.setPenetranceValue(cellId, value);
			}
		}
	}

	private void updateLocusCountInModel() {
		final int oldLocusCount = model.attributeCount.getInteger();
		if (oldLocusCount != locusCount) {
			model.resetAttributeCount(locusCount);
			for (int i = oldLocusCount; i < locusCount; ++i) {
				model.attributeNameArray[i] = new DocString(snpTitles[i]);
			}

			final PenetranceTable[] tables = model.getPenetranceTables();
			final PenetranceTable oldTable = tables[0];
			assert oldLocusCount == oldTable.attributeCount;

			final PenetranceTable newTable = new PenetranceTable(3, locusCount);
			CellId oldTableCellId = null;
			CellId newTableCellId = null;
			if (oldLocusCount == 2) {
				oldTableCellId = new CellId(2);
			} else if (oldLocusCount == 3) {
				oldTableCellId = new CellId(3);
				oldTableCellId.setIndex(2, 0);
			}
			if (locusCount == 2) {
				newTableCellId = new CellId(2);
			} else if (locusCount == 3) {
				newTableCellId = new CellId(3);
				newTableCellId.setIndex(2, 0);
			}
			if (((oldLocusCount == 2) && (locusCount == 3)) || ((oldLocusCount == 3) && (locusCount == 2))) {
				for (int i = 0; i < 3; ++i) {
					oldTableCellId.setIndex(1, i);
					newTableCellId.setIndex(1, i);
					for (int j = 0; j < 3; ++j) {
						oldTableCellId.setIndex(0, j);
						newTableCellId.setIndex(0, j);
						newTable.setPenetranceValue(newTableCellId, oldTable.getPenetranceValue(oldTableCellId));
					}
				}
			} else {
				assert false; // Should be one of the above two possibilities
			}
			newTable.setAttributeNames(Arrays.copyOf(snpTitles, locusCount));

			tables[0] = newTable;
			modelToPenetranceSquares();
		}
	}

	private boolean validateValuesInOnePenetranceSquare(final Square square) {
		boolean outValid = true;
		for (int i = 0; i < 3; ++i) {
			for (int j = 0; j < 3; ++j) {
				try {
					final double value = Double.parseDouble(square.cells[i][j].getText());
					if ((value < 0) || (value > 1)) {
						outValid = false;
						break;
					}
				} catch (final NumberFormatException nfe) {
					outValid = false;
					break;
				}
			}
		}
		return outValid;
	}

	public class MarginalPenetrance extends JPanel {
		private static final long serialVersionUID = 1L;
		JLabel[] penetranceValues;

		public MarginalPenetrance(final Graphics graphics, final String inOverallLabel, final String inMajorAllele,
				final String inMinorAllele) {
			super();
			setLayout(null);

			final int topTitleMargin = 0;
			final int topAlleleMargin = 5;
			final int leftTitleMargin = 5;
			final int alleleLabelMargin = 5;
			final int penetranceValueMarginY = 5;

			final JLabel leftTitleLabel = new JLabel(inOverallLabel);

			FontMetrics fontMetrics;
			Font font;

			font = leftTitleLabel.getFont();
			fontMetrics = graphics.getFontMetrics(font);

			final int leftTitleWidth = fontMetrics.stringWidth(inOverallLabel);
			final int leftTitleHeight = fontMetrics.getHeight();

			final String[] alleleNames = new String[EditModelDialog.alleleCount];
			alleleNames[0] = inMajorAllele + inMajorAllele;
			alleleNames[1] = inMajorAllele + inMinorAllele;
			alleleNames[2] = inMinorAllele + inMinorAllele;

			final JLabel[] alleleLabels = new JLabel[EditModelDialog.alleleCount];
			for (int i = 0; i < EditModelDialog.alleleCount; ++i) {
				alleleLabels[i] = new JLabel(alleleNames[i]);
			}
			final int alleleLabelWidth = Math.max(
					Math.max(fontMetrics.stringWidth(alleleNames[0]), fontMetrics.stringWidth(alleleNames[1])),
					fontMetrics.stringWidth(alleleNames[2]));
			final int alleleLabelHeight = fontMetrics.getHeight();

			final int penetranceValueWidth = 100;
			final int penetranceValueHeight = (int) (fontMetrics.getHeight() * 1.1);

			penetranceValues = new JLabel[EditModelDialog.alleleCount];
			for (int i = 0; i < EditModelDialog.alleleCount; ++i) {
				penetranceValues[i] = new JLabel();
				// penetranceValueHeight = penetranceValues[i].getHeight();
				penetranceValues[i].setPreferredSize(new Dimension(penetranceValueWidth, penetranceValueHeight));
			}

			// System.out.println("topTitleWidth=" + topTitleWidth +
			// "\ttopTitleHeight=" + topTitleHeight + "\tleftTitleWidth=" +
			// leftTitleWidth + "\tleftTitleHeight=" + leftTitleHeight);
			// System.out.println("topAlleleWidth=" + topAlleleWidth +
			// "\ttopAlleleHeight=" + topAlleleHeight + "\tleftAlleleWidth=" +
			// leftAlleleWidth + "\tleftAlleleHeight=" + leftAlleleHeight);

			final int leftHeaderWidth = leftTitleWidth + leftTitleMargin + alleleLabelWidth + alleleLabelMargin;
			final int totalWidth = leftHeaderWidth + penetranceValueWidth + 40;
			// System.out.println("totalWidth=" + totalWidth);
			final int totalHeight = topTitleMargin + topAlleleMargin
					+ (EditModelDialog.alleleCount * (penetranceValueHeight + penetranceValueMarginY));

			add(leftTitleLabel);
			leftTitleLabel.setBounds(0, 0, leftTitleWidth, leftTitleHeight);

			// System.out.println("topTitleLeft=" + topTitleLeft +
			// "\tleftTitleTop=" + leftTitleTop);
			// System.out.println();
			// System.out.println();

			for (int j = 0; j < EditModelDialog.alleleCount; ++j) {
				final int y = j * (penetranceValueHeight + penetranceValueMarginY);
				add(alleleLabels[j]);
				alleleLabels[j].setBounds(leftTitleWidth + leftTitleMargin, y, alleleLabelWidth, alleleLabelHeight);
				add(penetranceValues[j]);
				penetranceValues[j].setBounds(leftTitleWidth + leftTitleMargin + alleleLabelWidth + alleleLabelMargin, y,
						penetranceValueWidth, penetranceValueHeight);
			}
			final Dimension overallSize = new Dimension(totalWidth, totalHeight);
			setPreferredSize(overallSize);
		}

		public void updateMarginalPenetrances(final double[] inMarginalPenetrances) {
			try {
				for (int whichAlleleValue = 0; whichAlleleValue < EditModelDialog.alleleCount; ++whichAlleleValue) {
					penetranceValues[whichAlleleValue].getWidth();
					penetranceValues[whichAlleleValue].getPreferredSize();
					penetranceValues[whichAlleleValue].setText(doubleNumberFormat.format(inMarginalPenetrances[whichAlleleValue]));
					penetranceValues[whichAlleleValue].getWidth();
					penetranceValues[whichAlleleValue].getPreferredSize();
				}
			} catch (NullPointerException npe) {
				npe = null;
			}
		}
	}

	public class MarginalPenetranceSet extends JPanel {
		private static final long serialVersionUID = 1L;
		MarginalPenetrance[] panes;

		public MarginalPenetranceSet(final Graphics graphics, final String[] inSnpTitles, final String[] inSnpMajorAlleles,
				final String[] inSnpMinorAlleles) {
			super();
			final Border inset = BorderFactory.createEmptyBorder(10, 10, 10, 10);
			final Border line = BorderFactory.createLineBorder(Color.gray);
			final Border compound = BorderFactory.createCompoundBorder(BorderFactory.createCompoundBorder(inset, line), inset);
			setBorder(compound);
			// setBorder(BorderFactory.createLineBorder(Color.gray));
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			setOpaque(true);

			final JPanel titlePane = new JPanel();
			titlePane.add(new JLabel("Marginal Penetrances:"));
			add(titlePane);

			panes = new MarginalPenetrance[3];
			for (int i = 0; i < 3; ++i) {
				panes[i] = new MarginalPenetrance(graphics, inSnpTitles[i], inSnpMajorAlleles[i], inSnpMinorAlleles[i]);
				add(panes[i]);
			}
		}

		public void setLocusCount() {
			if (locusCount == 3) {
				panes[2].setVisible(true);
			} else {
				panes[2].setVisible(false);
			}
		}

		public void updateMarginalPenetrances(final double[][] inMarginalPenetrances) {
			for (int whichLocus = 0; whichLocus < locusCount; ++whichLocus) {
				panes[whichLocus].updateMarginalPenetrances(inMarginalPenetrances[whichLocus]);
			}
		}
	}

	private class CancelAction extends Action {
		private static final long serialVersionUID = 1L;

		public CancelAction() {
			super("Cancel", null, "Cancel", KeyEvent.VK_G, KeyStroke.getKeyStroke(KeyEvent.VK_G, ActionEvent.ALT_MASK));
		}

		@Override
		public void actionPerformed(final ActionEvent e) {
			cancel();
		}
	}

	private class ClearAction extends Action {
		private static final long serialVersionUID = 1L;

		public ClearAction() {
			super("Clear", null, "Clear", KeyEvent.VK_G, KeyStroke.getKeyStroke(KeyEvent.VK_G, ActionEvent.ALT_MASK));
		}

		@Override
		public void actionPerformed(final ActionEvent e) {
			clear();
		}
	}

	private static class LabelledLabel extends JPanel {
		private static final long serialVersionUID = 1L;
		public JLabel label;
		public JLabel value;

		public LabelledLabel(final String inLabel) {
			super();
			label = new JLabel(inLabel);
			label.setOpaque(true);
			add(label, BorderLayout.WEST);
			value = new JLabel("");
			value.setOpaque(true);
			add(value, BorderLayout.EAST);
		}
	}

	private static class LabelledTextField extends JPanel {
		private static final long serialVersionUID = 1L;
		public JLabel label;
		public JFormattedTextField textField;

		public LabelledTextField(final String inLabel, final int inColumnCount, final NumberFormat inFormat,
				final FocusListener inFocusListener) {
			super();
			// setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
			// setAlignmentY(Component.CENTER_ALIGNMENT);
			label = new JLabel(inLabel);
			label.setOpaque(true);
			add(label, BorderLayout.WEST);
			textField = new JFormattedTextField(inFormat);
			textField.setColumns(inColumnCount);
			textField.setOpaque(true);
			textField.addFocusListener(inFocusListener);
			add(textField, BorderLayout.EAST);
		}
	}

	private class SaveModelAction extends Action {
		private static final long serialVersionUID = 1L;

		public SaveModelAction() {
			super("Save", null, "Save the model", KeyEvent.VK_G, KeyStroke.getKeyStroke(KeyEvent.VK_G, ActionEvent.ALT_MASK));
		}

		@Override
		public void actionPerformed(final ActionEvent e) {
			confirm();
		}
	}

	private class SaveModelAsAction extends Action {
		private static final long serialVersionUID = 1L;

		public SaveModelAsAction() {
			super("Save As", null, "Save the model as a new file", KeyEvent.VK_G, KeyStroke.getKeyStroke(KeyEvent.VK_G,
					ActionEvent.ALT_MASK));
		}

		@Override
		public void actionPerformed(final ActionEvent e) {
			saveToNewFile = true;
			confirm();
		}
	}
}