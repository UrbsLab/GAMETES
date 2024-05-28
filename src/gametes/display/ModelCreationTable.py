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
   private static final int kColumnWidgetCount = 2;
   DefaultTableModel tableModel;
   ModelCreationTable.ColumnWidget[] widgets = null;

   public ModelCreationTable(DefaultTableModel inTableModel) {
      super(inTableModel);
      this.tableModel = inTableModel;
      this.widgets = new ModelCreationTable.ColumnWidget[2];
      Arrays.fill(this.widgets, (Object)null);

      for(int i = 0; i < 2; ++i) {
         this.setWidgetForColumn(this.tableModel, i);
      }

      this.adjustRows();
      this.setShowGrid(true);
      this.setGridColor(Color.black);
      this.setTableHeader(this.createDefaultTableHeader());
   }

   public TableModel getTableModel() {
      return this.getModel();
   }

   private ModelCreationTable.ColumnWidget setWidgetForColumn(TableModel inTableModel, int inColumn) {
      ModelCreationTable.ColumnWidget widget = new ModelCreationTable.ColumnWidget(inTableModel, inColumn);
      this.getColumnModel().getColumn(inColumn).setCellRenderer(widget);
      this.getColumnModel().getColumn(inColumn).setCellEditor(widget);
      this.widgets[inColumn] = widget;
      return widget;
   }

   public void tableChanged(TableModelEvent inEvent) {
      super.tableChanged(inEvent);
      this.adjustRows();
   }

   private void adjustRows() {
      if (this.widgets != null) {
         int row;
         for(row = 0; row < 2; ++row) {
            if (this.widgets[row] != null) {
               this.widgets[row].adjustRowCount(this.getRowCount());
            }
         }

         for(row = 0; row < this.getRowCount(); ++row) {
            int height = this.getPreferredRowHeight(row);
            this.setRowHeight(row, height);
         }
      }

   }

   private void adjustRowHeight(int inRow) {
      int height = this.getPreferredRowHeight(inRow);
      this.setRowHeight(inRow, height);
   }

   private int getPreferredRowHeight(int inRow) {
      int maxHeight = this.getRowHeight();
      if (this.widgets != null) {
         for(int i = 0; i < 2; ++i) {
            if (this.widgets[i] != null) {
               int height = this.widgets[i].getPreferredRowHeight(inRow);
               if (height > maxHeight) {
                  maxHeight = height;
               }
            }
         }
      }

      return maxHeight;
   }

   public class ColumnWidget extends AbstractCellEditor implements TableCellRenderer, TableCellEditor {
      int column;
      TableModel tableModel;
      ArrayList<ModelCreationTable.ColumnWidget.Panel> panelArray;

      public ColumnWidget(TableModel inModel, int inColumn) {
         this.column = inColumn;
         this.tableModel = inModel;
         int modelCount = inModel.getRowCount();
         this.panelArray = new ArrayList(modelCount);

         for(int row = 0; row < modelCount; ++row) {
            this.panelArray.add(new ModelCreationTable.ColumnWidget.Panel(row, inColumn, this.tableModel, this.tableModel.getValueAt(row, inColumn)));
         }

      }

      public Component getTableCellRendererComponent(JTable table, Object color, boolean isSelected, boolean hasFocus, int row, int column) {
         return (Component)this.panelArray.get(row);
      }

      public void adjustRowCount(int inRowCount) {
         int row;
         for(row = this.panelArray.size() - 1; row >= inRowCount; --row) {
            this.panelArray.remove(row);
         }

         for(row = this.panelArray.size(); row < inRowCount; ++row) {
            this.panelArray.add(new ModelCreationTable.ColumnWidget.Panel(row, this.column, this.tableModel, this.tableModel.getValueAt(row, this.column)));
         }

      }

      public int getPreferredRowHeight(int inRow) {
         return ((ModelCreationTable.ColumnWidget.Panel)this.panelArray.get(inRow)).getPreferredHeight();
      }

      public Object getCellEditorValue() {
         return new Integer(1);
      }

      public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
         return (Component)this.panelArray.get(row);
      }

      public class Panel extends JPanel implements FocusListener {
         private JTextField textField;
         private int row;
         private int column;
         private TableModel tableModel;

         public Panel(int inRow, int inColumn, TableModel inTableModel) {
            this.row = inRow;
            this.column = inColumn;
            this.tableModel = inTableModel;
            this.setLayout(new BoxLayout(this, 1));
            this.setOpaque(true);
            this.setBackground(Color.white);
            this.add(this.textField = new JTextField(3));
            this.textField.addFocusListener(this);
         }

         public Panel(int inRow, int inColumn, TableModel inTableModel, Object inValue) {
            this(inRow, inColumn, inTableModel);
            this.textField.setText(inValue.toString());
         }

         public int getPreferredHeight() {
            return this.getPreferredSize().height;
         }

         public void focusGained(FocusEvent e) {
         }

         public void focusLost(FocusEvent e) {
            this.tableModel.setValueAt(this.textField.getText(), this.row, this.column);
         }
      }
   }
}
