package ru.bulldog.justmap.advancedinfo;

import ru.bulldog.justmap.client.config.ClientSettings;
import ru.bulldog.justmap.enums.TextAlignment;
import ru.bulldog.justmap.util.CurrentWorldPos;
import ru.bulldog.justmap.util.PosUtil;

public class CoordsInfo extends InfoText {

	public CoordsInfo(TextAlignment alignment, String text) {
		super(alignment, text);
	}

	@Override
	public void updateOnTick() {
		this.setVisible(ClientSettings.showPosition);
		if (visible) {
			this.setText(PosUtil.posToString(CurrentWorldPos.currentPos()));
		}
	}
}
