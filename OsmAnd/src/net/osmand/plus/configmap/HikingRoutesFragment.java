package net.osmand.plus.configmap;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.base.BaseOsmAndFragment;
import net.osmand.plus.helpers.AndroidUiHelper;
import net.osmand.plus.settings.backend.OsmandSettings;
import net.osmand.plus.settings.backend.preferences.CommonPreference;
import net.osmand.plus.utils.AndroidUtils;
import net.osmand.plus.utils.UiUtilities;
import net.osmand.plus.widgets.multistatetoggle.RadioItem;
import net.osmand.plus.widgets.multistatetoggle.RadioItem.OnRadioItemClickListener;
import net.osmand.plus.widgets.multistatetoggle.TextToggleButton;
import net.osmand.plus.widgets.multistatetoggle.TextToggleButton.TextRadioItem;
import net.osmand.render.RenderingRuleProperty;
import net.osmand.util.Algorithms;

import java.util.ArrayList;
import java.util.List;

import static net.osmand.plus.configmap.ConfigureMapMenu.HIKING_ROUTES_OSMC_ATTR;

public class HikingRoutesFragment extends BaseOsmAndFragment {

	public static final String TAG = HikingRoutesFragment.class.getSimpleName();

	private CommonPreference<String> pref;
	@Nullable
	private RenderingRuleProperty property;
	private String previousValue;

	@Override
	protected boolean isUsedOnMap() {
		return true;
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		pref = settings.getCustomRenderProperty(HIKING_ROUTES_OSMC_ATTR);
		property = app.getRendererRegistry().getCustomRenderingRuleProperty(HIKING_ROUTES_OSMC_ATTR);
		if (property == null) {
			previousValue = pref.get();
		} else {
			previousValue = isEnabled() ? pref.get() : property.getPossibleValues()[0];
		}
	}

	private boolean isEnabled() {
		if (property != null) {
			for (String value : property.getPossibleValues()) {
				if (Algorithms.stringsEqual(value, pref.get())) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		MapActivity mapActivity = (MapActivity) requireMyActivity();
		LayoutInflater themedInflater = UiUtilities.getInflater(mapActivity, nightMode);
		View view = themedInflater.inflate(R.layout.map_route_types_fragment, container, false);

		showHideTopShadow(view);
		setupHeader(view);
		setupTypesCard(view);

		return view;
	}

	private void showHideTopShadow(@NonNull View view) {
		boolean portrait = AndroidUiHelper.isOrientationPortrait(requireActivity());
		AndroidUiHelper.updateVisibility(view.findViewById(R.id.shadow_on_map), portrait);
	}

	private void setupHeader(@NonNull View view) {
		View container = view.findViewById(R.id.preference_container);

		TextView title = container.findViewById(R.id.title);
		ImageView icon = container.findViewById(R.id.icon);
		TextView description = container.findViewById(R.id.description);

		boolean enabled = isEnabled();
		int selectedColor = settings.getApplicationMode().getProfileColor(nightMode);
		int disabledColor = AndroidUtils.getColorFromAttr(view.getContext(), R.attr.default_icon_color);

		title.setText(R.string.rendering_attr_hikingRoutesOSMC_name);
		icon.setImageDrawable(getPaintedContentIcon(R.drawable.ic_action_trekking_dark, enabled ? selectedColor : disabledColor));
		description.setText(enabled ? R.string.shared_string_enabled : R.string.shared_string_disabled);

		CompoundButton button = container.findViewById(R.id.toggle_item);
		button.setClickable(false);
		button.setFocusable(false);
		button.setChecked(enabled);
		UiUtilities.setupCompoundButton(nightMode, selectedColor, button);

		container.setOnClickListener(v -> {
			pref.set(!button.isChecked() ? previousValue : "");
			setupHeader(view);
			setupTypesCard(view);
			refreshMap();
		});
		AndroidUiHelper.updateVisibility(container.findViewById(R.id.divider), false);
		AndroidUiHelper.updateVisibility(container.findViewById(R.id.secondary_icon), false);
	}

	private void setupTypesCard(@NonNull View view) {
		View container = view.findViewById(R.id.card_container);

		boolean enabled = property != null && isEnabled();
		if (enabled) {
			TextRadioItem selectedItem = null;
			List<TextRadioItem> items = new ArrayList<>();
			for (String value : property.getPossibleValues()) {
				TextRadioItem item = createRadioButton(value);
				if (Algorithms.stringsEqual(value, pref.get())) {
					selectedItem = item;
				}
				items.add(item);
			}

			TextView title = container.findViewById(R.id.title);
			TextView description = container.findViewById(R.id.description);

			title.setText(R.string.routes_color_by_type);
			description.setText(AndroidUtils.getRenderingStringPropertyDescription(app, pref.get()));

			LinearLayout radioButtonsContainer = view.findViewById(R.id.custom_radio_buttons);
			TextToggleButton radioGroup = new TextToggleButton(app, radioButtonsContainer, nightMode, true);
			radioGroup.setItems(items);
			radioGroup.setSelectedItem(selectedItem);
		}
		AndroidUiHelper.updateVisibility(container, enabled);
		AndroidUiHelper.updateVisibility(container.findViewById(R.id.descr), false);
		AndroidUiHelper.updateVisibility(view.findViewById(R.id.topShadowView), enabled);
		AndroidUiHelper.updateVisibility(view.findViewById(R.id.card_bottom_divider), enabled);
	}

	private TextRadioItem createRadioButton(@NonNull String value) {
		String name = AndroidUtils.getRenderingStringPropertyValue(app, value);
		TextRadioItem item = new TextRadioItem(name);
		item.setOnClickListener((radioItem, v) -> {
			pref.set(value);
			previousValue = value;

			View view = getView();
			if (view != null) {
				setupHeader(view);
				setupTypesCard(view);
			}
			refreshMap();
			return true;
		});
		return item;
	}

	private void refreshMap() {
		MapActivity mapActivity = (MapActivity) getMyActivity();
		if (mapActivity != null) {
			mapActivity.refreshMapComplete();
			mapActivity.updateLayers();
		}
	}

	public static void showInstance(@NonNull FragmentManager fragmentManager) {
		if (AndroidUtils.isFragmentCanBeAdded(fragmentManager, TAG)) {
			fragmentManager.beginTransaction()
					.replace(R.id.content, new HikingRoutesFragment(), TAG)
					.commitAllowingStateLoss();
		}
	}
}