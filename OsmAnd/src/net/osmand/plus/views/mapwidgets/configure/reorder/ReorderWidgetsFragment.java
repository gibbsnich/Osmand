package net.osmand.plus.views.mapwidgets.configure.reorder;

import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import com.google.android.material.snackbar.Snackbar;

import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.base.BaseOsmAndFragment;
import net.osmand.plus.helpers.AndroidUiHelper;
import net.osmand.plus.profiles.SelectCopyAppModeBottomSheet;
import net.osmand.plus.profiles.SelectCopyAppModeBottomSheet.CopyAppModePrefsListener;
import net.osmand.plus.settings.backend.ApplicationMode;
import net.osmand.plus.settings.backend.OsmandSettings;
import net.osmand.plus.settings.bottomsheets.ResetProfilePrefsBottomSheet;
import net.osmand.plus.settings.bottomsheets.ResetProfilePrefsBottomSheet.ResetAppModePrefsListener;
import net.osmand.plus.utils.AndroidUtils;
import net.osmand.plus.utils.ColorUtilities;
import net.osmand.plus.utils.UiUtilities;
import net.osmand.plus.utils.UiUtilities.DialogButtonType;
import net.osmand.plus.views.controls.ReorderItemTouchHelperCallback;
import net.osmand.plus.views.layers.MapInfoLayer;
import net.osmand.plus.views.mapwidgets.MapWidgetInfo;
import net.osmand.plus.views.mapwidgets.MapWidgetRegistry;
import net.osmand.plus.views.mapwidgets.WidgetsPanel;
import net.osmand.plus.views.mapwidgets.configure.reorder.ReorderWidgetsAdapter.ItemType;
import net.osmand.plus.views.mapwidgets.configure.reorder.ReorderWidgetsAdapter.ListItem;
import net.osmand.plus.views.mapwidgets.configure.reorder.ReorderWidgetsAdapter.WidgetAdapterListener;
import net.osmand.plus.views.mapwidgets.configure.reorder.viewholder.ActionButtonViewHolder.ActionButtonInfo;
import net.osmand.plus.views.mapwidgets.configure.reorder.viewholder.AddedWidgetViewHolder.AddedWidgetUiInfo;
import net.osmand.plus.views.mapwidgets.configure.reorder.viewholder.AvailableWidgetViewHolder.AvailableWidgetUiInfo;
import net.osmand.plus.views.mapwidgets.configure.reorder.viewholder.PageViewHolder.PageUiInfo;
import net.osmand.util.Algorithms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static net.osmand.plus.views.mapwidgets.MapWidgetRegistry.AVAILABLE_MODE;

public class ReorderWidgetsFragment extends BaseOsmAndFragment implements CopyAppModePrefsListener, ResetAppModePrefsListener {

	public static final String TAG = ReorderWidgetsFragment.class.getSimpleName();

	private static final String APP_MODE_ATTR = "app_mode_key";

	private OsmandApplication app;
	private OsmandSettings settings;
	private MapWidgetRegistry widgetRegistry;

	private ApplicationMode selectedAppMode;
	private final WidgetsDataHolder dataHolder = new WidgetsDataHolder();

	private View view;
	private Toolbar toolbar;
	private RecyclerView recyclerView;

	private ReorderWidgetsAdapter adapter;

	private boolean nightMode;

	public void setSelectedAppMode(@NonNull ApplicationMode selectedAppMode) {
		this.selectedAppMode = selectedAppMode;
	}

	public void setSelectedPanel(@NonNull WidgetsPanel selectedPanel) {
		dataHolder.setSelectedPanel(selectedPanel);
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		app = requireMyApplication();
		settings = app.getSettings();
		widgetRegistry = app.getOsmandMap().getMapLayers().getMapWidgetRegistry();

		if (savedInstanceState != null) {
			String appModeKey = savedInstanceState.getString(APP_MODE_ATTR);
			selectedAppMode = ApplicationMode.valueOfStringKey(appModeKey, settings.getApplicationMode());
			dataHolder.restoreData(savedInstanceState);
		} else {
			dataHolder.initOrders(app, selectedAppMode, false);
		}
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		nightMode = !settings.isLightContent();

		LayoutInflater themedInflater = UiUtilities.getInflater(getContext(), nightMode);
		view = themedInflater.inflate(R.layout.fragment_reorder_widgets, container, false);
		if (Build.VERSION.SDK_INT < 30) {
			AndroidUtils.addStatusBarPadding21v(app, view);
		}

		toolbar = view.findViewById(R.id.toolbar);
		recyclerView = view.findViewById(R.id.content_list);

		setupToolbar();
		setupContent();
		setupApplyButton();

		return view;
	}

