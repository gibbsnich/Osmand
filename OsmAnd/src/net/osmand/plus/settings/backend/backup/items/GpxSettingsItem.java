package net.osmand.plus.settings.backend.backup.items;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.gpx.GPXFile;
import net.osmand.gpx.GPXUtilities;
import net.osmand.IndexConstants;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.track.helpers.GpxUiHelper;
import net.osmand.plus.settings.backend.backup.FileSettingsItemReader;
import net.osmand.plus.settings.backend.backup.GpxAppearanceInfo;
import net.osmand.plus.settings.backend.backup.SettingsItemReader;
import net.osmand.plus.settings.backend.backup.SettingsItemType;
import net.osmand.plus.track.GpxSelectionParams;
import net.osmand.plus.track.GpxSplitType;
import net.osmand.plus.track.helpers.GPXDatabase.GpxDataItem;
import net.osmand.plus.track.helpers.GpxDbHelper;
import net.osmand.plus.track.helpers.GpxDbHelper.GpxDataItemCallback;
import net.osmand.plus.track.helpers.GpxSelectionHelper;
import net.osmand.plus.track.helpers.SelectedGpxFile;
import net.osmand.plus.utils.FileUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class GpxSettingsItem extends FileSettingsItem {

	private GpxAppearanceInfo appearanceInfo;

	public GpxSettingsItem(@NonNull OsmandApplication app, @NonNull File file) throws IllegalArgumentException {
		super(app, file);
		createGpxAppearanceInfo();
	}

	public GpxSettingsItem(@NonNull OsmandApplication app, @NonNull JSONObject json) throws JSONException {
		super(app, json);
		updateFile();
	}

	@Nullable
	public GpxAppearanceInfo getAppearanceInfo() {
		return appearanceInfo;
	}

	@NonNull
	@Override
	public SettingsItemType getType() {
		return SettingsItemType.GPX;
	}

	@NonNull
	@Override
	public String getPublicName(@NonNull Context ctx) {
		return GpxUiHelper.getGpxTitle(file.getName());
	}

	@Override
	void readFromJson(@NonNull JSONObject json) throws JSONException {
		subtype = FileSubtype.GPX;
		super.readFromJson(json);
		appearanceInfo = GpxAppearanceInfo.fromJson(json);
	}

	@Override
	void writeToJson(@NonNull JSONObject json) throws JSONException {
		super.writeToJson(json);
		if (appearanceInfo != null) {
			appearanceInfo.toJson(json);
		}
	}

	@Override
	public void applyAdditionalParams(@Nullable SettingsItemReader<? extends SettingsItem> reader) {
		if (appearanceInfo != null) {
			File savedFile = null;
			if (reader instanceof FileSettingsItemReader) {
				savedFile = ((FileSettingsItemReader) reader).getSavedFile();
			}
			if (savedFile != null) {
				GpxDbHelper gpxDbHelper = app.getGpxDbHelper();
				boolean readItem = gpxDbHelper.hasItem(savedFile);
				GpxDataItem dataItem = null;
				if (!readItem) {
					dataItem = new GpxDataItem(savedFile);
					readItem = !gpxDbHelper.add(dataItem);
				}
				if (readItem) {
					dataItem = gpxDbHelper.getItem(savedFile, new GpxDataItemCallback() {
						@Override
						public void onGpxDataItemReady(@NonNull GpxDataItem item) {
							updateGpxParams(item);
						}
					});
				}
				if (dataItem != null) {
					updateGpxParams(dataItem);
				}
			}
		}
	}

	@Override
	public void delete() {
		super.delete();
		if (FileUtils.removeGpxFile(app, file)) {
			File parentFile = file.getParentFile();
			File gpxDir = app.getAppPath(IndexConstants.GPX_INDEX_DIR);
			if (parentFile != null && !parentFile.equals(gpxDir)) {
				parentFile.delete();
			}
		}
	}

	private void updateGpxParams(@NonNull GpxDataItem dataItem) {
		GpxDbHelper gpxDbHelper = app.getGpxDbHelper();
		GpxSplitType splitType = GpxSplitType.getSplitTypeByTypeId(appearanceInfo.splitType);
		gpxDbHelper.updateColor(dataItem, appearanceInfo.color);
		gpxDbHelper.updateWidth(dataItem, appearanceInfo.width);
		gpxDbHelper.updateShowArrows(dataItem, appearanceInfo.showArrows);
		gpxDbHelper.updateShowStartFinish(dataItem, appearanceInfo.showStartFinish);
		gpxDbHelper.updateSplit(dataItem, splitType, appearanceInfo.splitInterval);
		gpxDbHelper.updateColoringType(dataItem, appearanceInfo.coloringType);
	}

	private void createGpxAppearanceInfo() {
		GpxDataItem dataItem = app.getGpxDbHelper().getItem(file, new GpxDataItemCallback() {
			@Override
			public void onGpxDataItemReady(@NonNull GpxDataItem item) {
				appearanceInfo = new GpxAppearanceInfo(item);
			}
		});
		if (dataItem != null) {
			appearanceInfo = new GpxAppearanceInfo(dataItem);
		}
	}

	private void updateFile() {
		String subtypeFolder = subtype.getSubtypeFolder();
		if (fileName.contains(name) && !fileName.contains(subtypeFolder)) {
			this.file = new File(app.getAppPath(subtypeFolder), fileName);
		}
	}

	@Nullable
	@Override
	public SettingsItemReader<? extends SettingsItem> getReader() {
		return new FileSettingsItemReader(this) {
			@Override
			public void readFromStream(@NonNull InputStream inputStream, @Nullable File inputFile, @Nullable String entryName) throws IOException, IllegalArgumentException {
				super.readFromStream(inputStream, inputFile, entryName);
				GpxSelectionHelper gpxHelper = app.getSelectedGpxHelper();
				SelectedGpxFile selectedGpxFile = gpxHelper.getSelectedFileByPath(file.getAbsolutePath());
				if (selectedGpxFile != null) {
					GPXFile gpxFile = GPXUtilities.loadGPXFile(file);
					GpxSelectionParams params = GpxSelectionParams.newInstance()
							.showOnMap().syncGroup().setSelectedByUser(selectedGpxFile.selectedByUser);
					gpxHelper.selectGpxFile(gpxFile, params);
				}
			}
		};
	}
}
