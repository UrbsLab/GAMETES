package org.epistasis.snpgen.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.ArrayList;
import java.util.Arrays;

import javax.swing.AbstractCellEditor;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;

public class ModelCreationTable extends JTable {
	private static final long serialVersionUID = 1L;

	private static final int kColumnWidgetCount = 2;

	DefaultTableModel tableModel;
	ColumnWidget[] widgets = null;

	public ModelCreationTable(final DefaultTableModel inTableModel) {
		super(inTableModel);

		tableModel = inTableModel;
		widgets = new ColumnWidget[ModelCreationTable.kColumnWidgetCount];
		Arrays.fill(widgets, null);
		for (int i = 0; i < ModelCreationTable.kColumnWidgetCount; ++i) {
			setWidgetForColumn(tableModel, i);
		}

		adjustRows();
		setShowGrid(true);
		setGridColor(Color.black);
		setTableHeader(createDefaultTableHeader());
	}

	// public static ModelCreationTable createModelCreationTable()
	// {
	// DefaultTableModel model = new DefaultTableModel();
	//
	// model.addColumn("SNP");
	// model.addColumn("Minor Allele Frequency");
	//
	// ModelCreationTable table = new ModelCreationTable(model);
	// return table;
	// }

	public TableModel getTableModel() {
		return getModel();
	}

	@Override
	public void tableChanged(final TableModelEvent inEvent) {
		super.tableChanged(inEvent);
		adjustRows();
	}

	private void adjustRows() {
		int height;

		if (widgets != null) {
			for (int i = 0; i < ModelCreationTable.kColumnWidgetCount; ++i) {
				if (widgets[i] != null) {
					widgets[i].adjustRowCount(getRowCount());
				}
			}
			for (int row = 0; row < getRowCount(); ++row) {
				height = getPreferredRowHeight(row);
				setRowHeight(row, height);
			}
		}
	}

	private int getPreferredRowHeight(final int inRow) {
		int height, maxHeight;

		maxHeight = getRowHeight();
		if (widgets != null) {
			for (int i = 0; i < ModelCreationTable.kColumnWidgetCount; ++i) {
				if (widgets[i] != null) {
					height = widgets[i].getPreferredRowHeight(inRow);
					if (height > maxHeight) {
						maxHeight = height;
					}
				}
			}
		}
		return maxHeight;
	}

	private ColumnWidget setWidgetForColumn(final TableModel inTableModel, final int inColumn) {
		final ColumnWidget widget = new ColumnWidget(inTableModel, inColumn);
		getColumnModel().getColumn(inColumn).setCellRenderer(widget);
		getColumnModel().getColumn(inColumn).setCellEditor(widget);
		widgets[inColumn] = widget;
		return widget;
	}

	public class ColumnWidget extends AbstractCellEditor implements TableCellRenderer, TableCellEditor {
		private static final long serialVersionUID = 1L;
		int column;
		TableModel tableModel;
		ArrayList<Panel> panelArray;

		public ColumnWidget(final TableModel inModel, final int inColumn) {
			column = inColumn;
			tableModel = inModel;
			// ArrayList<SnpGenDocument.DocModel> modelList =
			// tableModel.getModelList();
			// int modelCount = modelList.size();
			final int modelCount = inModel.getRowCount();
			panelArray = new ArrayList<Panel>(modelCount);
			for (int row = 0; row < modelCount; ++row) {
				panelArray.add(new Panel(row, inColumn, tableModel, tableModel.getValueAt(row, inColumn)));
			}
		}

		public void adjustRowCount(final int inRowCount) {
			for (int row = panelArray.size() - 1; row >= inRowCount; --row) {
				panelArray.remove(row);
			}
			for (int row = panelArray.size(); row < inRowCount; ++row) {
				panelArray.add(new Panel(row, column, tableModel, tableModel.getValueAt(row, column)));
			}
		}

		// Implement the one CellEditor method that AbstractCellEditor doesn't.
		@Override
		public Object getCellEditorValue() {
			// return currentColor;
			return new Integer(1);
		}

		public int getPreferredRowHeight(final int inRow) {
			return panelArray.get(inRow).getPreferredHeight();
		}

		// Implement the one method defined by TableCellEditor.
		@Override
		public Component getTableCellEditorComponent(final JTable table, final Object value, final boolean isSelected, final int row,
				final int column) {
			return panelArray.get(row);
		}

		@Override
		public Component getTableCellRendererComponent(final JTable table, final Object color, final boolean isSelected,
				final boolean hasFocus, final int row, final int column) {
			return panelArray.get(row);
		}

		public class Panel extends JPanel implements FocusListener {
			private static final long serialVersionUID = 1L;
			private JTextField textField;
			private final int row;
			private final int column;
			private final TableModel tableModel;

			public Panel(final int inRow, final int inColumn, final TableModel inTableModel) {
				row = inRow;
				column = inColumn;
				tableModel = inTableModel;
				setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
				setOpaque(true); // Must do this for background to show up.
				setBackground(Color.white);
				add(textField = new JTextField(3));
				textField.addFocusListener(this);
			}

			public Panel(final int inRow, final int inColumn, final TableModel inTableModel, final Object inValue) {
				this(inRow, inColumn, inTableModel);
				textField.setText(inValue.toString());
			}

			@Override
			public void focusGained(final FocusEvent e) {
			}

			@Override
			public void focusLost(final FocusEvent e) {
				tableModel.setValueAt(textField.getText(), row, column);
			}

			public int getPreferredHeight() {
				return getPreferredSize().height;
			}
		}
	}
}