	private void setupToolbar() {
		TextView title = toolbar.findViewById(R.id.toolbar_title);
		TextView subtitle = toolbar.findViewById(R.id.toolbar_subtitle);

		title.setText(dataHolder.getSelectedPanel().getTitleId());
		subtitle.setText(selectedAppMode.toHumanString());

		toolbar.findViewById(R.id.back_button).setOnClickListener(v -> dismiss());
		toolbar.findViewById(R.id.reset_button).setOnClickListener(v -> resetChanges());
		toolbar.findViewById(R.id.copy_button).setOnClickListener(v -> copyFromProfile());
	}

	private void setupContent() {
		recyclerView.setLayoutManager(new LinearLayoutManager(app));
		adapter = new ReorderWidgetsAdapter(app, dataHolder, nightMode);

		ItemTouchHelper touchHelper = new ItemTouchHelper(new ReorderItemTouchHelperCallback(adapter));
		touchHelper.attachToRecyclerView(recyclerView);

		adapter.setListener(new WidgetAdapterListener() {
			private int fromPosition;

			@Override
			public void onDragStarted(ViewHolder holder) {
				fromPosition = holder.getAdapterPosition();
				touchHelper.startDrag(holder);
			}

			@Override
			public void onDragOrSwipeEnded(ViewHolder holder) {
				int toPosition = holder.getAdapterPosition();
				if (toPosition >= 0 && fromPosition >= 0 && toPosition != fromPosition) {
					adapter.notifyDataSetChanged();
				}
			}

			@Override
			public void onPageDeleted(int page, int position) {
				String snackbarText = getString(R.string.snackbar_page_removed, String.valueOf(page + 1));
				Snackbar snackbar = Snackbar.make(view, snackbarText, Snackbar.LENGTH_LONG)
						.setAnchorView(R.id.apply_button_container)
						.setAction(R.string.shared_string_undo, v -> adapter.restorePage(page, position));
				UiUtilities.setupSnackbar(snackbar, nightMode);
				snackbar.show();
			}
		});
		recyclerView.setAdapter(adapter);
		updateItems();
	}

	private void setupApplyButton() {
		View applyButton = view.findViewById(R.id.apply_button);
		UiUtilities.setupDialogButton(nightMode, applyButton, DialogButtonType.PRIMARY, R.string.shared_string_apply);
		applyButton.setOnClickListener(v -> onApplyChanges());
	}

	private void dismiss() {
		FragmentActivity activity = getActivity();
		if (activity != null) {
			activity.onBackPressed();
		}
	}

	private void copyFromProfile() {
		FragmentActivity activity = getActivity();
		if (activity != null) {
			SelectCopyAppModeBottomSheet.showInstance(activity.getSupportFragmentManager(), this, false, selectedAppMode);
		}
	}

	private void onApplyChanges() {
		applyWidgetsConfiguration();

		Fragment fragment = getTargetFragment();
		if (fragment instanceof WidgetsOrderListener) {
			((WidgetsOrderListener) fragment).onWidgetsOrderApplied();
		}
		dismiss();
	}

	private void resetChanges() {
		FragmentManager fragmentManager = getFragmentManager();
		if (fragmentManager != null) {
			ResetProfilePrefsBottomSheet.showInstance(fragmentManager, selectedAppMode, this, false);
		}
	}

