package org.epistasis.snpgen.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.ArrayList;

import javax.swing.AbstractCellEditor;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;

import org.epistasis.snpgen.document.SnpGenDocument;
import org.epistasis.snpgen.document.SnpGenDocument.DocMember;
import org.epistasis.snpgen.document.SnpGenDocument.DocModel;

public class ModelTable extends JTable implements SnpGenDocument.DocListener {
	private static final long serialVersionUID = 1L;
	ModelTableModel tableModel;
	ColumnWidget[] widgets;

	public ModelTable(final ModelTableModel inTableModel) {
		super(inTableModel);

		tableModel = inTableModel;

		getColumnModel().getColumn(0).setCellRenderer(new ColumnTextRenderer(inTableModel, 0));
		getColumnModel().getColumn(1).setCellRenderer(new ColumnTextRenderer(inTableModel, 1));
		getColumnModel().getColumn(2).setCellRenderer(new ColumnTextRenderer(inTableModel, 2));
		getColumnModel().getColumn(5).setCellRenderer(new ColumnTextRenderer(inTableModel, 5));
		getColumnModel().getColumn(6).setCellRenderer(new ColumnTextRenderer(inTableModel, 6));
		getColumnModel().getColumn(7).setCellRenderer(new ColumnCheckBoxRenderer(inTableModel, 7));

		widgets = new ColumnWidget[8];
		for (int i = 0; i < widgets.length; ++i) {
			widgets[i] = null;
		}
		setWidgetForColumn(tableModel, 3);
		setWidgetForColumn(tableModel, 4);
		tableModel.addTableModelListener(tableModel);

		adjustRowHeights();
		setShowGrid(true);
		setGridColor(Color.black);
		setTableHeader(createDefaultTableHeader());

		tableModel.document.addDocumentListener(this);
		// setEnabled(false);
	}

	public void addUpdateListener(final UpdateListener inListener) {
		getTableModel().addUpdateListener(inListener);
	}

	@Override
	public void attributeCountChanged(final SnpGenDocument inDoc, final SnpGenDocument.DocModel inModel, final int whichModel,
			final int inNewAttributeCount) {
		widgets[3].setNewPanel(whichModel);
		widgets[4].setNewPanel(whichModel);
		ModelTable.this.adjustRowHeight(whichModel);
	}

	@Override
	public void datasetAdded(final SnpGenDocument inDoc, final SnpGenDocument.DocDataset inDataset, final int whichModel) {
	}

	public ModelTableModel getTableModel() {
		return (ModelTableModel) getModel();
	}

	public int[] getWhichSelectedModels() {
		final int rowCount = getRowCount();
		final ArrayList<Integer> selections = new ArrayList<Integer>();
		final ArrayList<Boolean> selected = tableModel.selected;
		for (int i = 0; i < Math.min(rowCount, selected.size()); ++i) {
			if (selected.get(i)) {
				selections.add(i);
			}
		}
		final int[] outSelections = new int[selections.size()];
		for (int i = 0; i < selections.size(); ++i) {
			outSelections[i] = selections.get(i);
		}
		return outSelections;
	}

	@Override
	public boolean isCellEditable(final int row, final int column) {
		if (column == 7) {
			return true;
		} else if (column == 5) {
			return true;
		} else {
			return false;
		}
	}

	@Override
	public void modelAdded(final SnpGenDocument inDoc, final SnpGenDocument.DocModel inModel, final int whichModel) {
		setWidgetForColumn(tableModel, 3); // Quick and dirty -- could adjust
		// each widget instead,
		setWidgetForColumn(tableModel, 4); // by adding a panel to the
		// panelArray.
		tableModel.fireTableDataChanged();
		adjustRowHeights();
	}

	@Override
	public void modelRemoved(final SnpGenDocument inDoc, final SnpGenDocument.DocModel inModel, final int whichModel) {
		setSelected(whichModel, false);
		setWidgetForColumn(tableModel, 3); // Quick and dirty -- could adjust
		// each widget instead,
		setWidgetForColumn(tableModel, 4); // by adding a panel to the
		// panelArray.
		tableModel.fireTableDataChanged();
		adjustRowHeights();
	}

	@Override
	public void modelUpdated(final SnpGenDocument inDoc, final SnpGenDocument.DocModel inModel, final int whichModel) {
		setWidgetForColumn(tableModel, 3); // Quick and dirty -- could adjust
		// each widget instead,
		setWidgetForColumn(tableModel, 4); // by adding a panel to the
		// panelArray.
		tableModel.fireTableDataChanged();
		adjustRowHeights();
	}

