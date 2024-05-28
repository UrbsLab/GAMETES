import tkinter as tk
from tkinter import ttk
from tkinter import messagebox
from tkinter import simpledialog
import locale

class GenerateModelDialog(tk.Toplevel):
    def __init__(self, parent, inNextModelNumber, inNextSnpNumber):
        super().__init__(parent)
        self.parent = parent
        self.firstModelNumber = inNextModelNumber
        self.firstAttributeNumber = inNextSnpNumber
        self.saved = False
        
        locale.setlocale(locale.LC_ALL, '')
        self.integerNumberFormat = locale.atoi
        self.floatNumberFormat = locale.atof

        self.create_content_pane()
        self.protocol("WM_DELETE_WINDOW", self.cancel)
        self.focus()

    def focusGained(self, event):
        pass

    def focusLost(self, event):
        if event.widget == self.attributeCountTextField:
            try:
                attr_count = int(self.attributeCountTextField.get())
                self.reset_attribute_count(attr_count)
            except ValueError:
                pass

    def isSaved(self):
        return self.saved

    def get_quantile_count_field_value(self):
        try:
            return int(self.quantileCountTextField.get())
        except ValueError:
            return 0

    def get_quantile_population_field_value(self):
        try:
            return int(self.quantilePopulationTextField.get())
        except ValueError:
            return 0

    def get_attribute_count_field_value(self):
        try:
            return int(self.attributeCountTextField.get())
        except ValueError:
            return 0

    def get_heritability(self):
        try:
            return float(self.heritabilityTextField.get())
        except ValueError:
            return 0.0

    def get_prevalence(self):
        if self.prevalenceCheckBox.instate(['selected']):
            try:
                return float(self.prevalenceTextField.get())
            except ValueError:
                return None
        return None

    def get_use_odds_ratio(self):
        return self.oddsButton.instate(['selected'])

    def get_attribute_count(self):
        return len(self.tableModel.get_children())

    def get_attribute_names(self):
        return [self.tableModel.item(row)['values'][0] for row in self.tableModel.get_children()]

    def get_attribute_minor_allele_frequencies(self):
        mafs = []
        for row in self.tableModel.get_children():
            try:
                mafs.append(float(self.tableModel.item(row)['values'][1]))
            except ValueError:
                mafs.append(0.0)
        return mafs

    def create_content_pane(self):
        self.container = ttk.Frame(self)
        self.container.pack(fill=tk.BOTH, expand=True)

        parameter_pane = ttk.Frame(self.container)
        parameter_pane.pack(fill=tk.BOTH, expand=True)

        attribute_pane = ttk.Frame(parameter_pane)
        attribute_pane.pack(fill=tk.X)

        self.attributeCountTextField = self.create_labelled_text_field(attribute_pane, "Number of attributes", 4)
        self.attributeCountTextField.bind("<FocusOut>", self.focusLost)

        self.heritabilityTextField = self.create_labelled_text_field(attribute_pane, "Heritability", 4)
        self.heritabilityTextField.insert(0, '0.2')

        prevalence_pane = ttk.Frame(attribute_pane)
        prevalence_pane.pack(fill=tk.X)

        self.prevalenceCheckBox = ttk.Checkbutton(prevalence_pane, command=self.toggle_prevalence)
        self.prevalenceCheckBox.pack(side=tk.LEFT)

        self.prevalenceTextField = self.create_labelled_text_field(prevalence_pane, "Prevalence", 4)
        self.prevalenceTextField.config(state=tk.DISABLED)

        variant_pane = ttk.Frame(parameter_pane)
        variant_pane.pack(fill=tk.X)

        self.edmButton = ttk.Radiobutton(variant_pane, text="EDM", value="EDM")
        self.edmButton.invoke()
        self.oddsButton = ttk.Radiobutton(variant_pane, text="Odds ratio", value="Odds ratio")

        variant_pane.pack(fill=tk.X)
        self.quantileCountTextField = self.create_labelled_text_field(variant_pane, "Quantile count", 8)
        self.quantileCountTextField.insert(0, '2')

        self.quantilePopulationTextField = self.create_labelled_text_field(variant_pane, "Quantile population size", 8)
        self.quantilePopulationTextField.insert(0, '1000')

        self.tableModel = ttk.Treeview(self.container, columns=("SNP", "Minor allele frequency"), show='headings')
        self.tableModel.heading("SNP", text="SNP")
        self.tableModel.heading("Minor allele frequency", text="Minor allele frequency")

        self.push_default_table_row()
        self.push_default_table_row()

        self.tableModel.pack(fill=tk.BOTH, expand=True)

        command_panel = ttk.Frame(self.container)
        command_panel.pack(fill=tk.X)

        save_button = ttk.Button(command_panel, text="Save", command=self.confirm)
        save_button.pack(side=tk.LEFT)

        cancel_button = ttk.Button(command_panel, text="Cancel", command=self.cancel)
        cancel_button.pack(side=tk.LEFT)

    def toggle_prevalence(self):
        if self.prevalenceCheckBox.instate(['selected']):
            self.prevalenceTextField.config(state=tk.NORMAL)
        else:
            self.prevalenceTextField.config(state=tk.DISABLED)

    def confirm(self):
        self.saved = True
        self.destroy()

    def cancel(self):
        self.saved = False
        self.destroy()

    def reset_attribute_count(self, in_attribute_count):
        current_count = len(self.tableModel.get_children())
        if current_count > in_attribute_count:
            for _ in range(current_count - in_attribute_count):
                self.tableModel.delete(self.tableModel.get_children()[-1])
        elif current_count < in_attribute_count:
            for _ in range(in_attribute_count - current_count):
                self.push_default_table_row()

    def push_default_table_row(self):
        self.tableModel.insert('', 'end', values=(self.get_next_default_attribute_name(), self.get_next_default_maf()))

    def create_labelled_text_field(self, parent, label_text, column_count):
        frame = ttk.Frame(parent)
        frame.pack(fill=tk.X)

        label = ttk.Label(frame, text=label_text)
        label.pack(side=tk.LEFT)

        text_field = ttk.Entry(frame, width=column_count)
        text_field.pack(side=tk.LEFT)
        
        return text_field

    def get_next_default_attribute_name(self):
        out_attribute_number = self.firstAttributeNumber
        for row in self.tableModel.get_children():
            attr_name = self.tableModel.item(row)['values'][0].lower()
            if attr_name.startswith('p'):
                try:
                    attr_number = int(attr_name[1:])
                    if out_attribute_number < attr_number + 1:
                        out_attribute_number = attr_number + 1
                except ValueError:
                    pass
        return f"P{out_attribute_number}"

    def get_next_default_maf(self):
        return 0.2

if __name__ == "__main__":
    root = tk.Tk()
    root.withdraw()
    dialog = GenerateModelDialog(root, 1, 1)
    root.mainloop()