	private void updateItems() {
		List<ListItem> items = new ArrayList<>();
		items.add(new ListItem(ItemType.CARD_TOP_DIVIDER, null));
		items.add(new ListItem(ItemType.HEADER, getString(R.string.shared_string_visible_widgets)));
		items.addAll(createEnabledWidgetsList());
		if (dataHolder.getSelectedPanel().isPagingAllowed()) {
			items.add(new ListItem(ItemType.ADD_PAGE_BUTTON, null));
		}
		items.add(new ListItem(ItemType.CARD_DIVIDER, null));

		List<ListItem> availableWidgets = createAvailableWidgetsList();
		if (!Algorithms.isEmpty(availableWidgets)) {
			items.add(new ListItem(ItemType.HEADER, getString(R.string.available_widgets)));
			items.addAll(availableWidgets);
			items.add(new ListItem(ItemType.CARD_DIVIDER, null));
		}

		items.add(new ListItem(ItemType.HEADER, getString(R.string.shared_string_actions)));
		items.add(new ListItem(ItemType.ACTION_BUTTON, new ActionButtonInfo(
				getString(R.string.reset_to_default),
				R.drawable.ic_action_reset,
				v -> resetChanges()
		)));
		items.add(new ListItem(ItemType.ACTION_BUTTON, new ActionButtonInfo(
				getString(R.string.copy_from_other_profile),
				R.drawable.ic_action_copy,
				v -> copyFromProfile()
		)));
		items.add(new ListItem(ItemType.CARD_BOTTOM_DIVIDER, null));
		items.add(new ListItem(ItemType.SPACE, getResources().getDimensionPixelSize(R.dimen.bottom_space_height)));
		adapter.setItems(items);
	}

	private List<ListItem> createEnabledWidgetsList() {
		List<ListItem> widgetsItems = new ArrayList<>();

		WidgetsPanel selectedPanel = dataHolder.getSelectedPanel();
		Set<MapWidgetInfo> widgets = widgetRegistry.getWidgetsForPanel(selectedAppMode, selectedPanel, AVAILABLE_MODE);
		for (MapWidgetInfo widgetInfo : widgets) {
			boolean enabled = dataHolder.getOrders().containsKey(widgetInfo.key);
			if (!enabled) {
				continue;
			}

			Map<String, Integer> orders = dataHolder.getOrders();
			int page = dataHolder.getWidgetPage(widgetInfo.key);
			Integer order = orders.get(widgetInfo.key);
			if (page == -1) {
				page = widgetInfo.pageIndex;
				dataHolder.addWidgetToPage(widgetInfo.key, page);
			}
			if (order == null) {
				order = widgetInfo.priority;
				orders.put(widgetInfo.key, order);
			}
			AddedWidgetUiInfo info = new AddedWidgetUiInfo();
			info.key = widgetInfo.key;
			info.title = widgetInfo.getTitle(app);
			info.iconId = widgetInfo.getMapIconId(nightMode);
			info.page = page;
			info.order = order;
			info.info = widgetInfo;
			widgetsItems.add(new ListItem(ItemType.ADDED_WIDGET, info));
		}
		Collections.sort(widgetsItems, (o1, o2) -> {
			AddedWidgetUiInfo info1 = ((AddedWidgetUiInfo) o1.value);
			AddedWidgetUiInfo info2 = ((AddedWidgetUiInfo) o2.value);
			if (info1 == null || info2 == null) {
				return 0;
			} else if (info1.page != info2.page) {
				return Integer.compare(info1.page, info2.page);
			}
			return Integer.compare(info1.order, info2.order);
		});


		boolean pagingAllowed = dataHolder.getSelectedPanel().isPagingAllowed();
		return pagingAllowed ? getPagedWidgetItems(widgetsItems) : widgetsItems;
	}

	@NonNull
	private List<ListItem> createAvailableWidgetsList() {
		List<ListItem> widgetsItems = new ArrayList<>();

		WidgetsPanel selectedPanel = dataHolder.getSelectedPanel();
		Set<MapWidgetInfo> widgets = widgetRegistry.getWidgetsForPanel(selectedAppMode, selectedPanel, AVAILABLE_MODE);

		for (MapWidgetInfo widgetInfo : widgets) {
			boolean enabled = dataHolder.getOrders().containsKey(widgetInfo.key);
			if (enabled) {
				continue;
			}

			int order = selectedPanel.getOriginalWidgetOrder(widgetInfo.key);
			AvailableWidgetUiInfo info = new AvailableWidgetUiInfo();
			info.key = widgetInfo.key;
			info.title = widgetInfo.getTitle(app);
			info.iconId = widgetInfo.getMapIconId(nightMode);
			info.order = order;
			info.info = widgetInfo;
			widgetsItems.add(new ListItem(ItemType.AVAILABLE_WIDGET, info));
		}

		Collections.sort(widgetsItems, (o1, o2) -> {
			AvailableWidgetUiInfo info1 = ((AvailableWidgetUiInfo) o1.value);
			AvailableWidgetUiInfo info2 = ((AvailableWidgetUiInfo) o2.value);
			return info1 == null || info2 == null ? 0 : Integer.compare(info1.order, info2.order);
		});

		return widgetsItems;
	}

