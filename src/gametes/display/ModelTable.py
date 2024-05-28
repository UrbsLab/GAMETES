import tkinter as tk
from tkinter import ttk

class ModelTable(ttk.Treeview):
    def __init__(self, parent, columns, document):
        super().__init__(parent, columns=columns, show='headings')
        self.document = document
        self.selected = []
        self.updateListeners = []
        self.widgets = [None] * len(columns)
        
        for i, col in enumerate(columns):
            self.heading(col, text=col)
            self.column(col, anchor=tk.CENTER)
        
        self.populate_table()
        self.bind("<ButtonRelease-1>", self.on_click)

    def populate_table(self):
        for model in self.document.modelList:
            values = [
                model.modelId,
                model.attributeCount,
                model.heritability,
                ','.join(model.attributeNameArray),
                ','.join(map(str, model.attributeAlleleFrequencyArray)),
                model.fraction,
                model.getQuantileCountInModel(),
                False  # default selection state
            ]
            self.insert('', tk.END, values=values)
            self.selected.append(False)

    def on_click(self, event):
        selected_item = self.selection()
        if selected_item:
            item = self.item(selected_item)
            col = self.identify_column(event.x)
            row = self.identify_row(event.y)
            if col == '#8':  # Checkbox column
                index = self.index(selected_item)
                current_state = self.selected[index]
                self.selected[index] = not current_state
                self.update_selection()
                self.item(selected_item, values=[*item['values'][:7], self.selected[index]])

    def update_selection(self):
        for listener in self.updateListeners:
            listener.update_selection()

    def get_selected_models(self):
        return [index for index, selected in enumerate(self.selected) if selected]

    def set_selected(self, which_model, selected):
        self.selected[which_model] = selected

class Document:
    class DocModel:
        def __init__(self, modelId, attributeCount, heritability, attributeNameArray, attributeAlleleFrequencyArray, fraction):
            self.modelId = modelId
            self.attributeCount = attributeCount
            self.heritability = heritability
            self.attributeNameArray = attributeNameArray
            self.attributeAlleleFrequencyArray = attributeAlleleFrequencyArray
            self.fraction = fraction
        
        def getQuantileCountInModel(self):
            # Placeholder for actual implementation
            return len(self.attributeNameArray)

    def __init__(self):
        self.modelList = [
            self.DocModel("Model1", 5, 0.2, ["SNP1", "SNP2"], [0.1, 0.2], 0.5),
            self.DocModel("Model2", 3, 0.3, ["SNP3", "SNP4", "SNP5"], [0.3, 0.4, 0.5], 0.6)
        ]

class UpdateListener:
    def update_selection(self):
        print("Selection updated")

if __name__ == "__main__":
    root = tk.Tk()
    document = Document()
    columns = ["Model", "# Attributes", "Heritability", "SNPs", "Minor allele freq", "Heterogeneity proportion", "# Quantiles", "Selected"]
    table = ModelTable(root, columns, document)
    table.pack(fill=tk.BOTH, expand=True)

    listener = UpdateListener()
    table.updateListeners.append(listener)

    root.mainloop()