	public void setSelected(final int inWhichModel, final boolean inSelected) {
		tableModel.selected.set(inWhichModel, inSelected);
	}

	private void adjustRowHeight(final int inRow) {
		int height;

		height = getPreferredRowHeight(inRow);
		setRowHeight(inRow, height);
	}

	private void adjustRowHeights() {
		int height;

		for (int row = 0; row < getRowCount(); ++row) {
			height = getPreferredRowHeight(row);
			setRowHeight(row, height);
		}
	}

	private int getPreferredRowHeight(final int inRow) {
		int height, maxHeight;

		maxHeight = getRowHeight();
		for (int i = 0; i < 5; ++i) {
			if (widgets[i] != null) {
				height = widgets[i].getPreferredRowHeight(inRow);
				if (height > maxHeight) {
					maxHeight = height;
				}
			}
		}
		return maxHeight;
	}

	private ColumnWidget setWidgetForColumn(final ModelTableModel inTableModel, final int inColumn) {
		final ColumnWidget widget = new ColumnWidget(inTableModel, inColumn);
		getColumnModel().getColumn(inColumn).setCellRenderer(widget);
		getColumnModel().getColumn(inColumn).setCellEditor(widget);
		widgets[inColumn] = widget;
		return widget;
	}

	public static ModelTable createModelTable(final SnpGenDocument inDoc) {
		final ModelTableModel model = new ModelTableModel(inDoc);
		final ModelTable table = new ModelTable(model);
		return table;
	}

	public class ColumnCheckBoxRenderer extends DefaultTableCellRenderer implements TableCellRenderer {
		private static final long serialVersionUID = 1L;
		int column;
		ModelTableModel tableModel;
		protected JCheckBox checkBox;
		JPanel checkBoxPanel;

		public ColumnCheckBoxRenderer(final ModelTableModel inModel, final int inColumn) {
			checkBoxPanel = new JPanel();
			checkBoxPanel.setLayout(new BoxLayout(checkBoxPanel, BoxLayout.PAGE_AXIS));

			final JPanel horizontalPanel = new JPanel();
			horizontalPanel.setLayout(new BoxLayout(horizontalPanel, BoxLayout.LINE_AXIS));
			checkBoxPanel.add(Box.createVerticalGlue());
			checkBoxPanel.add(horizontalPanel);
			checkBoxPanel.add(Box.createVerticalGlue());

			checkBox = new JCheckBox();
			// checkBoxPanel.add(checkBox, BorderLayout.CENTER);

			horizontalPanel.add(Box.createHorizontalGlue());
			horizontalPanel.add(checkBox);
			horizontalPanel.add(Box.createHorizontalGlue());

			column = inColumn;
			tableModel = inModel;
		}

		@Override
		public Component getTableCellRendererComponent(final JTable table, final Object color, final boolean isSelected,
				final boolean hasFocus, final int row, final int column) {
			final Object obj = tableModel.getVariableAt(row, column);
			// Object obj = tableModel.getVariableAt(0, column);
			if (obj != null) {
				if (obj.getClass().equals(Boolean.class)) {
					final Boolean currentValue = (Boolean) obj;
					if (currentValue != null) {
						checkBox.setSelected(currentValue);
					}
				}
			}
			return checkBoxPanel;
		}
	}

	public class ColumnTextRenderer extends DefaultTableCellRenderer implements TableCellRenderer {
		private static final long serialVersionUID = 1L;
		int column;
		ModelTableModel tableModel;
		protected JTextField textField;

		public ColumnTextRenderer(final ModelTableModel inModel, final int inColumn) {
			textField = new JTextField();
			column = inColumn;
			tableModel = inModel;
		}

		@Override
		public Component getTableCellRendererComponent(final JTable table, final Object color, final boolean isSelected,
				final boolean hasFocus, final int row, final int column) {
			final Object obj = tableModel.getVariableAt(row, column);
			// Object obj = tableModel.getVariableAt(0, column);
			if (obj != null) {
				if (obj.getClass().equals(SnpGenDocument.DocString.class)) {
					final SnpGenDocument.DocString docString = (SnpGenDocument.DocString) obj;
					if (docString.getValue() != null) {
						textField.setText(docString.getValue().toString());
					}
				} else if (obj.getClass().equals(SnpGenDocument.DocDouble.class)) {
					final SnpGenDocument.DocDouble docDouble = (SnpGenDocument.DocDouble) obj;
					if (docDouble.getValue() != null) {
						textField.setText(docDouble.getValue().toString());
					}
				} else if (obj.getClass().equals(SnpGenDocument.DocInteger.class)) {
					final SnpGenDocument.DocInteger docInteger = (SnpGenDocument.DocInteger) obj;
					if (docInteger.getValue() != null) {
						textField.setText(docInteger.getValue().toString());
					}
				} else if (obj.getClass().equals(Integer.class)) {
					final Integer integer = (Integer) obj;
					if (integer != null) {
						textField.setText(integer.toString());
					}
				}
			}
			return textField;
		}
	}