	@NonNull
	private List<ListItem> getPagedWidgetItems(@NonNull List<ListItem> widgetsItems) {
		List<ListItem> pagedWidgetsItems = new ArrayList<>();
		for (int page : dataHolder.getPages().keySet()) {

			pagedWidgetsItems.add(new ListItem(ItemType.PAGE, new PageUiInfo(page)));

			for (ListItem widgetItem : widgetsItems) {
				int widgetPage = widgetItem.value instanceof AddedWidgetUiInfo
						? ((AddedWidgetUiInfo) widgetItem.value).page
						: -1;
				if (widgetPage == page) {
					pagedWidgetsItems.add(widgetItem);
				}
			}
		}
		return pagedWidgetsItems;
	}

	private void applyWidgetsConfiguration() {
		List<ListItem> items = adapter.getItems();
		Map<Integer, List<String>> pagedOrder = new TreeMap<>();
		List<String> enabledWidgetsIds = new ArrayList<>();

		for (ListItem item : items) {
			boolean isWidget = item.value instanceof AddedWidgetUiInfo;
			if (!isWidget) {
				continue;
			}

			AddedWidgetUiInfo widgetInfo = (AddedWidgetUiInfo) item.value;

			enabledWidgetsIds.add(widgetInfo.key);

			List<String> widgetsOrder = pagedOrder.get(widgetInfo.page);
			if (widgetsOrder == null) {
				widgetsOrder = new ArrayList<>();
				pagedOrder.put(widgetInfo.page, widgetsOrder);
			}
			widgetsOrder.add(widgetInfo.key);
		}

		applyWidgetsVisibility(enabledWidgetsIds);
		applyWidgetsOrder(new ArrayList<>(pagedOrder.values()));
		MapInfoLayer mapInfoLayer = app.getOsmandMap().getMapLayers().getMapInfoLayer();
		if (mapInfoLayer != null) {
			mapInfoLayer.recreateControls();
		}
	}

	private void applyWidgetsVisibility(@NonNull List<String> enabledWidgetsIds) {
		WidgetsPanel panel = dataHolder.getSelectedPanel();
		for (MapWidgetInfo widget : widgetRegistry.getWidgetsForPanel(panel)) {
			boolean enabledFromApply = enabledWidgetsIds.contains(widget.key);
			if (widget.isEnabledForAppMode(selectedAppMode) != enabledFromApply) {
				widgetRegistry.setVisibility(widget, enabledFromApply);
			}
		}
	}

	private void applyWidgetsOrder(@NonNull List<List<String>> pagedOrder) {
		WidgetsPanel selectedPanel = dataHolder.getSelectedPanel();
		selectedPanel.setWidgetsOrder(selectedAppMode, pagedOrder, settings);
		widgetRegistry.reorderWidgets();
	}

	@Override
	public void copyAppModePrefs(ApplicationMode appMode) {
		dataHolder.initOrders(app, appMode, false);
		updateItems();
	}

	@Override
	public void resetAppModePrefs(ApplicationMode appMode) {
		dataHolder.initOrders(app, selectedAppMode, true);
		updateItems();
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		dataHolder.onSaveInstanceState(outState);
		outState.putString(APP_MODE_ATTR, selectedAppMode.getStringKey());
	}

	@Override
	public int getStatusBarColorId() {
		AndroidUiHelper.setStatusBarContentColor(getView(), nightMode);
		return ColorUtilities.getListBgColorId(nightMode);
	}

	public static void showInstance(@NonNull FragmentManager manager,
	                                @NonNull WidgetsPanel panel,
	                                @NonNull ApplicationMode appMode,
	                                @NonNull Fragment target) {
		if (AndroidUtils.isFragmentCanBeAdded(manager, TAG)) {
			ReorderWidgetsFragment fragment = new ReorderWidgetsFragment();
			fragment.setTargetFragment(target, 0);
			fragment.setSelectedPanel(panel);
			fragment.setSelectedAppMode(appMode);
			manager.beginTransaction()
					.add(R.id.fragmentContainer, fragment, TAG)
					.addToBackStack(TAG)
					.commitAllowingStateLoss();
		}
	}

	public interface WidgetsOrderListener {
		void onWidgetsOrderApplied();
	}
}