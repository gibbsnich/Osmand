package net.osmand.plus.views.mapwidgets;

import static net.osmand.plus.views.mapwidgets.WidgetsPanel.LEFT;
import static net.osmand.plus.views.mapwidgets.WidgetsPanel.RIGHT;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import net.osmand.plus.settings.backend.OsmandSettings;
import net.osmand.plus.views.mapwidgets.widgets.TextInfoWidget;

public class SideWidgetInfo extends MapWidgetInfo {

	private String externalProviderPackage;

	public SideWidgetInfo(@NonNull String key,
	                      @NonNull TextInfoWidget widget,
	                      @DrawableRes int daySettingsIconId,
	                      @DrawableRes int nightSettingsIconId,
	                      @StringRes int messageId,
	                      @Nullable String message,
	                      int page,
	                      int order,
	                      @NonNull WidgetsPanel widgetPanel) {
		super(key, widget, daySettingsIconId, nightSettingsIconId, messageId, message, page, order, widgetPanel);

		if (getMessage() != null) {
			widget.setContentTitle(getMessage());
		} else if (getMessageId() != MapWidgetInfo.INVALID_ID) {
			widget.setContentTitle(getMessageId());
		}
	}

	public void setExternalProviderPackage(@NonNull String externalProviderPackage) {
		this.externalProviderPackage = externalProviderPackage;
	}

	@Nullable
	public String getExternalProviderPackage() {
		return externalProviderPackage;
	}

	@NonNull
	@Override
	public WidgetsPanel getUpdatedPanel() {
		OsmandSettings settings = widget.getMyApplication().getSettings();
		WidgetType widgetType = getWidgetType();
		if (widgetType.defaultPanel == LEFT && RIGHT.contains(key, settings)) {
			widgetPanel = RIGHT;
		} else if (widgetType.defaultPanel == RIGHT && LEFT.contains(key, settings)) {
			widgetPanel = LEFT;
		} else {
			widgetPanel = widgetType.defaultPanel;
		}
		return widgetPanel;
	}
}