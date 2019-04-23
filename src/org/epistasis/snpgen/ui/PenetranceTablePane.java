package org.epistasis.snpgen.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.Arrays;

import javax.swing.AbstractAction;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

public class PenetranceTablePane extends JPanel implements FocusListener {
	private static final long serialVersionUID = 1L;

	private static final int alleleCount = 3;

	private int locusCount;
	Graphics graphics;
	String[] snpTitles;
	String[] snpMajorAlleles;
	String[] snpMinorAlleles;
	ModelUpdater updater;

	Square[] squares;

	public PenetranceTablePane(final ModelUpdater inUpdater, final Graphics graphics, final String[] inSnpTitles,
			final String[] inSnpMajorAlleles, final String[] inSnpMinorAlleles, final int inLocusCount) {
		super();

		updater = inUpdater;
		locusCount = inLocusCount;
		this.graphics = graphics;
		snpTitles = Arrays.copyOf(inSnpTitles, PenetranceTablePane.alleleCount);
		snpMajorAlleles = Arrays.copyOf(inSnpMajorAlleles, PenetranceTablePane.alleleCount);
		snpMinorAlleles = Arrays.copyOf(inSnpMinorAlleles, PenetranceTablePane.alleleCount);

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setAlignmentX(Component.CENTER_ALIGNMENT);

		createSquares(inLocusCount);
		updater.setSquares(squares);
		updater.modelToPenetranceSquares();
	}

	public void cellUpdated() {
		updater.updateModelUnlessInvalid();
	}

	public void createSquares(final int inLocusCount) {
		assert (locusCount == 2) || (locusCount == 3);
		// if(locusCount == 2)
		// {
		// squares = new Square[1];
		// squares[0] = new Square(graphics, topTitle, topMajorAllele,
		// topMinorAllele, leftTitle, leftMajorAllele, leftMinorAllele);
		// add(squares[0]);
		// }
		// else if(locusCount == 3)
		// {
		squares = new Square[PenetranceTablePane.alleleCount];
		for (int i = 0; i < PenetranceTablePane.alleleCount; ++i) {
			squares[i] = new Square(graphics, locusCount, i, snpTitles[0], snpMajorAlleles[0], snpMinorAlleles[0], snpTitles[1],
					snpMajorAlleles[1], snpMinorAlleles[1], snpTitles[2], snpMajorAlleles[2], snpMinorAlleles[2]);
			squares[i].setLocusCount(inLocusCount);
			if ((locusCount < 3) && (i >= 1)) {
				squares[i].setVisible(false);
			}
			add(squares[i]);
		}
		// }
	}

	@Override
	public void focusGained(final FocusEvent e) {
	}

	@Override
	public void focusLost(final FocusEvent e) {
		cellUpdated();
	}

	public void setLocusCount(final int inLocusCount) {
		if (locusCount != inLocusCount) {
			locusCount = inLocusCount;
			for (int i = 0; i < PenetranceTablePane.alleleCount; ++i) {
				squares[i].setLocusCount(inLocusCount);
				if ((locusCount < 3) && (i >= 1)) {
					squares[i].setVisible(false);
				} else {
					squares[i].setVisible(true);
				}
			}
		}
	}

	public class Row extends JPanel {
		private static final long serialVersionUID = 1L;
		JTextField[] items;