	public class ColumnWidget extends AbstractCellEditor implements TableCellRenderer, TableCellEditor {
		private static final long serialVersionUID = 1L;
		int column;
		ModelTableModel tableModel;
		Panel[] panelArray;

		public ColumnWidget(final ModelTableModel inModel, final int inColumn) {
			column = inColumn;
			tableModel = inModel;
			final ArrayList<SnpGenDocument.DocModel> modelList = tableModel.getModelList();
			final int modelCount = modelList.size();
			panelArray = new Panel[modelCount];
			for (int row = 0; row < modelCount; ++row) {
				setNewPanel(row);
			}
		}

		// Implement the one CellEditor method that AbstractCellEditor doesn't.
		@Override
		public Object getCellEditorValue() {
			// return currentColor;
			return new Integer(1);
		}

		public int getPreferredRowHeight(final int inRow) {
			return panelArray[inRow].getPreferredHeight();
		}

		// Implement the one method defined by TableCellEditor.
		@Override
		public Component getTableCellEditorComponent(final JTable table, final Object value, final boolean isSelected, final int row,
				final int column) {
			return panelArray[row];
		}

		@Override
		public Component getTableCellRendererComponent(final JTable table, final Object color, final boolean isSelected,
				final boolean hasFocus, final int row, final int column) {
			return panelArray[row];
		}

		private Panel createPanel(final int inRow) {
			Panel outPanel = null;
			final Object obj = tableModel.getVariableAt(inRow, column);
			if (obj.getClass().equals(SnpGenDocument.DocString[].class)) {
				final SnpGenDocument.DocString[] docStringArray = (SnpGenDocument.DocString[]) obj;
				outPanel = new StringPanel(docStringArray);
			} else if (obj.getClass().equals(SnpGenDocument.DocDouble[].class)) {
				final SnpGenDocument.DocDouble[] docDoubleArray = (SnpGenDocument.DocDouble[]) obj;
				outPanel = new DoublePanel(docDoubleArray);
			}
			return outPanel;
		}

		private void setNewPanel(final int inRow) {
			Panel panel;
			panel = createPanel(inRow);
			if (panel != null) {
				panelArray[inRow] = panel;
			}
		}

		public class DoublePanel extends Panel {
			private static final long serialVersionUID = 1L;
			SnpGenDocument.DocDouble[] valueArray;

			public DoublePanel() {
				super();
			}

			public DoublePanel(final SnpGenDocument.DocDouble[] inArray) {
				super(inArray.length);
				valueArray = inArray;
				initFromDocument();
			}

			@Override
			protected DocMember[] getValueArray() {
				return valueArray;
			}
		}

		public abstract class Panel extends JPanel implements FocusListener {
			private static final long serialVersionUID = 1L;
			protected JTextField[] textFieldArray;

			public Panel() {
				setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
				setOpaque(true); // Must do this for background to show up.
				setBackground(Color.white);
			}

			public Panel(final int inCount) {
				this();
				textFieldArray = new JTextField[inCount];
				for (int j = 0; j < inCount; ++j) {
					add(textFieldArray[j] = new JTextField(3));
					textFieldArray[j].addFocusListener(this);
				}
			}

			@Override
			public void focusGained(final FocusEvent e) {
			}

			@Override
			public void focusLost(final FocusEvent e) {
				Integer which = null;
				for (int i = 0; i < textFieldArray.length; ++i) {
					if (e.getComponent().equals(textFieldArray[i])) {
						which = i;
						break;
					}
				}
				if (which != null) {
					try {
						getValueArray()[which].setValue(textFieldArray[which].getText());
					} catch (final NumberFormatException nfe) {
						textFieldArray[which].setText("");
						getValueArray()[which].setValue(textFieldArray[which].getText());
					}
				}
			}

			public int getPreferredHeight() {
				return getPreferredSize().height;
			}

			public synchronized Panel initFromDocument() {
				for (int j = 0; j < getValueArray().length; ++j) {
					final Object value = getValueArray()[j].getValue();
					if (value == null) {
						textFieldArray[j].setText("");
					} else {
						textFieldArray[j].setText(value.toString());
					}
				}
				return this;
			}

