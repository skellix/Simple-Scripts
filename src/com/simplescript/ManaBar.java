package com.simplescript;

import javax.swing.*;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;

/**
 * Created by Alex on 4/26/14.
 */
public class ManaBar extends JFrame {

	private final String name;
	private final byte[] data;
	private StyledDocument styledDocument;

	public ManaBar(String name, byte[] data) {
		this.name = name;
		this.data = data;
		init();
	}

	private void init() {
		add(
				new JScrollPane(
						new JTextPane() {{
							setBackground(Color.BLACK);
							setText(new String(data));
							setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
							styledDocument = getStyledDocument();
						}}
				)
		);
		//Random random = new Random(Objects.hash(data));
		for (int i = 0 ; i < data.length ; i ++) {
			String styleName = "s"+i;
			Style style = styledDocument.addStyle(styleName, null);
			Color color = Color.BLACK;
			if (data[i] > 32 && data[i] < 127) {
				color = Color.BLUE;
			} else if (data[i] > 127) {
				color = new Color(
						(int) ((data[i] / (float) Byte.MAX_VALUE) * Integer.MAX_VALUE)
				);
			}//*/
			StyleConstants.setBackground(
					style,
					color
			);
			StyleConstants.setForeground(
					style,
					new Color(
							Color.WHITE.getRGB() ^ color.getRGB()
					)
			);
			styledDocument.setCharacterAttributes(i, 1, style, true);
		}
		setSize(600, 600);
		setLocationRelativeTo(null);
		setVisible(true);
	}
}