		public Row() {
			super();

			items = new JTextField[PenetranceTablePane.alleleCount];

			// setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
			// setAlignmentY(Component.CENTER_ALIGNMENT);

			// setLayout(new GridBagLayout());
			// GridBagConstraints c = new GridBagConstraints();
			//
			// for(int i = 0; i < itemCount; ++i)
			// {
			// items[i] = new JTextField();
			// // c.fill = GridBagConstraints.HORIZONTAL;
			// // c.weightx = 0.5;
			// c.gridx = i + 1;
			// c.gridy = 0;
			// add(items[i], c);
			// }

			setLayout(null);
			final Dimension overallSize = getPreferredSize();
			overallSize.width = 0;
			for (int i = 0; i < PenetranceTablePane.alleleCount; ++i) {
				items[i] = new JTextField(4);
				add(items[i]);
				final Insets insets = getInsets();
				final Dimension size = items[i].getPreferredSize();
				items[i].setBounds(insets.left + ((size.width + 5) * i), insets.top, size.width, size.height);
				overallSize.width += items[i].getPreferredSize().width + 5;
			}
			overallSize.height = items[0].getPreferredSize().height;
			setPreferredSize(overallSize);
		}
	}

	public class Square extends JPanel {
		private static final long serialVersionUID = 1L;
		JTextField[][] cells;
		JLabel rightLocusLabel;
		JLabel[] rightAlleleLabels;