			protected abstract DocMember[] getValueArray();
		}

		public class StringPanel extends Panel {
			private static final long serialVersionUID = 1L;
			SnpGenDocument.DocString[] valueArray;

			public StringPanel() {
				super();
			}

			public StringPanel(final SnpGenDocument.DocString[] inArray) {
				super(inArray.length);
				valueArray = inArray;
				initFromDocument();
			}

			@Override
			protected DocMember[] getValueArray() {
				return valueArray;
			}
		}
	}

	public static class ModelTableModel extends DefaultTableModel implements TableModelListener {
		private static final long serialVersionUID = 1L;
		SnpGenDocument document;
		ArrayList<Boolean> selected;
		ArrayList<UpdateListener> updateListeners;

		private final String[] columnNames = { "Model", "# Attributes", "Heritability", "SNPs", "Minor allele freq",
				"Heterogeneity proportion", "# Quantiles", "Selected" };

		public ModelTableModel(final SnpGenDocument inDoc) {
			document = inDoc;
			selected = new ArrayList<Boolean>();
			updateListeners = new ArrayList<UpdateListener>();
		}

		public void addUpdateListener(final UpdateListener inListener) {
			updateListeners.add(inListener);
		}

		/*
		 * JTable uses this method to determine the default renderer/editor for
		 * each cell.
		 */
		@Override
		public Class<?> getColumnClass(final int col) {
			if (col == 5) {
				return Double.class;
			} else if (col == 6) {
				return Integer.class;
			} else if (col == 7) {
				return Boolean.class;
			} else {
				return Object.class;
				// return getValueAt(0, c).getClass();
			}
		}

		@Override
		public int getColumnCount() {
			return columnNames.length;
		}

		@Override
		public String getColumnName(final int col) {
			return columnNames[col];
		}

		public ArrayList<SnpGenDocument.DocModel> getModelList() {
			if (document == null) {
				return null;
			} else {
				return document.modelList;
			}
		}

		@Override
		public int getRowCount() {
			if (getModelList() == null) {
				return 0;
			} else {
				return getModelList().size();
			}
		}

		@Override
		public Object getValueAt(final int row, final int col) {
			final Object var = getVariableAt(row, col);
			if (DocMember.class.isInstance(var)) {
				return ((DocMember) var).getValue();
			} else {
				return var;
			}
		}

		public Object getVariableAt(final int row, final int col) {
			Object out = null;
			if (row < getModelList().size()) {
				final SnpGenDocument.DocModel model = getModelList().get(row);
				if (col == 0) {
					out = model.modelId;
				} else if (col == 1) {
					out = model.attributeCount;
				} else if (col == 2) {
					out = model.heritability;
				} else if (col == 3) {
					out = model.attributeNameArray;
				} else if (col == 4) {
					out = model.attributeAlleleFrequencyArray;
				} else if (col == 5) {
					out = model.fraction;
				} else if (col == 6) {
					out = new Integer(model.getQuantileCountInModel());
				} else if (col == 7) {
					while (selected.size() <= row) {
						selected.add(false);
					}
					out = selected.get(row);
				}
			}
			return out;
		}

		@Override
		public boolean isCellEditable(final int row, final int col) {
			return true;
		}

		@Override
		public void setValueAt(final Object value, final int row, final int col) {
			final Object var = getVariableAt(row, col);
			if (DocMember.class.isInstance(var)) {
				((DocMember) var).setValue(value);
			}
			if (col == 7) {
				while (selected.size() <= row) {
					selected.add(false);
				}
				selected.set(row, (Boolean) value);
				updateSelection();
			}
			fireTableCellUpdated(row, col);
		}

		@Override
		public void tableChanged(final TableModelEvent ev) {
			final int row = ev.getFirstRow();
			final int column = ev.getColumn();
			final TableModel model = (TableModel) ev.getSource();
			if (column == 1) // If we're in the Attribute-count column
			{
				final Object data = model.getValueAt(row, column);
				Integer attributeCount;
				try {
					attributeCount = new Integer(data.toString());
				} catch (final NumberFormatException nfe) {
					attributeCount = null;
				}
				if (attributeCount != null) {
					resetAttributeCount(row, attributeCount);
				}
			}
		}

		protected void updateSelection() {
			for (final UpdateListener l : updateListeners) {
				l.updateSelection();
			}
		}

		private void resetAttributeCount(final int inRow, final int inAttributeCount) {
			final DocModel snpModel = getModelList().get(inRow);
			snpModel.resetAttributeCount(inAttributeCount);
		}
	}

	public interface UpdateListener {
		void updateSelection();
	}
}