		public Square(final Graphics graphics, final int squareCount, final int whichSquare, final String topLocus,
				final String topMajorAllele, final String topMinorAllele, final String leftLocus, final String leftMajorAllele,
				final String leftMinorAllele, final String rightLocus, final String rightMajorAllele, final String rightMinorAllele) {
			super();
			setLayout(null);

			final int leftMargin = 5; // margin to the left of everything
			final int rightMargin = 5; // margin to the right of everything
			final int topLocusMargin = 0;
			final int topAlleleMargin = 5;
			final int leftLocusMargin = 5; // margin to the right of the left
											// locus-label
			final int leftAlleleMargin = 5; // margin to the right of the left
											// allele-label
			final int rightAlleleMargin = 5; // margin to the left of the right
												// allele-label
			final int rightLocusMargin = 15; // margin to the left of the right
												// locus-label
			final int cellMarginX = 5;
			final int cellMarginY = 5;

			final JLabel topLocusLabel = new JLabel(topLocus);
			final JLabel leftLocusLabel = new JLabel(leftLocus);
			/* JLabel */rightLocusLabel = new JLabel(rightLocus);

			FontMetrics fontMetrics;
			Font font;

			font = topLocusLabel.getFont();
			fontMetrics = graphics.getFontMetrics(font);

			final int topLocusWidth = fontMetrics.stringWidth(topLocus);
			final int topLocusHeight = fontMetrics.getHeight();

			final int leftLocusWidth = fontMetrics.stringWidth(leftLocus);
			final int leftLocusHeight = fontMetrics.getHeight();
			final int rightLocusWidth = fontMetrics.stringWidth(rightLocus);
			final int rightLocusHeight = fontMetrics.getHeight();

			final String[] topAlleles = new String[PenetranceTablePane.alleleCount];
			topAlleles[0] = topMajorAllele + topMajorAllele;
			topAlleles[1] = topMajorAllele + topMinorAllele;
			topAlleles[2] = topMinorAllele + topMinorAllele;

			final JLabel[] topAlleleLabels = new JLabel[PenetranceTablePane.alleleCount];
			for (int i = 0; i < PenetranceTablePane.alleleCount; ++i) {
				topAlleleLabels[i] = new JLabel(topAlleles[i]);
			}
			final int topAlleleWidth = Math.max(Math.max(fontMetrics.stringWidth(topAlleles[0]), fontMetrics.stringWidth(topAlleles[1])),
					fontMetrics.stringWidth(topAlleles[2]));
			final int topAlleleHeight = fontMetrics.getHeight();

			final String[] leftAlleles = new String[PenetranceTablePane.alleleCount];
			leftAlleles[0] = leftMajorAllele + leftMajorAllele;
			leftAlleles[1] = leftMajorAllele + leftMinorAllele;
			leftAlleles[2] = leftMinorAllele + leftMinorAllele;

			final JLabel[] leftAlleleLabels = new JLabel[PenetranceTablePane.alleleCount];
			for (int i = 0; i < PenetranceTablePane.alleleCount; ++i) {
				leftAlleleLabels[i] = new JLabel(leftAlleles[i]);
			}
			final int leftAlleleWidth = Math.max(
					Math.max(fontMetrics.stringWidth(leftAlleles[0]), fontMetrics.stringWidth(leftAlleles[1])),
					fontMetrics.stringWidth(leftAlleles[2]));
			final int leftAlleleHeight = fontMetrics.getHeight();

			final String[] rightAlleles = new String[PenetranceTablePane.alleleCount];
			rightAlleles[0] = rightMajorAllele + rightMajorAllele;
			rightAlleles[1] = rightMajorAllele + rightMinorAllele;
			rightAlleles[2] = rightMinorAllele + rightMinorAllele;

			/* JLabel[] */rightAlleleLabels = new JLabel[PenetranceTablePane.alleleCount];
			for (int i = 0; i < PenetranceTablePane.alleleCount; ++i) {
				rightAlleleLabels[i] = new JLabel(rightAlleles[i]);
			}
			final int rightAlleleWidth = Math.max(
					Math.max(fontMetrics.stringWidth(rightAlleles[0]), fontMetrics.stringWidth(rightAlleles[1])),
					fontMetrics.stringWidth(rightAlleles[2]));
			final int rightAlleleHeight = fontMetrics.getHeight();

			final JTextField template = new JTextField(4);
			final int itemWidth = Math.max(topAlleleWidth, template.getPreferredSize().width);
			final int itemHeight = Math.max(leftAlleleHeight, template.getPreferredSize().height);

			final int[] cellX = new int[PenetranceTablePane.alleleCount + 1];
			final int[] cellY = new int[PenetranceTablePane.alleleCount + 1];

			// System.out.println("topTitleWidth=" + topTitleWidth +
			// "\ttopTitleHeight=" + topTitleHeight + "\tleftTitleWidth=" +
			// leftTitleWidth + "\tleftTitleHeight=" + leftTitleHeight);
			// System.out.println("topAlleleWidth=" + topAlleleWidth +
			// "\ttopAlleleHeight=" + topAlleleHeight + "\tleftAlleleWidth=" +
			// leftAlleleWidth + "\tleftAlleleHeight=" + leftAlleleHeight);

			for (int i = 0; i < (PenetranceTablePane.alleleCount + 1); ++i) {
				cellX[i] = leftMargin + leftLocusWidth + leftLocusMargin + leftAlleleWidth + leftAlleleMargin
						+ (i * (itemWidth + cellMarginX));
				// System.out.print(cellX[i] + "\t");
			}
			// System.out.println();
			for (int j = 0; j < (PenetranceTablePane.alleleCount + 1); ++j) {
				cellY[j] = topLocusHeight + topLocusMargin + topAlleleHeight + topAlleleMargin + (j * (itemHeight + cellMarginY));
				// System.out.print(cellY[j] + "\t");
			}
			// System.out.println();

			final int rightAlleleX = cellX[PenetranceTablePane.alleleCount] + rightAlleleMargin;
			final int rightLocusX = rightAlleleX + rightAlleleWidth + rightLocusMargin;

			final int leftHeaderWidth = leftMargin + leftLocusWidth + leftLocusMargin + leftAlleleWidth + leftAlleleMargin;
			final int topHeaderHeight = topLocusHeight + topLocusMargin + topAlleleHeight + topAlleleMargin;
			final int tableWidth = (PenetranceTablePane.alleleCount * itemWidth) + ((PenetranceTablePane.alleleCount - 1) * cellMarginX);
			final int tableHeight = (PenetranceTablePane.alleleCount * itemHeight) + ((PenetranceTablePane.alleleCount - 1) * cellMarginY);
			final int totalWidth = rightLocusX + rightLocusWidth + rightMargin;
			final int totalHeight = topHeaderHeight + tableHeight;

			final int topTitleLeft = leftHeaderWidth + ((tableWidth - topLocusWidth) / 2);
			add(topLocusLabel);
			topLocusLabel.setBounds(topTitleLeft, 0, topLocusWidth, topLocusHeight);

			final int leftTitleTop = topHeaderHeight + ((tableHeight - leftLocusHeight) / 2);
			add(leftLocusLabel);
			leftLocusLabel.setBounds(leftMargin, leftTitleTop, leftLocusWidth, leftLocusHeight);

			// System.out.println("topTitleLeft=" + topTitleLeft +
			// "\tleftTitleTop=" + leftTitleTop);
			// System.out.println();
			// System.out.println();

			for (int i = 0; i < PenetranceTablePane.alleleCount; ++i) {
				add(topAlleleLabels[i]);
				topAlleleLabels[i].setBounds(cellX[i] + 6, topLocusHeight + topLocusMargin, itemWidth, itemHeight);
			}
			for (int j = 0; j < PenetranceTablePane.alleleCount; ++j) {
				add(leftAlleleLabels[j]);
				leftAlleleLabels[j].setBounds(leftMargin + leftLocusWidth + leftLocusMargin, cellY[j], itemWidth, itemHeight);
			}

			// if(squareCount >= 3)
			{
				// JSeparator rightAlleleSeparator = new
				// JSeparator(SwingConstants.VERTICAL);
				// add(rightAlleleSeparator);
				// rightAlleleSeparator.setVisible(true);
				// rightAlleleSeparator.setBounds(rightAlleleX, cellY[1],
				// rightAlleleSeparatorWidth, rightAlleleSeparatorHeight);
				// rightAlleleSeparator.setPreferredSize(new
				// Dimension(rightAlleleSeparatorWidth,
				// rightAlleleSeparatorHeight));
				// rightAlleleSeparator.setMinimumSize(new
				// Dimension(rightAlleleSeparatorWidth,
				// rightAlleleSeparatorHeight));

				add(rightAlleleLabels[whichSquare]);
				rightAlleleLabels[whichSquare].setBounds(rightAlleleX, cellY[1], rightAlleleWidth, rightAlleleHeight);
				if (whichSquare == 1) {
					add(rightLocusLabel);
					rightLocusLabel.setBounds(rightLocusX, cellY[1], rightLocusWidth, rightLocusHeight);
				}
			}

			cells = new JTextField[PenetranceTablePane.alleleCount][PenetranceTablePane.alleleCount];
			// "Row i, column j" means that i specifies the y-coordinate and j
			// specifies the x-coordinate.
			for (int i = 0; i < PenetranceTablePane.alleleCount; ++i) {
				for (int j = 0; j < PenetranceTablePane.alleleCount; ++j) {
					cells[i][j] = new JTextField(4);
					cells[i][j].addFocusListener(PenetranceTablePane.this);
					add(cells[i][j]);
					// Insets insets = getInsets();
					cells[i][j].setBounds(cellX[j], cellY[i], itemWidth, itemHeight);
					cells[i][j].getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0),
							"doUpdate");
					cells[i][j].getActionMap().put("doUpdate", new UpdateAction());
				}
			}
			final Dimension overallSize = new Dimension(totalWidth, totalHeight);
			setPreferredSize(overallSize);
		}

		public void setLocusCount(final int inLocusCount) {
			if (inLocusCount < 3) {
				rightLocusLabel.setVisible(false);
				for (final JLabel alleleLabel : rightAlleleLabels) {
					alleleLabel.setVisible(false);
				}
			} else {
				rightLocusLabel.setVisible(true);
				for (final JLabel alleleLabel : rightAlleleLabels) {
					alleleLabel.setVisible(true);
				}
			}
		}
	}

	public class UpdateAction extends AbstractAction {
		private static final long serialVersionUID = 1L;

		public UpdateAction() {
			super();
		}

		@Override
		public void actionPerformed(final ActionEvent e) {
			cellUpdated();
		}
	}
}
